Реализуй Шаг 33 из Плана разработки ARCHITECTURE.md.

Создай GitHub Actions workflow: backend test/verify, frontend lint/test/build, при необходимости Docker build; включи dependency и secret scanning, не отключая проверки ради зеленого pipeline. Workflow должен запускаться на push и pull request.

Не добавляй секреты, не изменяй существующие Gradle, npm или Docker интерфейсы. Соблюдай принцип минимальных привилегий для GITHUB_TOKEN и закрепи внешние actions на неизменяемых commit SHA.
