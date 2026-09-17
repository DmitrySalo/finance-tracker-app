# Инструкции backend

## Обязательный порядок работы

В дополнение к корневому `AGENTS.md` перед изменением backend-кода:

1. Прочитай `.opencode/instructions/java-style.md`.
2. Если задача затрагивает тесты — прочитай `.opencode/instructions/testing.md`.
3. Если задача затрагивает БД, миграции или JPA — прочитай `.opencode/instructions/database.md`.
4. Если задача затрагивает HTTP API — прочитай `.opencode/instructions/api-contracts.md`.
5. Если задача затрагивает безопасность, аутентификацию или авторизацию — прочитай `.opencode/instructions/security.md`.

## Технологии и структура

- Язык backend: Java 21. Используй Java toolchain, заданный в корневом `build.gradle`.
- Основной framework: Spring Boot 4.1.0 согласно `ARCHITECTURE.md`; обновление с текущей версии выполняется отдельной проверяемой задачей.
- Сохраняй слои `api`, `application`, `domain`, `infrastructure`, `configuration` и `shared` из `ARCHITECTURE.md`.
- Контроллеры не содержат бизнес-логики и не обращаются напрямую к JPA-репозиториям. JPA entities не покидают infrastructure-слой.
- Изменения схемы выполняй только новыми Flyway-миграциями в `backend/src/main/resources/db/migration/`; не редактируй примененные миграции.
- Не добавляй backend-зависимости, если задачу можно решить средствами JDK или уже подключённых библиотек.

## Проверки

- После изменений запускай релевантные Gradle-задачи.
- Полная backend-проверка: `./gradlew verify`.
- Для изменений JPA или миграций выполни релевантные интеграционные тесты с PostgreSQL/Testcontainers.
