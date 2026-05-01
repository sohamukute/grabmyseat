#!/usr/bin/env python3
"""Measure how many users can concurrently wait in the queue and complete the full journey."""
import asyncio
import base64
import json
import os
import random
import string
import subprocess
import time
from collections import Counter

import aiohttp

GATEWAY = "http://localhost:8080"
INTERNAL_EXEC_CONTAINER = "grabmyseat-ticketing-1"
TICKET_PRICE = 10.00
session_password = "LoadTest123!"


def sh(cmd: str) -> str:
    out = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
    if out.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\n{out.stdout}\n{out.stderr}")
    return out.stdout.strip()


def internal_curl(method: str, url: str, headers: dict, body: dict | None = None) -> dict:
    header_args = " ".join(f'-H "{k}: {v}"' for k, v in headers.items())
    body_arg = f"-d '{json.dumps(body)}'" if body is not None else ""
    cmd = (
        f"docker exec {INTERNAL_EXEC_CONTAINER} curl -s -X {method} "
        f"{header_args} {body_arg} \"{url}\""
    )
    out = sh(cmd)
    return json.loads(out) if out else {}


def bootstrap_admin_organizer():
    username = "loadtest-admin-" + "".join(random.choices(string.ascii_lowercase, k=6))
    internal_curl(
        "POST",
        "http://auth-service:8081/api/auth/register",
        {"Content-Type": "application/json"},
        {"username": username, "password": session_password},
    )
    auth_key = sh("docker exec grabmyseat-auth-service-1 printenv AUTH_INTERNAL_API_KEY")
    lookup = internal_curl(
        "GET",
        f"http://auth-service:8081/api/auth/internal/users/{username}",
        {"X-Internal-Api-Key": auth_key},
    )
    user_id = lookup["userId"]
    for role in ("ROLE_ADMIN", "ROLE_ORGANIZER"):
        internal_curl(
            "POST",
            f"http://auth-service:8081/api/auth/internal/users/{user_id}/roles",
            {"X-Internal-Api-Key": auth_key, "Content-Type": "application/json"},
            {"role": role},
        )
    return username


async def login(session: aiohttp.ClientSession, username: str, password: str) -> str:
    for attempt in range(8):
        async with session.post(
            f"{GATEWAY}/api/auth/login", json={"username": username, "password": password}
        ) as resp:
            if resp.status == 200:
                data = await resp.json()
                return data["accessToken"]
            await resp.read()
        await asyncio.sleep(min(2.0, 0.25 * (attempt + 1)))
    raise RuntimeError(f"login failed for {username}")


async def register(session: aiohttp.ClientSession, username: str, password: str) -> None:
    for attempt in range(8):
        async with session.post(
            f"{GATEWAY}/api/auth/register", json={"username": username, "password": password}
        ) as resp:
            if resp.status in (200, 201):
                return
            details = await resp.text()
        if resp.status not in (429, 502, 503, 504):
            raise RuntimeError(f"register failed for {username}: HTTP {resp.status} {details}")
        await asyncio.sleep(min(2.0, 0.25 * (attempt + 1)))
    raise RuntimeError(f"register failed for {username}: retries exhausted")


async def create_event(session: aiohttp.ClientSession, token: str) -> dict:
    zones = [
        {"name": "Front Pit", "capacity": 500, "price": TICKET_PRICE},
        {"name": "Main Floor", "capacity": 1000, "price": TICKET_PRICE},
        {"name": "Balcony", "capacity": 1000, "price": TICKET_PRICE},
    ]
    body = {
        "name": "Queue Capacity Test",
        "venue": "Stress Arena",
        "startsAt": "2027-01-01T20:00:00Z",
        "endsAt": "2027-01-02T00:00:00Z",
        "zones": zones,
    }
    async with session.post(
        f"{GATEWAY}/api/inventory/events",
        json=body,
        headers={"Authorization": f"Bearer {token}"},
    ) as resp:
        assert resp.status == 201, await resp.text()
        return await resp.json()


