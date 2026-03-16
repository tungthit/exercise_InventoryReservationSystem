# Warehouse Inventory Reservation System

High-concurrency inventory reservation service built with **Java 21**, **Spring WebFlux (Netty)**, **NATS JetStream**, **Redis**, and **PostgreSQL**.

---

## 🚀 How to Run Locally

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

## 🛠️ Tech Stack

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

## 🏛️ Architecture Overview

```
┌─────────────────────────────────────────────────┐
│          REST API  (Netty / Non-blocking)       │
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
│ Domain Layer   │  │      Infrastructure Layer   │
│ Entities       │  │  Redis  (Cache + Lock)      │
│ Repositories   │  │  NATS JetStream (Events)    │
│ Port Interfaces│  │  PostgreSQL + R2DBC         │
└────────────────┘  └─────────────────────────────┘
```

---

## 🚦 Concurrency Strategy

Two-level guard prevents overselling across any number of replicas:

1. **Redisson distributed lock** (per SKU) – acquired before reading inventory. Prevents two concurrent requests on different pods from seeing the same uncommitted available stock.
2. **Pessimistic DB row lock** (`SELECT … FOR UPDATE`) – inside the R2DBC transaction. Ensures 100% correctness even if the distributed lock layer fails.
3. **`@Version` optimistic lock** – final defence on the `inventory` table row.

```
Replica A  ──► Redis LOCK "A100" ──► DB FOR UPDATE ──► save ──► Redis UNLOCK
Replica B  ──► Redis LOCK "A100"  (waits)           ──► DB FOR UPDATE ...
```

---

## 🧩 Design Patterns

| Pattern      | Location                                 | Purpose                                                    |
|--------------|------------------------------------------|------------------------------------------------------------|
| **Factory**  | `application/factory/ReservationFactory` | Validates SKUs, builds `ReservationAggregate`; isolates create logic |
| **State**    | `domain/state/ReservationStateMachine`   | PENDING→CONFIRMED or CANCELLED; terminal states throw     |
| **Observer** | `infrastructure/messaging/NatsEventPublisher` | Domain events published to NATS decoupled from core logic |
| **Strategy** | `domain/port/LockService`                | Swappable locking strategy (Redisson implementation)      |

---

---

## ⚖️ Horizontal Scaling

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

## 📊 Observability

This application exposes monitoring endpoints to track performance, health, and message broker status.

### Spring Boot Actuator
- **Health Check**: [http://localhost:8080/api/actuator/health](http://localhost:8080/api/actuator/health)
- **Application Metrics**: [http://localhost:8080/api/actuator/metrics](http://localhost:8080/api/actuator/metrics)

### NATS JetStream Dashboard
The NATS monitoring server is exposed locally to inspect broker statistics and connections:
- **Main Dashboard**: [http://localhost:8222](http://localhost:8222)
- **JetStream Metrics**: [http://localhost:8222/jsz](http://localhost:8222/jsz) (Shows streams, consumers, and message volume)
- **Active Connections**: [http://localhost:8222/connz](http://localhost:8222/connz) (Shows active Spring Boot clients)
- **Server Health**: [http://localhost:8222/varz](http://localhost:8222/varz)

---

## 🧪 Testing & API Reference

Please see [how_to_test.md](how_to_test.md) for detailed instructions on:
- Smoke testing the API with curl
- Full REST API Request/Response schemas
- How to execute the unit test suite

---

### 📦 Package Structure

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
