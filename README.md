# Finance Tracker

Full-stack application for tracking personal income, expenses, monthly budgets, recurring operations, analytics, and CSV import/export.

## Quick Start With Docker

Prerequisites: Docker Engine with Docker Compose v2.

```shell
docker compose up --build
```

Open the application at `http://localhost:8080`. The initial build starts PostgreSQL, the Spring Boot API, and the React SPA. Docker Compose waits for the database and backend health checks before starting the frontend.

Stop the stack with `docker compose down`. Add `--volumes` to also remove the local PostgreSQL data.

## Configuration

Docker Compose has development-only defaults. To override them, copy `.env.example` to `.env` and change the values before running the stack. Never commit `.env`.

| Variable | Description | Default for Docker Compose |
|---|---|---|
| `POSTGRES_DB` | PostgreSQL database name | `finance_tracker` |
| `POSTGRES_USER` | PostgreSQL application user | `finance_tracker_app` |
| `POSTGRES_PASSWORD` | PostgreSQL password | fictional development value |
| `DATASOURCE_URL` | Backend PostgreSQL JDBC URL for a direct local run | `jdbc:postgresql://localhost:5432/finance_tracker` |
| `DATASOURCE_USERNAME` | Backend PostgreSQL user for a direct local run | `finance_tracker_app` |
| `DATASOURCE_PASSWORD` | Backend PostgreSQL password for a direct local run | local development value |
| `JWT_ISSUER` | JWT issuer | `finance-tracker` |
| `JWT_AUDIENCE` | JWT audience | `finance-tracker-web` |
| `JWT_SECRET` | HMAC JWT secret; replace before deployment | fictional development value |
| `JWT_ACCESS_TOKEN_TTL` | Access-token lifetime | `15m` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated trusted browser origins | `http://localhost:8080` |
| `MAX_REQUEST_SIZE` | Maximum HTTP request size | `2MB` |
| `MAX_CSV_FILE_SIZE` | Maximum CSV upload size | `1MB` |
| `MAX_CSV_ROWS` | Maximum imported or exported CSV rows | `10000` |
| `MAX_REQUEST_HEADER_SIZE` | Maximum HTTP header size | `16KB` |
| `REGISTRATION_MAX_ATTEMPTS` | Registration/login attempts per window | `5` |
| `REGISTRATION_WINDOW` | Rate-limit window | `1m` |
| `FRONTEND_PORT` | Host port for the SPA | `8080` |

## Local Development

Prerequisites: Java 21, Node.js 24 or newer, PostgreSQL 18, and Docker for backend integration tests.

Start PostgreSQL and export `DATASOURCE_URL`, `DATASOURCE_USERNAME`, and `DATASOURCE_PASSWORD` for that instance, then run the backend:

```shell
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Run the frontend separately in another terminal:

```shell
cd frontend
npm ci
npm run dev
```

Vite serves the SPA at `http://localhost:5173` and proxies `/api` to the backend at `http://localhost:8080`. Set `CORS_ALLOWED_ORIGINS=http://localhost:5173` for this setup.

## Demo Data And API Documentation

Docker Compose always starts the backend with the `dev` profile. This profile loads deterministic synthetic data: two users, 18 categories, three budgets, and 216 transactions from March through August 2026. All data and accounts are fictional and intended only for a local demonstration.

To recreate the database and load the complete seed data, run:

```shell
docker compose down --volumes
docker compose up --build --detach --wait
```

`docker compose down --volumes` deletes the local PostgreSQL volume of this Compose project. Do not use it if you need to keep locally entered data. Open `http://localhost:8080`, select **Log in**, and use either demonstration account:

| Email | Password | Data available |
|---|---|---|
| `alex.demo@example.test` | `DemoPassword2026` | 9 categories, 2 budgets, 108 transactions |
| `sam.demo@example.test` | `DemoPassword2026` | 9 categories, 1 budget, 108 transactions |

The accounts are isolated from each other. Sign in as each user to verify that transactions, budgets, dashboard data, CSV export/import, recurring transactions, and audit logs are scoped to the current account.

You can confirm that the seed migration completed without exposing password hashes:

```shell
docker compose exec postgres psql -U finance_tracker_app -d finance_tracker -c "SELECT email, display_name FROM users ORDER BY email;"
docker compose exec postgres psql -U finance_tracker_app -d finance_tracker -c "SELECT COUNT(*) AS transaction_count FROM transactions;"
```

With the `dev` profile active, Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`, and the OpenAPI document is at `http://localhost:8080/v3/api-docs`.

The health endpoint is `http://localhost:8080/actuator/health`.

## Verification

```shell
./gradlew verify
cd frontend
npm run lint
npm run test
npm run build
```

The E2E flow uses the Docker Compose deployment and covers registration, login, category and transaction creation, budget and dashboard updates, and CSV export/import:

```shell
docker compose up --build --detach --wait
cd frontend
npx playwright install chromium
npm run test:e2e
```

After the E2E run, stop the test environment with `docker compose down --volumes`.

---

# Finance Tracker

Full-stack приложение для учёта личных доходов, расходов, месячных бюджетов, повторяющихся операций, аналитики, а также импорта и экспорта CSV.

## Быстрый Запуск С Docker

Требование: Docker Engine с Docker Compose v2.

```shell
docker compose up --build
```

Откройте приложение по адресу `http://localhost:8080`. При первой сборке запускаются PostgreSQL, Spring Boot API и React SPA. Docker Compose ждёт healthcheck базы данных и backend до запуска frontend.