async def topup(session: aiohttp.ClientSession, admin_token: str, user_id: int, key: str) -> None:
    async with session.post(
        f"{GATEWAY}/api/wallet/admin/topups",
        json={"userId": user_id, "amount": 50.0, "idempotencyKey": key},
        headers={"Authorization": f"Bearer {admin_token}"},
    ) as resp:
        if resp.status != 200:
            raise RuntimeError(f"topup failed for user {user_id}: HTTP {resp.status} {await resp.text()}")


def user_id_from_jwt(token: str) -> int:
    payload_b64 = token.split(".")[1]
    payload_b64 += "=" * (-len(payload_b64) % 4)
    payload = json.loads(base64.urlsafe_b64decode(payload_b64))
    return payload["userId"]


async def run_full_journey(
    session: aiohttp.ClientSession,
    sem: asyncio.Semaphore,
    username: str,
    event_id: int,
    zone_id: int,
    seat_id: int,
    funded: bool,
    outcome: Counter,
    outcome_lock: asyncio.Lock,
):
    async with sem:
        try:
            token = await login(session, username, session_password)

            # Join queue
            async with session.post(
                f"{GATEWAY}/api/waiting-room/events/{event_id}/join",
                headers={"Authorization": f"Bearer {token}"},
            ) as resp:
                if resp.status != 200:
                    await outcome_lock.acquire()
                    outcome[f"queue_join_failed_{resp.status}"] += 1
                    outcome_lock.release()
                    return
                q = await resp.json()
                queue_token = q["token"]

            # Wait for admission
            permit_token = None
            for _ in range(300):
                async with session.get(
                    f"{GATEWAY}/api/waiting-room/events/{event_id}/permit",
                    params={"token": queue_token},
                    headers={"Authorization": f"Bearer {token}"},
                ) as resp:
                    if resp.status == 200:
                        permit_token = (await resp.json())["permitToken"]
                        break
                await asyncio.sleep(0.15)

            if not permit_token:
                await outcome_lock.acquire()
                outcome["never_admitted"] += 1
                outcome_lock.release()
                return

            # Reserve
            async with session.post(
                f"{GATEWAY}/api/inventory/reservations",
                json={"eventId": event_id, "zoneId": zone_id, "seatIds": [seat_id]},
                headers={"Authorization": f"Bearer {token}", "X-Queue-Permit": permit_token},
            ) as resp:
                if resp.status != 201:
                    await outcome_lock.acquire()
                    outcome[f"reserve_failed_{resp.status}"] += 1
                    outcome_lock.release()
                    return
                reservation = await resp.json()
                reservation_token = reservation["token"]

            if not funded:
                await outcome_lock.acquire()
                outcome["reserved_no_funds"] += 1
                outcome_lock.release()
                return

            # Confirm
            async with session.post(
                f"{GATEWAY}/api/saga/bookings/{reservation_token}/confirm",
                headers={"Authorization": f"Bearer {token}"},
            ) as resp:
                await outcome_lock.acquire()
                if resp.status == 200:
                    outcome["confirmed"] += 1
                elif resp.status == 402:
                    outcome["insufficient_funds"] += 1
                else:
                    outcome[f"confirm_failed_{resp.status}"] += 1
                outcome_lock.release()
        except Exception as ex:
            await outcome_lock.acquire()
            outcome[f"exception_{type(ex).__name__}"] += 1
            outcome_lock.release()


