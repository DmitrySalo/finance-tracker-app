---
name: database-migrations
description: Use when changing JPA mappings, SQL, Flyway migrations, indexes, constraints, transactions, or persistent data.
---

# Database Migrations

Read `.opencode/instructions/database.md`. Use a new Flyway migration for every schema or persistent-data change; never edit an applied migration. Prefer expand-migrate-contract for deployed schemas and stop for explicit confirmation before destructive operations.

Model integrity with database constraints where appropriate, use parameterized queries, and assess indexes from actual query paths. Validate against clean and upgraded schemas when project tooling supports it; include migration, repository, and concurrency tests proportionate to risk.