Остановить окружение можно командой `docker compose down`. Добавьте `--volumes`, чтобы также удалить локальные данные PostgreSQL.

## Конфигурация

Docker Compose использует значения по умолчанию только для разработки. Чтобы переопределить их, скопируйте `.env.example` в `.env` и измените значения перед запуском. Не добавляйте `.env` в Git.

| Переменная | Описание | Значение по умолчанию для Docker Compose |
|---|---|---|
| `POSTGRES_DB` | Имя базы данных PostgreSQL | `finance_tracker` |
| `POSTGRES_USER` | Пользователь PostgreSQL | `finance_tracker_app` |
| `POSTGRES_PASSWORD` | Пароль PostgreSQL | фиктивное значение для разработки |
| `DATASOURCE_URL` | JDBC URL PostgreSQL для прямого локального запуска backend | `jdbc:postgresql://localhost:5432/finance_tracker` |
| `DATASOURCE_USERNAME` | Пользователь PostgreSQL для прямого локального запуска backend | `finance_tracker_app` |
| `DATASOURCE_PASSWORD` | Пароль PostgreSQL для прямого локального запуска backend | локальное значение для разработки |
| `JWT_ISSUER` | Issuer JWT | `finance-tracker` |
| `JWT_AUDIENCE` | Audience JWT | `finance-tracker-web` |
| `JWT_SECRET` | HMAC-секрет JWT; замените перед развёртыванием | фиктивное значение для разработки |
| `JWT_ACCESS_TOKEN_TTL` | Время жизни access token | `15m` |
| `CORS_ALLOWED_ORIGINS` | Разрешённые browser origin через запятую | `http://localhost:8080` |
| `MAX_REQUEST_SIZE` | Максимальный размер HTTP request | `2MB` |
| `MAX_CSV_FILE_SIZE` | Максимальный размер загружаемого CSV | `1MB` |
| `MAX_CSV_ROWS` | Максимальное число импортируемых или экспортируемых строк CSV | `10000` |
| `MAX_REQUEST_HEADER_SIZE` | Максимальный размер HTTP header | `16KB` |
| `REGISTRATION_MAX_ATTEMPTS` | Число попыток регистрации/login за окно | `5` |
| `REGISTRATION_WINDOW` | Окно rate limit | `1m` |
| `FRONTEND_PORT` | Порт SPA на хосте | `8080` |

## Локальная Разработка

Требования: Java 21, Node.js 24 или новее, PostgreSQL 18 и Docker для backend integration tests.

Запустите PostgreSQL и задайте `DATASOURCE_URL`, `DATASOURCE_USERNAME` и `DATASOURCE_PASSWORD` для этого экземпляра, затем запустите backend:

```shell
./gradlew bootRun --args='--spring.profiles.active=dev'
```

В отдельном терминале запустите frontend:

```shell
cd frontend
npm ci
npm run dev
```

Vite запускает SPA по адресу `http://localhost:5173` и проксирует `/api` в backend на `http://localhost:8080`. Для такого запуска установите `CORS_ALLOWED_ORIGINS=http://localhost:5173`.

## Демонстрационные Данные И Документация API

Docker Compose всегда запускает backend с профилем `dev`. Этот профиль загружает детерминированные синтетические данные: двух пользователей, 18 категорий, три бюджета и 216 транзакций с марта по август 2026 года. Все данные и учётные записи фиктивны и предназначены только для локальной демонстрации.

Чтобы пересоздать БД и загрузить полный набор seed-данных, выполните:

```shell
docker compose down --volumes
docker compose up --build --detach --wait
```

`docker compose down --volumes` удаляет локальный PostgreSQL volume этого Compose-проекта. Не используйте команду, если необходимо сохранить внесённые локально данные. Откройте `http://localhost:8080`, выберите **Войти** и используйте одну из демонстрационных учётных записей:

| Email | Пароль | Доступные данные |
|---|---|---|
| `alex.demo@example.test` | `DemoPassword2026` | 9 категорий, 2 бюджета, 108 транзакций |
| `sam.demo@example.test` | `DemoPassword2026` | 9 категорий, 1 бюджет, 108 транзакций |

Данные аккаунтов изолированы друг от друга. Войдите под каждым пользователем, чтобы проверить, что транзакции, бюджеты, данные dashboard, экспорт/импорт CSV, повторяющиеся операции и аудит ограничены текущей учётной записью.

Создание seed-данных можно проверить через PostgreSQL, не выводя password hash:

```shell
docker compose exec postgres psql -U finance_tracker_app -d finance_tracker -c "SELECT email, display_name FROM users ORDER BY email;"
docker compose exec postgres psql -U finance_tracker_app -d finance_tracker -c "SELECT COUNT(*) AS transaction_count FROM transactions;"
```

При активном профиле `dev` Swagger UI доступен по адресу `http://localhost:8080/swagger-ui/index.html`, а OpenAPI-документ - по адресу `http://localhost:8080/v3/api-docs`.

Health endpoint: `http://localhost:8080/actuator/health`.

## Проверки

```shell
./gradlew verify
cd frontend
npm run lint
npm run test
npm run build
```

E2E flow использует Docker Compose deployment и проверяет регистрацию, login, создание категории, транзакции и бюджета, обновление dashboard, экспорт CSV, preview и confirm import:

```shell
docker compose up --build --detach --wait
cd frontend
npx playwright install chromium
npm run test:e2e
```

После E2E-запуска остановите тестовое окружение командой `docker compose down --volumes`.