async def run_load_test(total_users: int, concurrency: int):
    print(f"\n=== Testing {total_users} users with concurrency={concurrency} ===")
    admin_username = bootstrap_admin_organizer()

    connector = aiohttp.TCPConnector(limit=concurrency + 50)
    async with aiohttp.ClientSession(
        connector=connector, timeout=aiohttp.ClientTimeout(total=120)
    ) as session:
        admin_token = await login(session, admin_username, session_password)
        admin_id = user_id_from_jwt(admin_token)
        print(f"admin user_id={admin_id}")

        event = await create_event(session, admin_token)
        event_id = event["id"]
        zones = {z["name"]: z for z in event["zones"]}
        print(f"event_id={event_id}")

        # Distribute users across zones
        zone_plan = [
            ("Front Pit", 500, total_users // 3),
            ("Main Floor", 1000, total_users // 3),
            ("Balcony", 1000, total_users - 2 * (total_users // 3)),
        ]

        total_planned = sum(d for _, _, d in zone_plan)
        usernames = [
            f"lt-{i:06d}-{''.join(random.choices(string.ascii_lowercase, k=4))}"
            for i in range(total_planned)
        ]

        sem = asyncio.Semaphore(concurrency)

        # Register users slowly to avoid rate limits
        print(f"Registering {total_planned} users...")
        async def reg(u):
            async with sem:
                await register(session, u, session_password)
        await asyncio.gather(*[reg(u) for u in usernames], return_exceptions=True)

        # Fund 80% of users
        funded = set(random.sample(usernames, int(total_planned * 0.8)))
        print(f"Funding {len(funded)} wallets...")
        async def do_topup(u, idx):
            async with sem:
                token = await login(session, u, session_password)
                uid = user_id_from_jwt(token)
                await topup(session, admin_token, uid, f"topup-{idx}")
        await asyncio.gather(*[do_topup(u, i) for i, u in enumerate(funded)], return_exceptions=True)

        # Build tasks
        outcome = Counter()
        outcome_lock = asyncio.Lock()
        tasks = []
        idx = 0
        for name, cap, demand in zone_plan:
            zone_id = zones[name]["id"]
            seat_ids = [s["id"] for s in zones[name]["seats"]]
            for i in range(demand):
                username = usernames[idx]
                idx += 1
                seat_id = seat_ids[i % len(seat_ids)]
                tasks.append(
                    run_full_journey(
                        session, sem, username, event_id, zone_id, seat_id,
                        username in funded, outcome, outcome_lock
                    )
                )

        print(f"Launching {len(tasks)} users...")
        t0 = time.monotonic()
        await asyncio.gather(*tasks, return_exceptions=True)
        elapsed = time.monotonic() - t0
        print(f"Finished in {elapsed:.1f}s")

        async with session.get(
            f"{GATEWAY}/api/inventory/status/oversell",
            headers={"Authorization": f"Bearer {admin_token}"},
        ) as resp:
            oversell = await resp.json()

        return dict(outcome), oversell, elapsed


async def main():
    results = []
    stages = os.getenv("LOAD_TEST_STAGES", "25:10,50:25,100:50")
    for stage in stages.split(","):
        total, concurrency = (int(part) for part in stage.strip().split(":", 1))
        outcome, oversell, elapsed = await run_load_test(total, concurrency)
        confirmed = outcome.get("confirmed", 0)
        print(f"\n--- Result for {total} users @ concurrency={concurrency} ---")
        for label, count in sorted(outcome.items(), key=lambda x: -x[1]):
            print(f"  {label}: {count}")
        print(f"  oversell: {oversell}")
        results.append({
            "total": total,
            "concurrency": concurrency,
            "confirmed": confirmed,
            "outcome": outcome,
            "oversell": oversell,
            "elapsed": elapsed,
        })

    print("\n\n=== SUMMARY ===")
    print(f"{'Users':>8} {'Concurrency':>12} {'Confirmed':>10} {'Oversell':>10} {'Time(s)':>10}")
    for r in results:
        print(
            f"{r['total']:>8} {r['concurrency']:>12} {r['confirmed']:>10} "
            f"{r['oversell'].get('oversellCount', 0):>10} {r['elapsed']:>10.1f}"
        )


if __name__ == "__main__":
    asyncio.run(main())
