# Архитектура Finance Tracker

## Назначение

Finance Tracker - full-stack приложение для учета личных доходов и расходов. Пользователь регистрируется, ведет изолированные транзакции, категории, месячные бюджеты и повторяющиеся операции, анализирует статистику, импортирует и экспортирует CSV. Приложение запускается одной командой `docker compose up`.

Первый релиз покрывает все требования `TECHNICAL_SPECIFICATIONS.md`: JWT-аутентификацию, CRUD, фильтрацию и пагинацию, бюджеты, дашборд, CSV, повторяющиеся операции, мультивалютность, аудит, seed-данные, OpenAPI, тесты, Docker Compose и CI.

Выбран модульный монолит: один backend, одна PostgreSQL БД и один SPA frontend. Он сохраняет транзакционную целостность финансовых данных, прост в развертывании и не требует распределенных транзакций. Микросервисы, брокер сообщений, кэш и отдельное файловое хранилище не нужны в первом релизе.

## Контекст и развертывание

```text
Browser
  | HTTPS / HTTP
  v
React SPA (Nginx)
  | /api/v1, Bearer JWT
  v
Spring Boot REST API
  | JDBC/HikariCP
  v
PostgreSQL

Docker Compose: frontend + backend + postgres
GitHub Actions: backend checks + frontend checks
```

Frontend в production собирается в статические файлы и отдается Nginx. Nginx проксирует `/api/` в backend, поэтому браузер использует единый origin. В development Vite проксирует API на локальный backend. PostgreSQL - единственный источник истины; Flyway применяет миграции до доступа Hibernate к схеме.

Swagger UI доступен в development или защищается в развернутом окружении. Actuator публикует только `health` и `info`; Docker healthcheck backend использует health endpoint.

## Backend

Пакетный корень: `ru.otus.financetracker`.

```text
src/main/java/ru/otus/financetracker/
├── api/                    # controllers, request/response DTO, error handler
├── application/            # use cases, команды, query services, транзакции
├── domain/                 # бизнес-модель, правила, enums, порты
├── infrastructure/         # JPA, CSV, security, scheduler
├── configuration/          # Spring-конфигурация, properties, Clock, OpenAPI
└── shared/                 # pagination, error codes, correlation ID
```

- `api` содержит HTTP-контракт, DTO и Bean Validation. Контроллеры вызывают application-сервисы, но не JPA-репозитории.
- `application` координирует сценарии, проверяет ownership и определяет границы `@Transactional`.
- `domain` не зависит от Spring, JPA, HTTP и файловой системы. День транзакции представлен `LocalDate`, деньги - `BigDecimal`.
- `infrastructure` реализует порты через Hibernate, PostgreSQL, JWT, CSV и Spring Scheduler. JPA entities не покидают этот слой.
- API request/response, доменная модель и persistence entities - отдельные типы. Неизменяемые DTO реализуются records.

| Модуль | Ответственность |
|---|---|
| Identity | Регистрация, вход, выпуск и проверка JWT, текущий пользователь. |
| Categories | Категории пользователя, тип дохода/расхода, цвет, иконка. |
| Transactions | Транзакции, фильтрация, экспорт, изоляция данных. |
| Budgets | Лимиты по категории и месяцу, расчет прогресса. |
| Recurring transactions | Правила повторения и идемпотентное создание экземпляров. |
| Analytics | Агрегации PostgreSQL для дашборда. |
| Imports | Валидация CSV, маппинг колонок и отчет обработки. |
| Audit | Неизменяемый журнал изменения транзакций и бюджетов. |

### Транзакции, время и конкурентность

- Изменяющие use case выполняются в сервисном `@Transactional`; чтение - в `@Transactional(readOnly = true)`.
- Транзакции, бюджеты, категории и повторяющиеся правила содержат `@Version`. Конфликт обновления возвращает `409 Conflict`.
- Сумма всегда положительна. Направление задается `TransactionType` (`INCOME`, `EXPENSE`), а не знаком суммы.
- День операции хранится как `DATE`; моменты создания, изменения и аудита - UTC `TIMESTAMP WITH TIME ZONE`. Преобразования используют явный `ZoneId`, бизнес-код получает `Clock` через DI.
- Планировщик запускается ежедневно. Уникальность `(recurring_transaction_id, occurrence_date)` предотвращает дубли при перезапуске или параллельном запуске. День 29-31 в коротком месяце переносится на последний день месяца.
- CSV обрабатывается потоково, с ограничением размера файла и числа строк, вне долгой транзакции. После полной валидации строки сохраняются ограниченными батчами.

