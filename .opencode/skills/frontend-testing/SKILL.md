---
name: frontend-testing
description: Use when writing, changing, or selecting React and TypeScript frontend tests with Vitest, Testing Library, or Playwright.
---

# Frontend Testing

Read `.opencode/instructions/frontend-testing.md` and the nearest applicable `AGENTS.md`. Test user-observable behavior at the smallest reliable level: pure logic with unit tests, UI states and interactions with component/integration tests, and only critical cross-browser flows with E2E tests when Playwright is configured.

Use accessible Testing Library queries and deterministic async assertions. Mock network and browser boundaries with API-contract-shaped data; restore mocks, session, history, query cache, and browser state after every test. Cover relevant success, validation, API error, authorization, loading, empty, and mutation states. Run focused tests first, then the relevant frontend checks, and report exact commands and results.
