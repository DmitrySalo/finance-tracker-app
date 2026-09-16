---
name: api-contracts
description: Use when creating or changing HTTP endpoints, request or response DTOs, errors, authentication requirements, pagination, or OpenAPI contracts.
---

# API Contracts

Read `.opencode/instructions/api-contracts.md`. Preserve existing URI, JSON, error, and versioning conventions. Treat removals, renamed or retyped fields, new required request fields, authorization changes, and changed status semantics as breaking until compatibility is demonstrated.

Validate input at the API boundary and enforce authorization server-side. Keep persistence entities and internal exceptions out of responses. Add focused HTTP or contract tests for changed behavior, validation, authorization, and error handling.
