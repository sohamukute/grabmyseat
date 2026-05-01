#!/usr/bin/env python3
"""One-off large-scale flash-sale load test against the real gateway.

Simulates thousands of real users hitting a single event's on-sale moment:
join waiting room -> wait for admission -> pick zone/seat -> reserve ->
confirm (funded users) or fail on insufficient funds (unfunded users) or
abandon (never confirm, exercising the 5-minute TTL hold expiry).

Talks to the actual public gateway (http://localhost:8080) for everything
a real client would do. Bootstrap steps (creating the organizer/admin test
accounts, granting roles, seeding wallet balances) go straight to the
internal service ports over the docker network via `docker exec`, since
those aren't things a real customer-facing load test should route through
public auth.
"""
import asyncio
import base64
import json
import random
import string
import subprocess
import time
from collections import Counter

import aiohttp

GATEWAY = "http://localhost:8080"
INTERNAL_EXEC_CONTAINER = "grabmyseat-ticketing-1"  # has curl, on the compose network

ZONE_PLAN = [
    ("Front Pit", 500, 1500),   # (name, capacity, demand) - deliberately oversubscribed 3x
    ("Main Floor", 1000, 1000), # demand exactly matches capacity
    ("Balcony", 1000, 500),     # slack, no contention
]
FUNDED_FRACTION = 0.70   # rest get $0 balance -> insufficient funds at confirm
ABANDON_FRACTION = 0.10  # of funded users: reserve but never confirm (TTL test)
CONCURRENCY = 400
TICKET_PRICE = 10.00
FUNDED_TOPUP = 50.00

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
        f'docker exec {INTERNAL_EXEC_CONTAINER} curl -s -X {method} '
        f'{header_args} {body_arg} "{url}"'
    )
    out = sh(cmd)
    return json.loads(out) if out else {}


def bootstrap_admin_organizer():
    """Register a bootstrap account, grant it ADMIN+ORGANIZER via auth-service's internal endpoint."""
    username = "loadtest-admin-" + "".join(random.choices(string.ascii_lowercase, k=6))
    internal_curl(
        "POST", f"http://auth-service:8081/api/auth/register",
        {"Content-Type": "application/json"},
        {"username": username, "password": session_password},
    )
    auth_key = sh("docker exec grabmyseat-auth-service-1 printenv AUTH_INTERNAL_API_KEY")
    lookup = internal_curl(
        "GET", f"http://auth-service:8081/api/auth/internal/users/{username}",
        {"X-Internal-Api-Key": auth_key},
    )
    user_id = lookup["userId"]
    for role in ("ROLE_ADMIN", "ROLE_ORGANIZER"):
        internal_curl(
            "POST", f"http://auth-service:8081/api/auth/internal/users/{user_id}/roles",
            {"X-Internal-Api-Key": auth_key, "Content-Type": "application/json"},
            {"role": role},
        )
    return username


async def login(session: aiohttp.ClientSession, username: str, password: str) -> str:
    for attempt in range(3):
        async with session.post(f"{GATEWAY}/api/auth/login",
                                 json={"username": username, "password": password}) as resp:
            if resp.status == 200:
                data = await resp.json()
                return data["accessToken"]
            await resp.read()
        await asyncio.sleep(0.5 * (attempt + 1))
    raise RuntimeError(f"login failed for {username} after 3 attempts")


async def register(session: aiohttp.ClientSession, username: str, password: str) -> None:
    async with session.post(f"{GATEWAY}/api/auth/register",
                             json={"username": username, "password": password}) as resp:
        await resp.read()


async def create_event(session: aiohttp.ClientSession, token: str) -> dict:
    zones = [{"name": name, "capacity": cap, "price": TICKET_PRICE}
             for name, cap, _demand in ZONE_PLAN]
    body = {
        "name": "Load Test Concert",
        "venue": "Stress Arena",
        "startsAt": "2027-01-01T20:00:00Z",
        "endsAt": "2027-01-02T00:00:00Z",
        "zones": zones,
    }
    async with session.post(f"{GATEWAY}/api/inventory/events",
                             json=body,
                             headers={"Authorization": f"Bearer {token}"}) as resp:
        assert resp.status == 201, await resp.text()
        return await resp.json()


