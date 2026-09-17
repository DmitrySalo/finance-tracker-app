---
name: architecture-design
description: Use when designing a non-trivial architecture, module boundary, integration, data model, scalability, or deployment approach.
---

# Architecture Design

Read `.opencode/instructions/architecture.md` before proposing the solution. Load `api-contracts`, `database-migrations`, `secure-development`, or `react-frontend` when their scope is involved.

Start with the existing code and the product constraint. Prefer the smallest evolutionary change; do not introduce microservices, storage, queues, or dependencies without evidence that the current design cannot satisfy the requirement.

State the goal, facts and assumptions, proposed boundaries and data flow, contract and data impacts, alternatives, risks, rollback or migration path, and validation plan. Keep the project-root `ARCHITECTURE.md` current: record applied architecture decisions and maintain the development-stage plan. Record only accepted, project-level decisions; do not use it for a small local implementation detail.
