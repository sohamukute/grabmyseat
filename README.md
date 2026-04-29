# GrabMySeat

GrabMySeat is a high-demand event-ticketing platform built to protect finite inventory from overselling. Customers discover events, enter a fair queue, reserve seats, pay from a demo wallet, and receive rotating QR tickets for entry.

## Architecture

The Maven reactor contains eight modules:

- `gateway` — public API routing, JWT validation, and rate limiting.
- `auth-service` — registration, login, refresh tokens, roles, and organiser profiles.
- `inventory-booking` — events, zones, seats, reservation holds, and capacity protection.
- `payment-wallet` — demo wallet, idempotent credits/debits, and audit records.
- `saga-orchestrator` — booking confirmation, compensation, and workflow state.
- `waiting-room` — Redis-backed queue admission, permits, and waitlist flow.
- `ticketing` — ticket issuance, rotating QR payloads, and staff check-in.
- `load-tests` — Gatling scenarios for the booking flow.

PostgreSQL stores durable service state. Redis provides short-lived queue, permit, counter, and locking state. The React frontend calls the services through the gateway.

## Prerequisites

- Java 21
- Docker with Docker Compose
- Node.js and npm

## Verify the project

Run the backend test suite:

```bash
./mvnw test
```

Build the frontend production bundle:

```bash
npm --prefix frontend run build
```

Run frontend tests:

```bash
npm --prefix frontend test
```

## Run locally

Start the full local stack:

```bash
docker compose up --build
```

The frontend is available at `http://localhost:4173` by default. The gateway is available at `http://localhost:8080`.

## Scope and limitations

- Payments use a demo wallet; no real payment-provider or UPI/card integration exists.
- Compose usernames, passwords, and demo accounts are local-development values only. Replace them with environment-provided secrets before any deployment.
- The project is designed as a local demonstration of booking correctness, queue admission, saga compensation, and ticket validation; it is not production deployed.
