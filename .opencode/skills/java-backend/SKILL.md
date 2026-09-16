---
name: java-backend
description: Use when implementing or refactoring Java, Spring Boot, JPA, REST, or backend integration code.
---

# Java Backend

Read `.opencode/instructions/java-style.md` and follow the existing module structure and conventions. Keep HTTP, business logic, persistence, and integrations separated. Use constructor injection, explicit DTO mapping, safe transactions, `BigDecimal` for money, and typed time values.

Before editing, inspect analogous production code and tests. Keep the change scoped. Load `api-contracts`, `database-migrations`, `secure-development`, and `testing` only when their topic applies. Run the narrowest relevant Maven verification, then `./mvnw verify` when warranted by the change or project rule.
