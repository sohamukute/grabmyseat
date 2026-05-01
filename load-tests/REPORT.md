# GrabMySeat Load Test Report

This report records the actual numbers from each Gatling run so the project's
headline trust claim — "no oversell under load" — can be verified by reading
a single file rather than re-running the suite.

## Environment

Record the environment for every run:

- **Date / commit**: `<git rev-parse HEAD> @ <YYYY-MM-DD>`
- **Hardware**: `<CPU, RAM>`
- **Services**: `<docker compose ps>` output, especially inventory-booking /
  payment-wallet / saga-orchestrator / waiting-room / ticketing versions.
- **Load parameters**: `GATLING_RAMP_USERS`, `GATLING_RAMP_DURATION_SECONDS`,
  `GATLING_SEAT_COUNT`, `GATLING_BASE_URL`.

## Pre-run setup

```bash
# 1. start infra + services
docker compose up -d
./mvnw -pl gateway,auth-service,inventory-booking,payment-wallet,saga-orchestrator,waiting-room,ticketing \
    spring-boot:run &

# 2. seed event + seats via the internal seed endpoint
export INVENTORY_BOOKING_INTERNAL_API_KEY=change-me
export LOAD_TEST_BASE_URL="[REDACTED-URL]"
export LOAD_TEST_SEAT_COUNT=100

./mvnw -pl load-tests exec:java
# capture eventId, zoneId, seatStart, seatCount into GATLING_* env vars
```

## Gatling run

```bash
GATLING_RAMP_USERS=200 GATLING_RAMP_DURATION_SECONDS=60 \
  ./mvnw -pl load-tests gatling:test
```

Gatling writes HTML reports under `load-tests/target/gatling/`. Open the most
recent `*/index.html`.

## What the assertions actually prove

The Gatling assertions in `GrabMySeatSimulation.scala` enforce, after the run:

| Assertion | Meaning |
|---|---|
| `details("ReserveSeat").successfulRequests.count.lte(seatCount)` | Successful 201 reserves never exceed zone capacity — no oversell at the inventory API layer. |
| `details("ReserveSeat").successfulRequests.count.gte(1)` | At least one reserve succeeded so the run isn't a no-op (sanity). |
| `details("ConfirmBooking").successfulRequests.count.lte(seatCount)` | Confirmations never exceed successful reserves. |

These three assertions are necessary but **not sufficient** for the full
no-oversell guarantee — they only constrain Gatling's view of the inventory
API. The full check requires the system-level audit below.

## Post-run verification (required)

After every Gatling run, copy-paste these commands and paste their output
into the table below. **Do not skip.** These are the numbers the project
actually proves.

```bash
# 1. Oversell count must be 0.
curl -s "$GATLING_BASE_URL/api/inventory/status/oversell"

# 2. Successful reserve count from the Gatling report.
grep -h "ReserveSeat" load-tests/target/gatling/grabmyseatloadtest-*/js/stats.json | head

# 3. Total confirmed reservations per zone from inventory-booking.
PGPASSWORD=grabmyseat psql -h localhost -U grabmyseat -d grabmyseat_inventory \
  -c "SELECT zone_id, COUNT(*) FROM reservations WHERE status='CONFIRMED' GROUP BY zone_id;"
```

## Results log

| Run | Commit | Ramp | Successful 201 reserves | oversellCount | Confirmed reservations | Pass/Fail |
|---|---|---|---|---|---|---|
| <date> | <sha> | 50u / 60s | n | 0 | n | PASS |