## Данные

Все бизнес-таблицы используют UUID как неизменяемый первичный ключ. Удаление транзакций, бюджетов, категорий и правил физическое: обязательный audit log хранит историю. Категорию нельзя удалить, пока существуют ссылающиеся транзакции или бюджеты: API возвращает `409 Conflict`.

| Таблица | Основные поля и инварианты |
|---|---|
| `users` | `id`, `email`, `password_hash`, `display_name`, `base_currency`, timestamps, `version`; email уникален без учета регистра. |
| `categories` | `id`, `user_id`, `name`, `transaction_type`, `icon`, `color`, timestamps, `version`; уникальность `(user_id, transaction_type, name)`. |
| `transactions` | `id`, `user_id`, `category_id`, `amount NUMERIC(19,4)`, `currency CHAR(3)`, `exchange_rate_to_base NUMERIC(19,8)`, `transaction_date`, `description`, `transaction_type`, `recurring_transaction_id`, timestamps, `version`; сумма и курс больше нуля, тип совпадает с категорией. |
| `budgets` | `id`, `user_id`, `category_id`, `budget_month DATE`, `limit_amount NUMERIC(19,4)`, `currency CHAR(3)`, timestamps, `version`; месяц - первый день месяца, лимит положителен, валюта совпадает с `users.base_currency`, уникальность `(user_id, category_id, budget_month)`, только расходная категория. |
| `recurring_transactions` | `id`, `user_id`, `category_id`, `amount`, `currency`, `exchange_rate_to_base`, `description`, `transaction_type`, `day_of_month`, `start_date`, `next_occurrence_date`, `active`, timestamps, `version`. |
| `recurring_transaction_occurrences` | `id`, `recurring_transaction_id`, `occurrence_date`, `transaction_id`, `created_at`; уникальность `(recurring_transaction_id, occurrence_date)`. |
| `audit_logs` | `id`, `actor_user_id`, `entity_type`, `entity_id`, `action`, `occurred_at`, `before_state`, `after_state`; состояния содержат только нужные бизнес-поля. |

Внешние ключи обеспечивают обязательные связи. Индексы:

- `transactions(user_id, transaction_date DESC, id DESC)` для списка и периода;
- `transactions(user_id, category_id, transaction_date DESC)` для фильтра, бюджета и аналитики;
- `budgets(user_id, budget_month, category_id)`;
- `audit_logs(actor_user_id, occurred_at DESC)` и `audit_logs(entity_type, entity_id, occurred_at DESC)`;
- `recurring_transactions(active, next_occurrence_date)` для scheduler.

Валюта бюджета всегда совпадает с базовой валютой его владельца; это закреплено составным foreign key `(budgets.user_id, budgets.currency)` на `(users.id, users.base_currency)`. Поэтому расход бюджета рассчитывается в его валюте как `SUM(transactions.amount * transactions.exchange_rate_to_base)` по расходным транзакциям категории в полуинтервале месяца `[budget_month, budget_month + 1 month)`. Используется только неизменяемый сохраненный курс транзакции; внешние курсы при расчете не запрашиваются. `spentAmount` и `remainingAmount` округляются до четырех десятичных знаков `HALF_UP`; `percentage` рассчитывается от округленного расхода с точностью до двух десятичных знаков `HALF_UP`.

Миграции находятся в `src/main/resources/db/migration/` и создаются только через Flyway как `V<номер>__<описание>.sql`. Примененные миграции не редактируются. Hibernate выполняет `ddl-auto=validate`. Отдельная детерминированная Flyway-миграция создает два демонстрационных пользователя, 12 категорий, три бюджета и более 200 транзакций за шесть месяцев. Используются только синтетические данные и заведомо фиктивные пароли.

## REST API

Базовый путь: `/api/v1`. JSON UTF-8, `camelCase`, UUID строкой, суммы decimal-строкой, даты `YYYY-MM-DD`, timestamps ISO 8601 UTC. Списки используют единый envelope:

```json
{
  "items": [],
  "page": { "number": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
}
```

`size` ограничен 100. Сортировка проходит allowlist. `userId` не принимается от клиента: субъект извлекается только из `SecurityContext`.

