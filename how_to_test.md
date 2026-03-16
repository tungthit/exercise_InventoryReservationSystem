# How to Test the API & Run Unit Tests

This document covers how to verify that the Warehouse Inventory Reservation System is working properly, including smoke testing the API and running the automated test suite.

---

## 1. Smoke Test the API

You can use Postman or `curl` to test the API. Make sure the application is running and the database has been seeded by Flyway (SKUs `A100`, `B200`, `C300` will be available).

```bash
# 1. Create a new reservation 
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-1001",
    "items": [
      {"sku": "A100", "quantity": 5},
      {"sku": "B200", "quantity": 10}
    ]
  }'

# The terminal will output JSON containing the new Reservation ID. Copy it for the next steps!
```

```bash
# Note the returned `id`, then:

# Confirm
curl -X POST http://localhost:8080/api/reservations/{id}/confirm

# Cancel (releases stock)
curl -X POST http://localhost:8080/api/reservations/{id}/cancel

# Try to oversell (returns 409 CONFLICT)
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-OVERSELL","items":[{"sku":"A100","quantity":9999}]}'
```

---

## 2. API Reference

### `POST /api/reservations`

**Request**
```json
{
  "orderId": "ORD-1001",
  "items": [
    { "sku": "A100", "quantity": 5 }
  ]
}
```
**Response** `201 Created`

```json
{
  "id": "uuid",
  "orderId": "ORD-1001",
  "status": "PENDING",
  "items": [{ "id": "uuid", "sku": "A100", "quantity": 5 }],
  "createdAt": "...",
  "updatedAt": "..."
}
```

### `POST /api/reservations/{id}/confirm`
Response `200 OK` with `status: "CONFIRMED"`.

### `POST /api/reservations/{id}/cancel`
Response `200 OK` with `status: "CANCELLED"`.  
Reserved stock is released atomically.

### Error Responses

| HTTP | Code | Cause |
|------|------|-------|
| 400 | `VALIDATION_ERROR` | Missing/invalid fields |
| 400 | `INVALID_STATE_TRANSITION` | e.g. confirming a CANCELLED reservation |
| 404 | `NOT_FOUND` | Unknown reservation ID |
| 404 | `PRODUCT_NOT_FOUND` | Unknown SKU |
| 409 | `INSUFFICIENT_STOCK` | Not enough available units |
| 409 | `LOCK_CONFLICT` | High contention; retry |

---

## 3. Running Tests

The application is thoroughly tested with JUnit 5, Mockito, and Reactor `StepVerifier`. The tests cover domain immutability, state transitions, cache fallbacks, and multi-threaded race conditions.

```bash
# Run all unit tests
mvn test

# Run a specific test class
mvn -Dtest=ReservationStateMachineTest test
mvn -Dtest=InventoryServiceTest test
mvn -Dtest=ReservationServiceTest test
mvn -Dtest=InventoryTest test
```

---

## 4. Stress Testing (Load Testing)

To prove the concurrency locks (Redisson + R2DBC) work under extreme pressure, we simulate a **"Flash Sale"** where hundreds of users attempt to buy the exact same SKU at the exact same millisecond.

A ready-to-use **Apache JMeter** script is included in the repository: `flash-sale-load-test.jmx`.

### Running the JMeter Test:
1. Ensure the application and Docker containers are running.
2. Ensure SKU `A100` has its default seeded stock (100 units). If you've already depleted it, restart the database container to re-seed Flyway: `docker compose down -v && docker compose up -d`.
3. Open **Apache JMeter 5.6+**.
4. Click `File -> Open` and select `flash-sale-load-test.jmx`.
5. The test is configured for **500 concurrent threads** looping twice (1000 total requests) against `POST /api/reservations`.
6. Click the Green "Start" button.

**Expected Results:**
- Exactly **100** requests will succeed with `201 Created`.
- Exactly **900** requests will fail gracefully with `409 Conflict` (Insufficient Stock or Lock Conflict).
- The PostgreSQL database will show exactly `0` available stock, with no overselling.

*Alternative: If you prefer **k6**, you can write a similar script that fires `http.post` with `{"sku": "A100", "quantity": 1}` using 500 VUs.*

---

## 5. Penetration Testing (Security)

When penetration testing this application (using tools like OWASP ZAP or Burp Suite), focus on validating these business logic and security boundaries:

1. **Negative Quantity Exploits:**
   - **Attack:** Send `{"sku": "A100", "quantity": -50}` to try and artificially inflate stock.
   - **Defense:** The API immediately rejects this with `400 Bad Request` (`@Min(1)` validation).
2. **SQL Injection (SQLi):**
   - **Attack:** Pass DROP TABLE commands in the `orderId` JSON field.
   - **Defense:** Impossible. Spring Data R2DBC uses parameterized binds exclusively; no raw query concatenation is occurring.
3. **Lock Starvation (DoS):**
   - **Attack:** Flood the API with requests for random, non-existent SKUs to exhaust the Redis lock pool.
   - **Defense:** Redisson is configured with a `lock-watchdog-timeout-ms` (30s) to automatically purge abandoned locks. (In production, an API Gateway should rate-limit these IPs).
4. **Idempotency Replay Attacks:**
   - **Attack:** Capture a successful `POST /api/reservations/{id}/confirm` request and replay it 100 times to break state.
   - **Defense:** The `ReservationStateMachine` uses the State Pattern to instantly throw a 400 `InvalidStateTransitionException` if the reservation is already CONFIRMED.
