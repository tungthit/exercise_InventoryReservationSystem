# Warehouse Inventory Reservation System

High-concurrency inventory reservation service built with **Java 21**, **Spring WebFlux (Netty)**, **NATS JetStream**, **Redis**, and **PostgreSQL**.

---

## How to Run Locally

### 1. Prerequisites (What you need installed)

Before running the application, ensure your system has the following installed:

1. **Java 21 JDK**: Required to compile and run the Spring Boot application. [Download Java 21](https://www.oracle.com/java/technologies/downloads/#java21)
2. **Maven 3.9+**: Required to build the project and run tests. [Download Maven](https://maven.apache.org/download.cgi)
3. **Docker Desktop**: Required to spin up the localized infrastructure (PostgreSQL, Redis, NATS). [Download Docker](https://www.docker.com/products/docker-desktop/)

### 2. Start the Infrastructure

Open a terminal in the root directory (where `docker-compose.yml` is located) and start the required services in the background:

```bash
docker compose up -d
```

This will spin up:
- **PostgreSQL 16** on port `:5432` (Relational database for storing reservations and inventory)
- **Redis 7** on port `:6379` (Distributed locking and caching)
- **NATS 2.10 (JetStream)** on port `:4222` (Event broker for domain events)

*(Note: The first time you run this, Docker will download the images which may take a minute).*

### 3. Start the Application

Once the Docker containers are running happily, boot up the Spring application using Maven:

```bash
mvn spring-boot:run
```

The application will automatically:
1. Connect to PostgreSQL, Redis, and NATS.
2. Run **Flyway** migration scripts to automatically create tables.
3. Seed the database with sample inventory (SKUs `A100`, `B200`, `C300`).
4. Start the Netty web server on `http://localhost:8080`.

### 4. Smoke Test the API

You can use Postman or `curl` to test the API. Note the `api` prefix in the URL.

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

### Why we chose these technologies for this use-case:

- **Java 21**: Offers Virtual Threads capability (though we opted for raw Reactive Streams here for maximum raw throughput), excellent pattern matching, and record types which perfectly suit Domain-Driven Design aggregates.
- **Spring WebFlux (Netty)**: Inventory reservation is a highly I/O bound process (waiting on database locks, Redis locks, and message brokers). A non-blocking Netty server means we don't block an OS thread for every concurrent HTTP request, allowing us to handle thousands of concurrent requests on minimal compute resources.
- **PostgreSQL + R2DBC**: Relational databases are critical for inventory because we absolutely need ACID compliance and `SELECT ... FOR UPDATE` row-level locking to prevent overselling. R2DBC provides a reactive, non-blocking driver to PostgreSQL to match Netty's architecture.
- **Redis (Redisson)**: We use this as a first-line-of-defense distributed lock. Instead of sending 100 concurrent requests to hammer the database row, Redisson queues or fast-fails them at the cache layer, keeping the database healthy under spike loads (e.g. Flash Sales).
- **NATS JetStream**: Standard NATS is fire-and-forget. JetStream provides lightweight, persistent, at-least-once message delivery. When a reservation drops, a downstream service (like fulfillment or notification) is guaranteed to receive the event even if they are temporarily offline.

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
