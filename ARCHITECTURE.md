# Architecture

## Шаг 0. Инициализация проекта

### Технологический стек

- Java 21 с использованием Gradle toolchain.
- Spring Boot 3.5.6.
- Gradle 9.1.0 и Gradle Wrapper.
- JUnit 5 и Spring Boot Test.

### Структура приложения

```text
src/
  main/
    java/ru/otus/financetracker/
      FinanceTrackerApplication.java
    resources/
      application.yaml
  test/
    java/ru/otus/financetracker/
      FinanceTrackerApplicationTests.java
```

`FinanceTrackerApplication` является точкой входа Spring Boot приложения.

### Сборка и проверка

- Зависимости разрешаются из Maven Central.
- Включен `spring-boot-starter-web` для HTTP-приложения.
- Проверка контекста Spring Boot выполняется тестом `FinanceTrackerApplicationTests`.
- Основная команда сборки: `./gradlew build`.

### Конфигурация и служебные файлы

- Имя приложения задано в `src/main/resources/application.yaml`.
- `.gitignore` исключает результаты сборки, кэш Gradle, файлы IDE, логи и локальные переменные окружения.
- `README.md` содержит минимальные требования и команды запуска.
