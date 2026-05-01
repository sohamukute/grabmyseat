# GrabMySeat Load Tests

This module contains a Gatling simulation that drives the full booking flow:
login, waiting-room admission, seat reservation, and saga confirmation (which
also triggers ticket issuance through the saga-orchestrator).

> **Why Gatling and not k6?** See
> [`docs/decisions/0007-load-testing-tool.md`](../docs/decisions/0007-load-testing-tool.md)
> for the rationale (JVM-native stack, Maven-native execution, assertion DSL,
> HTML reports).

## Prerequisites

1. Start the infrastructure and services:
   ```bash
   docker compose up -d
   ```
   This starts Postgres, Redis, Prometheus, and Grafana.

2. Start the GrabMySeat services (gateway, auth-service, inventory-booking,
   payment-wallet, saga-orchestrator, waiting-room, and ticketing).

3. Create a load-test customer user. The auth service registers users with
   `ROLE_CUSTOMER` by default, so a simple registration is enough:
   ```bash
   curl -X POST [REDACTED-URL] \
     -H "Content-Type: application/json" \
     -d '{"username":"loadtester","password":"LoadTest123!","email":"[REDACTED-EMAIL_ADDRESS]","roles":["ROLE_CUSTOMER"]}'
   ```

4. Top up the test user's wallet using an admin account. The saga will debit
   this wallet during confirmation:
   ```bash
   ADMIN_JWT=$(curl -s -X POST [REDACTED-URL] \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

   TEST_USER_ID=$(curl -s [REDACTED-URL] \
     -H "Authorization: Bearer $ADMIN_JWT" | jq -r '.users[] | select(.username=="loadtester") | .id')

   curl -X POST [REDACTED-URL] \
     -H "Authorization: Bearer $ADMIN_JWT" \
     -H "Content-Type: application/json" \
     -d "{\"userId\":$TEST_USER_ID,\"amount\":10000.00,\"idempotencyKey\":\"load-test-wallet\"}"
   ```

## Seed a load-test event

The fastest way to create a load-test event is the internal seed endpoint. It
is blocked at the gateway and protected by the `INVENTORY_BOOKING_INTERNAL_API_KEY`
environment variable, so call it directly against the `inventory-booking` service
(or through the gateway with the internal API key header).

### Option A: use the Java seeder

```bash
export INVENTORY_BOOKING_INTERNAL_API_KEY=change-me
export LOAD_TEST_BASE_URL="[REDACTED-URL]"
export LOAD_TEST_SEAT_COUNT=100

./mvnw -pl load-tests exec:java
```

The seeder prints the seeded `eventId`, `zoneId`, and `seatIds`. Export them for
Gatling:

```bash
export GATLING_EVENT_ID=<eventId>
export GATLING_ZONE_ID=<zoneId>
export GATLING_SEAT_START=<first seat id>
export GATLING_SEAT_COUNT=100
```

### Option B: call the seed endpoint directly with curl

```bash
SEED=$(curl -s -X POST [REDACTED-URL] \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: $INVENTORY_BOOKING_INTERNAL_API_KEY" \
  -d '{"seatCount":100}')

echo "$SEED" | jq
export GATLING_EVENT_ID=$(echo "$SEED" | jq -r '.eventId')
export GATLING_ZONE_ID=$(echo "$SEED" | jq -r '.zoneId')
export GATLING_SEAT_START=$(echo "$SEED" | jq -r '.seatIds[0]')
export GATLING_SEAT_COUNT=$(echo "$SEED" | jq -r '.seatIds | length')
```

## Run the simulation

Compile and run the Gatling simulation using the Maven wrapper from the project
root:

```bash
./mvnw -pl load-tests gatling:test
```

The default scenario ramps **50 virtual users over 60 seconds**.

## Configuration

The simulation reads environment variables so the same code can run against any
seeded event without recompilation:

| Variable | Default | Description |
|----------|---------|-------------|
| `GATLING_BASE_URL` | `[REDACTED-URL] | Gateway base URL |
| `GATLING_EVENT_ID` | `1` | Event to target |
| `GATLING_ZONE_ID` | `1` | Zone to target |
| `GATLING_SEAT_START` | `1` | First seat id to reserve |
| `GATLING_SEAT_COUNT` | `100` | Number of seats available (feeder cycles through them) |
| `GATLING_USERNAME` | `loadtester` | Test user username |
| `GATLING_PASSWORD` | `LoadTest123!` | Test user password |
| `GATLING_RAMP_USERS` | `50` | Number of virtual users |
| `GATLING_RAMP_DURATION_SECONDS` | `60` | Ramp duration |

Example with a larger ramp:

```bash
GATLING_RAMP_USERS=100 GATLING_RAMP_DURATION_SECONDS=60 \
  ./mvnw -pl load-tests gatling:test
```

## Reports

Gatling writes HTML reports under `load-tests/target/gatling/` after each run:

```bash
open load-tests/target/gatling/grabmyseatloadtest-*/index.html
```

## Notes

- The waiting room emits permits over an SSE broadcast. Under high concurrency
  the current broadcast model means virtual users may observe permits issued to
  other users, which will fail the inventory permit check. For a clean
  multi-user run the waiting room should emit per-user SSE channels.
- The simulation therefore primarily proves that the Maven module compiles, the
  scenario logic is wired correctly, and each service endpoint accepts traffic
  under a ramped load.