async def topup(session: aiohttp.ClientSession, admin_token: str, user_id: int, key: str) -> None:
    async with session.post(
            f"{GATEWAY}/api/wallet/admin/topups",
            json={"userId": user_id, "amount": FUNDED_TOPUP, "idempotencyKey": key},
            headers={"Authorization": f"Bearer {admin_token}"}) as resp:
        await resp.read()


def user_id_from_jwt(token: str) -> int:
    # /api/auth/me only returns username+roles, not the numeric id -
    # the access token itself carries a "userId" claim, decode it directly.
    payload_b64 = token.split(".")[1]
    payload_b64 += "=" * (-len(payload_b64) % 4)
    payload = json.loads(base64.urlsafe_b64decode(payload_b64))
    return payload["userId"]


class Outcome:
    def __init__(self):
        self.counts = Counter()
        self.lock = asyncio.Lock()
        self.latencies = []

    async def record(self, label: str, latency: float | None = None):
        async with self.lock:
            self.counts[label] += 1
            if latency is not None:
                self.latencies.append(latency)


async def run_user(session: aiohttp.ClientSession, sem: asyncio.Semaphore,
                    username: str, password: str, event_id: int, zone_id: int,
                    seat_id: int, should_confirm: bool, outcome: Outcome):
    async with sem:
        t0 = time.monotonic()
        try:
            token = await login(session, username, password)

            async with session.post(
                    f"{GATEWAY}/api/waiting-room/events/{event_id}/join",
                    headers={"Authorization": f"Bearer {token}"}) as resp:
                if resp.status != 200:
                    await outcome.record(f"queue_join_failed_{resp.status}")
                    return
                q = await resp.json()
                queue_token = q["token"]

            permit_token = None
            for _ in range(300):
                async with session.get(
                        f"{GATEWAY}/api/waiting-room/events/{event_id}/permit",
                        params={"token": queue_token},
                        headers={"Authorization": f"Bearer {token}"}) as resp:
                    if resp.status == 200:
                        permit_token = (await resp.json())["permitToken"]
                        break
                await asyncio.sleep(0.15)

            if not permit_token:
                await outcome.record("never_admitted")
                return

            async with session.post(
                    f"{GATEWAY}/api/inventory/reservations",
                    json={"eventId": event_id, "zoneId": zone_id, "seatIds": [seat_id]},
                    headers={"Authorization": f"Bearer {token}", "X-Queue-Permit": permit_token}) as resp:
                if resp.status != 201:
                    await outcome.record(f"reserve_failed_{resp.status}")
                    return
                reservation = await resp.json()
                reservation_token = reservation["token"]

            if not should_confirm:
                await outcome.record("abandoned_after_reserve")
                return

            async with session.post(
                    f"{GATEWAY}/api/saga/bookings/{reservation_token}/confirm",
                    headers={"Authorization": f"Bearer {token}"}) as resp:
                if resp.status == 200:
                    await outcome.record("confirmed", time.monotonic() - t0)
                elif resp.status == 402:
                    await outcome.record("insufficient_funds")
                else:
                    await outcome.record(f"confirm_failed_{resp.status}")
        except Exception as ex:
            await outcome.record(f"exception_{type(ex).__name__}")