| Группа | Endpoint-ы |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` |
| Categories | `GET/POST /categories`, `GET/PATCH/DELETE /categories/{categoryId}` |
| Transactions | `GET/POST /transactions`, `GET/PATCH/DELETE /transactions/{transactionId}`, `GET /transactions/export` |
| Import | `POST /transactions/imports/preview`, `POST /transactions/imports` |
| Budgets | `GET/POST /budgets`, `GET/PATCH/DELETE /budgets/{budgetId}` |
| Recurring | `GET/POST /recurring-transactions`, `GET/PATCH/DELETE /recurring-transactions/{recurringTransactionId}` |
| Dashboard | `GET /dashboard?month=YYYY-MM`, `GET /dashboard/spending-trend?endMonth=YYYY-MM` |
| Audit | `GET /audit-logs?entityType=...&entityId=...` |

`GET /transactions` поддерживает `fromDate`, `toDate`, `categoryId`, `minAmount`, `maxAmount`, `transactionType`, `page`, `size`, `sort`. Валидируются пары дат и сумм, а категория должна принадлежать текущему пользователю. Экспорт принимает те же фильтры, формирует потоковый CSV с `Content-Disposition: attachment` и ограничением числа записей.

Импорт разделен на preview и подтверждение. Preview разбирает CSV и mapping, возвращает распознанные строки и ошибки без записи. Подтверждение повторно валидирует файл и mapping. Политика первого релиза - атомарный импорт: ошибка хотя бы в одной строке не создает транзакций.

Ошибки единообразны и безопасны:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "traceId": "...",
  "violations": [{ "field": "amount", "code": "POSITIVE", "message": "Must be positive." }]
}
```

OpenAPI генерируется из контроллеров и DTO через Springdoc. Для каждого endpoint-а описываются security, примеры, фильтры, статусы и ошибки.

## Безопасность и наблюдаемость

- Публичны только `POST /api/v1/auth/register`, `POST /api/v1/auth/login` и healthcheck. Все остальные API требуют Bearer JWT.
- JWT короткоживущий, подписывается секретом из environment variable; проверяются алгоритм, issuer, audience и срок действия. Секреты и данные БД не попадают в Git.
- Пароль хранится только как bcrypt или Argon2id hash через Spring Security `PasswordEncoder` и не попадает в логи, аудит или response.
- Любой query и mutation ограничены `user_id` текущего пользователя. Доступ к чужому UUID возвращает `404 Not Found`.
- CORS ограничивается origin frontend. Авторизация не использует cookie, поэтому CSRF-риска cookie-сессий нет.
- Ограничиваются HTTP body, CSV, число строк, длины полей и page size. Имя файла не используется как путь. Формулы CSV экранируются при экспорте для предотвращения CSV injection.
- Валидация реализуется на HTTP, business и DB уровнях. Клиент не получает stack trace, SQL, токены, password hash и технические детали.
- Login и registration защищаются rate limit по IP и нормализованному email. В первом релизе это ограниченный in-memory bucket; для горизонтального масштабирования его следует заменить shared storage или gateway rate limit.
- В каждый запрос добавляется входящий либо сгенерированный `X-Correlation-Id`; структурированные логи содержат только безопасные идентификаторы. Метрики включают HTTP latency/error rate и результат scheduler/import.

## Frontend

Frontend - React SPA в `frontend/`, сгруппированный по пользовательским возможностям.

```text
frontend/src/
├── app/                    # router, providers, shell, global styles
├── shared/                 # API client, UI primitives, formatters, validators
├── features/
│   ├── auth/
│   ├── categories/
│   ├── transactions/
│   ├── budgets/
│   ├── recurring-transactions/
│   ├── dashboard/
│   └── audit/
└── pages/                  # маршруты и композиция feature-компонентов
```

- React Router защищает приватные маршруты и перенаправляет неаутентифицированного пользователя на login.
- TanStack Query владеет server state, кеширует только необходимые read-запросы и инвалидирует query после mutation. Локальное UI state остается в компонентах.
- HTTP client добавляет Bearer token из памяти приложения. После обновления страницы пользователь повторно входит в систему; это безопасный компромисс первого релиза без refresh-token cookie.
- React Hook Form и Zod выполняют клиентскую валидацию до отправки. Серверная ошибка остается источником истины и отображается рядом с полем или как безопасное уведомление.
- Recharts получает уже агрегированные dashboard response, не вычисляет финансовые итоги из неограниченного списка транзакций в браузере.
- UI адаптивен: таблица транзакций на mobile превращается в карточки, фильтры доступны в сворачиваемой панели, действия доступны с клавиатуры, поля имеют labels и видимый focus.
- Пользовательский текст выводится стандартным экранированием React. `dangerouslySetInnerHTML` не используется. Токены не хранятся в `localStorage`.

