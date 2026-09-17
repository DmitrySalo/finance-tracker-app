# Report

Журнал выполненных этапов проекта будет вестись в этом файле.

## 15.09.2026

Стартовый AI стек:
- opencode
- gpt-5.6 terra

Без скиллов и MCP.

### Шаг. 0
Попросил агента написать основу приложения.

[Промт.](promts/init-commit.md)

### Шаг. 1
Попросил агента добавить документы для фиксирования работы над проектом.

[Промт.](promts/add-documents.md)

## 16.09.2026

### Шаг. 2
Добавил в проект новых агентов.
Переделал правила под формат opencode.
Добавил в проект новые skills.
Добавил в opencode MCP Context7.

Для реализации этого шага я активно пользовался чат-версией gpt-5.6 terra и opencode чтобы снизить кол-во рутинных действий до минимума.
[Промт.](promts/add-preparation-agent-infrastructure.md)

### Шаг. 3
Сформировал и добавил ТЗ для проекта.

### Шаг. 4
Изучение ТЗ.
Разработка архитектуры и пошагового плана разработки.
Подбор и утверждение технологического стека.

[Промт.](promts/add-architecture-and-development-plan.md)

### Шаг. 5
Перепроверил подготовленные для проекта инфраструктуру opencode и инструкций. Внёс соответствующие правки.

[Промт.](promts/add-resolution-logical-conflicts.md)

### Шаг. 6
Донастройка суб-агентов.

[Промт.](promts/add-fine-tuning-subagents.md)

### Шаг 7
Обновить основу backend.

[Промт.](promts/update-backend-foundation.md)

Данная запись сгенерирована агентом.

### Шаг 8
Настроить профили и безопасную конфигурацию.

[Промт.](promts/configure-profiles-and-secure-configuration.md)

Данная запись сгенерирована агентом.

### Шаг 9
Подключить Flyway и начальную схему identity.

[Промт.](promts/connect-flyway-and-create-identity-schema.md)

Данная запись сгенерирована агентом.

### Шаг 10
Реализовать общий HTTP-контракт.

[Промт.](promts/implement-common-http-contract.md)

Данная запись сгенерирована агентом.

### Шаг 11
Реализовать регистрацию.

[Промт.](promts/implement-user-registration.md)

Данная запись сгенерирована агентом.

### Шаг 12
Реализовать login и JWT security.

[Промт.](promts/implement-login-and-jwt-security.md)

Данная запись сгенерирована агентом.

### Шаг 13
Создать схему и persistence категорий.

[Промт.](promts/create-categories-schema-and-persistence.md)

Данная запись сгенерирована агентом.

### Шаг 14
Реализовать API категорий.

[Промт.](promts/implement-categories-api.md)

Данная запись сгенерирована агентом.

### Шаг 15
Создать схему транзакций.

[Промт.](promts/create-transactions-schema.md)

Данная запись сгенерирована агентом.

### Шаг 16
Реализовать создание и чтение транзакций.

[Промт.](promts/implement-transaction-create-and-read.md)

Данная запись сгенерирована агентом.

### Шаг 17
Реализовать список транзакций.

[Промт.](promts/implement-transaction-list.md)

Данная запись сгенерирована агентом.

### Шаг 18
Реализовать изменение и удаление транзакций.

[Промт.](promts/implement-transaction-update-and-delete.md)

Данная запись сгенерирована агентом.

## 17.09.2026

### Шаг 19
Добавить аудит транзакций.

[Промт.](promts/add-transaction-audit.md)

Данная запись сгенерирована агентом.

### Шаг 20
Создать и реализовать бюджеты.

[Промт.](promts/create-and-implement-budgets.md)

Данная запись сгенерирована агентом.

### Шаг 21
Реализовать расчет бюджета.

[Промт.](promts/implement-budget-calculation.md)

Данная запись сгенерирована агентом.

### Шаг 22
Реализовать dashboard API.

[Промт.](promts/implement-dashboard-api.md)

Данная запись сгенерирована агентом.

### Шаг 23
Реализовать recurring transactions.

[Промт.](promts/implement-recurring-transactions.md)

Данная запись сгенерирована агентом.

### Шаг 24
Реализовать экспорт CSV.

[Промт.](promts/implement-transaction-csv-export.md)

Данная запись сгенерирована агентом.

### Шаг 25
Реализовать preview CSV-импорта.

[Промт.](promts/implement-transaction-csv-import-preview.md)

Данная запись сгенерирована агентом.

### Шаг 26
Реализовать подтверждение CSV-импорта.

[Промт.](promts/implement-transaction-csv-import-confirmation.md)

Данная запись сгенерирована агентом.

### Шаг 27
Завершить backend API и seed-данные.

[Промт.](promts/complete-backend-api-and-seed-data.md)

Данная запись сгенерирована агентом.

### Шаг 28
Инициализировать frontend.

[Промт.](promts/initialize-frontend.md)

Данная запись сгенерирована агентом.

### Шаг 29
Построить frontend foundation.

[Промт.](promts/build-frontend-foundation.md)

Данная запись сгенерирована агентом.

### Шаг 30
Реализовать auth UI.

[Промт.](promts/implement-auth-ui.md)

Данная запись сгенерирована агентом.

### Шаг 31
Реализовать UI категорий.

[Промт.](promts/implement-categories-ui.md)

Данная запись сгенерирована агентом.

### Шаг 32
Реализовать UI транзакций.

[Промт.](promts/implement-transactions-ui.md)

Данная запись сгенерирована агентом.

### Шаг 33
Реализовать CSV UI.

[Промт.](promts/implement-csv-ui.md)

Данная запись сгенерирована агентом.

### Шаг 34
Реализовать UI бюджетов.

[Промт.](promts/implement-budgets-ui.md)

Данная запись сгенерирована агентом.

### Шаг 35
Реализовать UI дашборда.

[Промт.](promts/implement-dashboard-ui.md)

Данная запись сгенерирована агентом.

### Шаг 36
Реализовать UI повторяющихся операций и аудита.

[Промт.](promts/implement-recurring-rules-and-audit-ui.md)

Данная запись сгенерирована агентом.

### Шаг 37
Контейнеризировать приложение.

[Промт.](promts/containerize-application.md)

Данная запись сгенерирована агентом.

### Шаг 38
Провожу промежуточную ретроспективу и некоторые доработки:
1. Ради эксперимента, запустил сразу реализацию шагов 9 - 15 и 16 - 31 плана разработки по следующим [промтам](promts/batch-start-development-steps.md).
2. Далеко не всё было учтено в архитектуре и инструкциях к агенту, но как эксперимент, результат превзошёл все ожидания.
3. Внёс небольшие изменения в структуру проекта по [промту](promts/change-project-structure-for-backend.md).
4. Внёс небольшие изменения в инструкции для агентов по [промту](promts/add-fine-tuning-subagents-2.md).
5. Внёс изменения в структуру java-кода согласно новому правилу в [java-style.md](.opencode/instructions/java-style.md) по [промту](promts/refactoring-backend.md).
6. Немного изменил структуру тестов по [промту](promts/refactoring-backend-tests.md).
7. Решил добавить новое функциональное требование.

### Шаг 39
Локализовать пользовательский интерфейс.

[Промт.](promts/localize-user-interface.md)

Данная запись сгенерирована агентом.

### Шаг 40
Добавить CI.

[Промт.](promts/add-github-actions-ci.md)

Данная запись сгенерирована агентом.
