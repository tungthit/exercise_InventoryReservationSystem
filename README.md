# Warehouse Inventory Reservation System

A production-grade, high-concurrency inventory reservation service built with **Java 21**, **Spring WebFlux (Netty)**, **NATS JetStream**, **Redis**, and **PostgreSQL**.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│          REST API  (Netty / Non-blocking)        │
│   POST /api/reservations                        │
│   POST /api/reservations/{id}/confirm           │
│   POST /api/reservations/{id}/cancel            │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│              Application Layer                  │
│   ReservationService    InventoryService        │
│   ReservationFactory  ← Factory Pattern         │
│   ReservationStateMachine ← State Pattern       │
└─────────┬────────────────────┬──────────────────┘
          │                    │
┌─────────▼──────┐  ┌──────────▼──────────────────┐
│  Domain Layer  │  │      Infrastructure Layer    │
│  Entities      │  │  Redis  (Cache + Lock)       │
│  Repositories  │  │  NATS JetStream (Events)     │
│  Port Interfaces│  │  PostgreSQL + R2DBC         │
└────────────────┘  └─────────────────────────────┘
```

### Package Structure

```
com.warehouse.inventory
├── api/                    ← Controllers, DTOs, ExceptionHandler
├── application/            ← Use-case services, Factory
│   ├── factory/            (Factory Pattern)
│   └── service/
├── domain/                 ← Entities, State Pattern, Events, Ports
│   ├── exception/
│   ├── event/
│   ├── model/
│   ├── port/
│   ├── repository/
│   └── state/              (State Pattern)
├── infrastructure/         ← Redis cache, Redisson lock, NATS publisher
│   ├── cache/
│   ├── lock/
│   └── messaging/
└── config/                 ← DatabaseConfig, NatsConfig, RedisConfig, AotRuntimeHints
```

---

## Design Patterns

| Pattern      | Location                                 | Purpose                                                    |
|--------------|------------------------------------------|------------------------------------------------------------|
| **Factory**  | `application/factory/ReservationFactory` | Validates SKUs, builds `ReservationAggregate`; isolates create logic |
| **State**    | `domain/state/ReservationStateMachine`   | PENDING→CONFIRMED or CANCELLED; terminal states throw     |
| **Observer** | `infrastructure/messaging/NatsEventPublisher` | Domain events published to NATS decoupled from core logic |
| **Strategy** | `domain/port/LockService`                | Swappable locking strategy (Redisson implementation)      |

---

## Tech Stack

| Concern        | Technology                            |
|----------------|---------------------------------------|
| Server         | **Netty** (via Spring WebFlux)        |
| Language       | **Java 21**                           |
| Framework      | Spring Boot 3.3 (reactive/WebFlux)    |
| Database       | PostgreSQL 16 + **R2DBC** (reactive)  |
| Migrations     | Flyway (JDBC)                         |
| Cache          | Redis 7 + Lettuce (reactive)          |
| Dist. Lock     | **Redisson** per-SKU locks            |
| Message Broker | **NATS JetStream** (at-least-once)    |
| AOT            | `RuntimeHintsRegistrar` (GraalVM-ready)|
| Tests          | JUnit 5, Mockito, StepVerifier        |

---

## Concurrency Strategy

Two-level guard prevents overselling across any number of replicas:

1. **Redisson distributed lock** (per SKU) – acquired before reading inventory. Prevents two concurrent requests on different pods from seeing the same uncommitted available stock.
2. **Pessimistic DB row lock** (`SELECT … FOR UPDATE`) – inside the R2DBC transaction. Ensures 100% correctness even if the distributed lock layer fails.
3. **`@Version` optimistic lock** – final defence on the `inventory` table row.

```
Replica A  ──► Redis LOCK "A100" ──► DB FOR UPDATE ──► save ──► Redis UNLOCK
Replica B  ──► Redis LOCK "A100"  (waits)           ──► DB FOR UPDATE ...
```

---

## Prerequisites

- **Docker Desktop** (for local infra)
- **Java 21 JDK**
- **Maven 3.9+**

---

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on `:5432`
- Redis on `:6379`
- NATS (with JetStream) on `:4222` / monitoring `:8222`

### 2. Run the Application

```bash
mvn spring-boot:run
```

Flyway will automatically apply migrations and seed sample data.

### 3. Smoke Test the API

```bash
# Create a reservation (SKUs A100, B200, C300 are seeded)
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-1001",
    "items": [
      {"sku": "A100", "quantity": 5},
      {"sku": "B200", "quantity": 10}
    ]
  }'

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

## API Reference

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

## Database Schema

```
products          inventory             reservations        reservation_items
─────────         ─────────────────     ────────────────    ─────────────────────
id (PK)           id (PK)               id (PK)             id (PK)
sku (UNIQUE)      product_id (FK)       order_id            reservation_id (FK)
name              total_stock           status              sku
created_at        reserved_stock        created_at          quantity
                  version (OPT LOCK)    updated_at
```

---

## Running Tests

```bash
# All unit tests
mvn test

# Specific test class
mvn -Dtest=ReservationStateMachineTest test
mvn -Dtest=InventoryServiceTest test
mvn -Dtest=ReservationServiceTest test
mvn -Dtest=InventoryTest test
```

---

## Horizontal Scaling

The service is stateless by design:

- All state is in PostgreSQL and Redis
- Redisson distributed locks work across replicas
- NATS JetStream delivers events exactly-once regardless of publisher replica
- Session affinity is NOT required

```bash
# Scale to 3 replicas (Kubernetes / Docker Swarm)
kubectl scale deployment inventory-reservation --replicas=3
```

---

## Observability

- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **NATS monitor**: `http://localhost:8222`

---

## Environment Variables

| Variable       | Default        | Description              |
|----------------|----------------|--------------------------|
| `DB_HOST`      | `localhost`    | PostgreSQL host          |
| `DB_PORT`      | `5432`         | PostgreSQL port          |
| `DB_NAME`      | `inventory_db` | Database name            |
| `DB_USER`      | `postgres`     | DB username              |
| `DB_PASS`      | `postgres`     | DB password              |
| `REDIS_HOST`   | `localhost`    | Redis / Sentinel host    |
| `REDIS_PORT`   | `6379`         | Redis port               |
| `NATS_URL`     | `nats://localhost:4222` | NATS server URL |
| `SERVER_PORT`  | `8080`         | HTTP port                |