Маршруты: `/login`, `/register`, `/dashboard`, `/transactions`, `/categories`, `/budgets`, `/recurring-transactions`, `/audit-logs`.

## Технологии и совместимость

| Область | Технология | Версия и назначение |
|---|---|---|
| Язык backend | Java | 21 LTS. |
| Основной framework | Spring Boot | 4.1.0, последняя стабильная версия на момент подготовки архитектуры. Управляет совместимыми версиями Spring Framework, Security, Data и инфраструктурных библиотек. |
| ORM | Hibernate ORM | 7.4.8.Final, управляется BOM Spring Boot 4.1.0. Версию не фиксировать вручную. |
| Web/API | Spring MVC, Jakarta Validation | Из Spring Boot BOM; REST, multipart и Bean Validation. |
| Security | Spring Security, OAuth2 Resource Server, JOSE | Из BOM; password hashing и проверка JWT. |
| Persistence | Spring Data JPA, HikariCP, PostgreSQL JDBC | Из BOM; JPA repositories и connection pool. |
| СУБД | PostgreSQL | 18.x stable Docker image; production-совместимый SQL. |
| Миграции | Flyway | Версия из Spring Boot BOM; единственный механизм изменения схемы. |
| API documentation | springdoc-openapi | 3.1.0; совместима со Spring Boot 4.1.0, Swagger UI и OpenAPI 3. |
| Тесты backend | JUnit 5, Spring Boot Test, Testcontainers PostgreSQL | Версии из BOM; unit, HTTP integration и реальные миграции PostgreSQL. |
| Сборка backend | Gradle Wrapper | 9.1.0; Java toolchain 21. |
| Язык frontend | TypeScript | 5.9.x. |
| UI framework | React | 19.2.x. |
| Сборка frontend | Vite | 7.x; Node.js 24 LTS. |
| Routing | React Router | 7.x. |
| Server state | TanStack Query | 5.x. |
| Формы | React Hook Form + Zod | 7.x + 4.x; формы и типизированная валидация. |
| Графики | Recharts | 3.x; pie и line charts. |
| UI/CSS | CSS Modules + CSS custom properties | Без тяжелой UI-библиотеки; контролируемый адаптивный интерфейс. |
| Тесты frontend | Vitest, Testing Library, Playwright | Unit/component и ключевые browser E2E сценарии. |
| Контейнеризация | Docker, Docker Compose | Multi-stage images: JDK/Gradle backend, Node build + Nginx frontend. |
| CI | GitHub Actions | Проверки backend и frontend на pull request/push. |

Совместимость обеспечивается Spring Boot BOM: она уже согласует Spring-проекты, Hibernate, HikariCP, драйвер PostgreSQL, JUnit и Testcontainers. Hibernate нельзя независимо обновлять до иной версии. PostgreSQL 18 поддерживается Hibernate ORM 7.4 и JDBC-драйвером из BOM. Spring Boot 4.1 требует минимум Java 17 и совместим с Java 21, выбранной в проекте.

Текущий `build.gradle` использует Spring Boot 3.5.6. Первый шаг реализации обновляет его до 4.1.0 вместе с зависимостями, необходимыми для этого документа; это отдельное проверяемое изменение, а не неявное смешение с бизнес-функциями.

## План разработки

Каждый шаг - самостоятельная задача для одного агента-разработчика с ясным результатом и проверкой. Шаги выполняются строго по порядку; frontend начинается только после стабилизации backend API в шаге 21.

### Backend

**Шаг 1. Обновить основу backend.** Обновить Spring Boot до `4.1.0`, сохранить Java 21 toolchain, добавить только необходимые starter-зависимости: Web, Validation, Data JPA, Security, OAuth2 Resource Server, Actuator, Flyway, PostgreSQL, Springdoc и тестовые Testcontainers. Результат: приложение компилируется и проходит context test на Java 21.

**Шаг 2. Настроить профили и безопасную конфигурацию.** Создать `application.yaml`, `application-dev.yaml`, `application-test.yaml` с внешними переменными для datasource, JWT, CORS и лимитов; добавить `Clock`, Jackson-настройки даты/decimal и лимиты multipart. Результат: отсутствуют секреты в репозитории, некорректная обязательная конфигурация выявляется при старте.

