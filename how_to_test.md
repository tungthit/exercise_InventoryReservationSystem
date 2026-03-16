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
