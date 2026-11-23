# Auto Service API

REST API for managing an auto repair shop (clients, vehicles, appointments, interventions, parts),
built with Java 21 and Spring Boot 3.

> Work in progress — full documentation will be added once the core features are in place.

## Run with Docker

```bash
docker compose up --build
```

The API starts on `http://localhost:8080` (Swagger UI: `http://localhost:8080/swagger-ui.html`)
together with PostgreSQL 16. Optional overrides go in a `.env` file — see `.env.example`.

## Build

```bash
./mvnw verify
```

Integration tests use [Testcontainers](https://testcontainers.com/), so a running Docker daemon is required.