**Шаг 3. Подключить Flyway и начальную схему identity.** Создать миграцию `users` с UUID, уникальным нормализованным email, основной валютой, timestamps и optimistic locking. Установить `ddl-auto=validate`. Результат: чистая PostgreSQL поднимается только миграциями.

**Шаг 4. Реализовать общий HTTP-контракт.** Добавить correlation ID filter, единый pagination envelope, error response, error codes и `@ControllerAdvice` для validation, not found, ownership и optimistic-lock ошибок. Результат: HTTP integration tests подтверждают стабильные 400, 401, 404, 409 и формат ошибок без внутренних деталей.

**Шаг 5. Реализовать регистрацию.** Создать domain/application/API сценарий регистрации, пароль через `PasswordEncoder`, уникальность email в БД и нейтральную обработку дубликатов. Результат: unit и HTTP tests покрывают успешную регистрацию, невалидный пароль и повторный email.

**Шаг 6. Реализовать login и JWT security.** Настроить stateless Spring Security, выпуск access JWT и resource-server validation issuer/audience/expiry; добавить endpoint текущего пользователя. Результат: защищенный endpoint недоступен без JWT, а JWT одного пользователя формирует корректный principal.

**Шаг 7. Создать схему и persistence категорий.** Добавить миграцию `categories`, FK, unique/check constraints и требуемые индексы; реализовать JPA mapping и repository port. Результат: Testcontainers test подтверждает миграцию, ограничения и изоляцию по пользователю.

**Шаг 8. Реализовать API категорий.** Добавить create/list/update/delete категорий, validation name/type/icon/color и запрет удаления используемой категории. Результат: HTTP tests покрывают CRUD, чужой ресурс и конфликт удаления.

**Шаг 9. Создать схему транзакций.** Добавить миграцию `transactions` со всеми денежными полями, валютой, курсом, датой, FK и индексами из раздела данных. Результат: интеграционный тест проверяет ограничения суммы, курса, FK и индексы.

**Шаг 10. Реализовать создание и чтение транзакций.** Добавить domain rules и use cases create/get: тип категории должен совпадать с типом операции, категория принадлежит пользователю, курс фиксируется в операции. Результат: unit tests подтверждают инварианты, API не раскрывает чужие записи.

**Шаг 11. Реализовать список транзакций.** Добавить спецификации/запросы PostgreSQL для фильтров даты, категории, суммы и типа, paginated response и allowlist сортировки. Результат: integration test покрывает каждый фильтр, комбинацию, стабильную сортировку и max page size.

**Шаг 12. Реализовать изменение и удаление транзакций.** Добавить PATCH и DELETE с ownership, `@Version` и записью версии клиента в контракте. Результат: HTTP tests покрывают update, delete, validation, конфликт версии и отсутствие доступа к чужой транзакции.

**Шаг 13. Добавить аудит транзакций.** Создать `audit_logs`, application-компонент неизменяемой записи before/after и аудит create/update/delete в той же транзакции. Результат: integration test подтверждает автора, время, действие, состояние до/после и откат аудита при неуспешной операции.

**Шаг 14. Создать и реализовать бюджеты.** Добавить таблицу `budgets`, CRUD, уникальность категории и месяца, правило только expense-категории, `@Version` и аудит create/update/delete. Результат: unit/integration/HTTP tests покрывают инварианты, ownership, version conflict и audit log.

**Шаг 15. Реализовать расчет бюджета.** Добавить query, считающий расходы категории за месяц в валюте бюджета через сохраненный курс транзакции, сумму расходов, лимит, остаток и процент. Результат: unit test граничных случаев и PostgreSQL integration test агрегирования.

**Шаг 16. Реализовать dashboard API.** Добавить ограниченные агрегирующие запросы: pie расходов по категориям за месяц, line trend за шесть месяцев и top-5 категорий. Результат: integration tests подтверждают период, исключение доходов, группировку и сортировку top-5.

**Шаг 17. Реализовать recurring transactions.** Создать две таблицы правил и occurrences, CRUD правил, ежедневный scheduled use case и создание транзакции в нужную дату. Результат: тесты с fixed `Clock` проверяют последний день короткого месяца, inactive rule и идемпотентность повторного запуска.