async def main():
    print("=== bootstrapping admin/organizer account ===")
    admin_username = bootstrap_admin_organizer()

    connector = aiohttp.TCPConnector(limit=CONCURRENCY + 50)
    async with aiohttp.ClientSession(connector=connector,
                                      timeout=aiohttp.ClientTimeout(total=60)) as session:
        admin_token = await login(session, admin_username, session_password)
        admin_id = user_id_from_jwt(admin_token)
        print(f"admin user_id={admin_id}")

        print("=== creating event with 3 zones ===")
        event = await create_event(session, admin_token)
        event_id = event["id"]
        zones = {z["name"]: z for z in event["zones"]}
        print(f"event_id={event_id}")
        for name, cap, demand in ZONE_PLAN:
            print(f"  zone '{name}': id={zones[name]['id']} capacity={cap} planned_demand={demand}")

        total_users = sum(d for _, _, d in ZONE_PLAN)
        print(f"=== registering {total_users} test users ===")
        usernames = [f"lt-{i:06d}-{''.join(random.choices(string.ascii_lowercase, k=4))}"
                     for i in range(total_users)]
        sem = asyncio.Semaphore(CONCURRENCY)

        async def reg(u):
            async with sem:
                await register(session, u, session_password)
        results = await asyncio.gather(*[reg(u) for u in usernames], return_exceptions=True)
        failures = [r for r in results if isinstance(r, Exception)]
        print(f"registration done ({len(failures)} failures, retried individually below)")
        for u, r in zip(usernames, results):
            if isinstance(r, Exception):
                await register(session, u, session_password)

        funded = set(random.sample(usernames, int(total_users * FUNDED_FRACTION)))
        print(f"=== topping up {len(funded)} wallets (${FUNDED_TOPUP} each) ===")

        async def do_topup(u, idx):
            async with sem:
                token = await login(session, u, session_password)
                uid = user_id_from_jwt(token)
                await topup(session, admin_token, uid, f"loadtest-topup-{idx}")
        results = await asyncio.gather(*[do_topup(u, i) for i, u in enumerate(funded)],
                                        return_exceptions=True)
        failures = [r for r in results if isinstance(r, Exception)]
        print(f"wallet funding done ({len(failures)} failures)")
        if failures:
            print(f"  sample failure: {failures[0]!r}")

        abandon = set(random.sample(list(funded), int(len(funded) * ABANDON_FRACTION)))
        print(f"=== {len(abandon)} funded users will reserve then abandon (TTL test) ===")

        # build per-zone seat assignment: cycle seat ids for each zone across its demand
        tasks = []
        outcome = Outcome()
        idx = 0
        for name, cap, demand in ZONE_PLAN:
            zone_id = zones[name]["id"]
            seat_ids = [s["id"] for s in zones[name]["seats"]]
            for i in range(demand):
                username = usernames[idx]
                idx += 1
                seat_id = seat_ids[i % len(seat_ids)]  # deliberately reuse seats -> contention
                should_confirm = username not in abandon
                tasks.append(run_user(session, sem, username, session_password,
                                       event_id, zone_id, seat_id, should_confirm, outcome))

        print(f"=== launching {len(tasks)} simulated users (concurrency={CONCURRENCY}) ===")
        t0 = time.monotonic()
        await asyncio.gather(*tasks, return_exceptions=True)
        elapsed = time.monotonic() - t0
        print(f"=== load burst finished in {elapsed:.1f}s ===")

        print("\n=== outcome breakdown ===")
        for label, count in outcome.counts.most_common():
            print(f"  {label}: {count}")

        if outcome.latencies:
            lats = sorted(outcome.latencies)
            p50 = lats[len(lats) // 2]
            p95 = lats[int(len(lats) * 0.95)]
            p99 = lats[int(len(lats) * 0.99)]
            print(f"\nconfirm latency: p50={p50:.2f}s p95={p95:.2f}s p99={p99:.2f}s max={lats[-1]:.2f}s")

        async with session.get(f"{GATEWAY}/api/inventory/status/oversell",
                                headers={"Authorization": f"Bearer {admin_token}"}) as resp:
            oversell = await resp.json()
            print(f"\noversell status right after burst: {oversell}")

        with open("/tmp/loadtest_state.json", "w") as f:
            json.dump({
                "event_id": event_id,
                "zones": {name: zones[name]["id"] for name, _, _ in ZONE_PLAN},
                "admin_username": admin_username,
            }, f)
        print("\nstate saved to /tmp/loadtest_state.json for the TTL-expiry follow-up check")


if __name__ == "__main__":
    asyncio.run(main())
