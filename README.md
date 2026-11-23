# Auto Service API

REST API for managing an auto repair shop (clients, vehicles, appointments, interventions, parts),
built with Java 21 and Spring Boot 3.

> Work in progress — full documentation will be added once the core features are in place.

## Run with Docker

```bash
# JWTs are signed with a secret that is never stored in the repository: generate one locally
echo "JWT_SECRET=$(openssl rand -base64 48)" > .env
docker compose up --build
```

The API starts on `http://localhost:8080` (Swagger UI: `http://localhost:8080/swagger-ui.html`)
together with PostgreSQL 16. Other optional overrides are listed in `.env.example`.

Development users (created only by the `dev` and `test` profiles):

| Username   | Password       | Role     |
|------------|----------------|----------|
| `admin`    | `Admin123!`    | ADMIN    |
| `owner`    | `Owner123!`    | OWNER    |
| `employee` | `Employee123!` | EMPLOYEE |

## Build

```bash
./mvnw verify
```

Integration tests use [Testcontainers](https://testcontainers.com/), so a running Docker daemon is required.
