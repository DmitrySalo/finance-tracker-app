---
name: frontend-code-review
description: Use when reviewing React or TypeScript frontend diffs for correctness, UX, accessibility, security, API compatibility, and missing tests without modifying files.
---

# Frontend Code Review

Read `ARCHITECTURE.md`, `.opencode/instructions/architecture.md`, `.opencode/instructions/frontend-style.md`, and `.opencode/instructions/testing.md`. Inspect the diff and enough surrounding code to verify each claim. Load `api-contracts` or `secure-development` when their topic applies.

Report only actionable findings. Order them by severity and provide path, line, impact, and a concrete remediation. Focus on React correctness, TypeScript safety, server-state handling, API and authentication compatibility, loading/empty/error states, responsive behavior, keyboard access, semantic HTML, focus management, safe rendering, and test gaps. If there are no findings, say so and name any residual testing limitations.
