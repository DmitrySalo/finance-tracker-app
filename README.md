# Finance Tracker App

Spring Boot application for personal finance tracking.

## Requirements

- Java 21

## Build and test

```shell
./gradlew verify
```

## Run locally

```shell
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

## Environment variables

The application fails fast if required configuration is absent. Configure the following variables for a local `dev` run:

| Variable | Description | Example value |
|---|---|---|
| `DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/finance_tracker` |
| `DATASOURCE_USERNAME` | PostgreSQL application user | `finance_tracker` |
| `DATASOURCE_PASSWORD` | PostgreSQL application password | Obtain from local secret storage |
| `JWT_ISSUER` | JWT issuer URI | `https://finance-tracker.local` |
| `JWT_AUDIENCE` | Expected JWT audience | `finance-tracker-api` |
| `JWT_SECRET` | At least 32-character HMAC secret | Generate and store outside Git |
| `CORS_ALLOWED_ORIGINS` | Comma-separated trusted frontend origins | `http://localhost:5173` |
| `MAX_REQUEST_SIZE` | Maximum HTTP request size | `1MB` |
| `MAX_CSV_FILE_SIZE` | Maximum uploaded CSV size; not larger than request size | `512KB` |
| `MAX_CSV_ROWS` | Maximum CSV import/export rows | `1000` |
| `MAX_REQUEST_HEADER_SIZE` | Maximum HTTP request-header size | `8KB` |
| `JWT_ACCESS_TOKEN_TTL` | Access token TTL (optional; default `15m`) | `15m` |
| `REGISTRATION_MAX_ATTEMPTS` | Registration/login attempts per window (optional; default `5`) | `5` |
| `REGISTRATION_WINDOW` | Rate-limit window (optional; default `1m`) | `1m` |

The `dev` profile makes Swagger UI available at `/swagger-ui/index.html` and OpenAPI JSON at `/v3/api-docs`. Outside `dev`, Swagger UI and OpenAPI generation are disabled. Actuator exposes only `/actuator/health` and `/actuator/info`; health is public and info requires authentication.

The `dev` Flyway configuration includes `V10__seed_synthetic_demo_data.sql` from `db/demo-migration`, which creates deterministic synthetic data: two users, twelve categories, three budgets, and 216 transactions spanning March through August 2026. Test and non-development runtime use schema migrations only to preserve test isolation. The user identities and transaction descriptions are fictitious; no usable credentials are published.
