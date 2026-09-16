---
name: testing
description: Use when adding, changing, selecting, or reviewing unit, integration, HTTP, or end-to-end tests.
---

# Testing

Read `.opencode/instructions/testing.md`. Test observable behavior and regressions with the smallest reliable level of the test pyramid. Keep tests deterministic: no real external services, production data, timing guesses, or order dependencies.

For API changes cover success, validation, authorization, and error contracts. For database changes cover migrations and persistence constraints where relevant. Run the most focused available checks first and report exact commands, results, and intentionally skipped checks.