**Шаг 18. Реализовать экспорт CSV.** Реализовать streaming export транзакций с теми же фильтрами, разрешенными колонками, корректным quoting и защитой от spreadsheet formulas. Результат: HTTP test проверяет заголовки, CSV content и изоляцию текущего пользователя.

**Шаг 19. Реализовать preview CSV-импорта.** Добавить multipart validation, безопасный CSV parser, mapping входных колонок, лимиты и DTO результата с line errors без сохранения. Результат: tests покрывают корректный файл, неизвестную колонку, неверные amount/date/currency и превышение лимита.

**Шаг 20. Реализовать подтверждение CSV-импорта.** Повторно валидировать file и mapping, реализовать атомарное сохранение валидного набора транзакций и аудит каждого создания. Результат: integration tests доказывают отсутствие частичных данных при одной ошибочной строке и корректную фиксацию всего valid файла.

**Шаг 21. Завершить backend API и seed-данные.** Аннотировать контроллеры Springdoc/OpenAPI, добавить синтетические seed-данные требуемого объема через Flyway, health/info и документацию environment variables. Результат: Swagger описывает все endpoint-ы, новая БД содержит 2 пользователей, 12 категорий, 3 бюджета и 200+ транзакций; backend suite проходит.

### Frontend

**Шаг 22. Инициализировать frontend.** Создать `frontend/` на Vite, React 19, TypeScript и ESLint, настроить npm scripts, API proxy и Docker-compatible production build. Результат: `npm run lint`, `npm run test` и `npm run build` проходят.

**Шаг 23. Построить frontend foundation.** Добавить router, application shell, CSS tokens, responsive layout, API client, TanStack Query provider, notification/error boundary и typed API models. Результат: приложение имеет публичный и защищенный маршруты, а API error contract отображается единообразно.

**Шаг 24. Реализовать auth UI.** Создать register/login forms с React Hook Form и Zod, in-memory session state, logout и guard приватных маршрутов. Результат: component tests проверяют клиентскую валидацию, успешный login и redirect при 401.

**Шаг 25. Реализовать UI категорий.** Создать список, create/edit form, color/icon selector и delete confirmation. Результат: component tests проверяют optimistic invalidation и отображение server validation/conflict error.

**Шаг 26. Реализовать UI транзакций.** Создать адаптивный список, filters, сортировку, пагинацию и create/edit/delete forms; на мобильном экране таблица заменяется карточками. Результат: tests проверяют query serialization, paging, формы и mobile layout.

**Шаг 27. Реализовать CSV UI.** Добавить export по текущим фильтрам, upload, column mapping, preview строк и отображение line errors до подтверждения. Результат: component tests покрывают mapping, disabled confirm при ошибках и загрузку CSV.

**Шаг 28. Реализовать UI бюджетов.** Создать выбор месяца, CRUD бюджета, отображение суммы расхода, лимита, остатка и progress bar с доступными текстовыми значениями. Результат: component tests подтверждают границы 0%, 100% и превышение лимита.

**Шаг 29. Реализовать UI дашборда.** Создать переключатель месяца, pie chart, six-month line chart и top-5 categories из dashboard API с loading/empty/error states. Результат: component tests подтверждают передачу параметров и доступные текстовые альтернативы графиков.

**Шаг 30. Реализовать UI повторяющихся операций и аудита.** Добавить CRUD recurring rules и paginated/filterable audit log для транзакций и бюджетов. Результат: component tests покрывают activation, форму правила и отображение before/after state.

### Поставка и финальная проверка

**Шаг 31. Контейнеризировать приложение.** Добавить Dockerfile backend, multi-stage Dockerfile frontend, Nginx proxy configuration, `.env.example` с фиктивными значениями и `compose.yaml` с volumes, healthchecks и зависимостями. Результат: в чистом окружении `docker compose up --build` запускает три контейнера и открывает приложение.

**Шаг 32. Добавить CI.** Создать GitHub Actions workflow: backend test/verify, frontend lint/test/build, при необходимости Docker build; включить dependency/secret scanning, не отключая проверки ради зеленого pipeline. Результат: workflow запускается на push и pull request.

**Шаг 33. Добавить E2E и завершить документацию.** Реализовать Playwright flow login -> transaction -> budget/dashboard -> CSV export/import, обновить README инструкциями запуска, переменными, demo accounts и Swagger URL. Результат: выполнено не менее 10 unit/integration тестов, ключевой E2E flow проходит, README позволяет поднять приложение без дополнительной настройки.
