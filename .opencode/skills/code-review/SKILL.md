---
name: code-review
description: Use when reviewing a code diff for correctness, regressions, security, compatibility, and missing tests without modifying files.
---

# Code Review

Read `.opencode/instructions/architecture.md`, `.opencode/instructions/java-style.md`, and `.opencode/instructions/testing.md`. Inspect the diff and enough surrounding code to verify each claim. Load domain-specific skills only when applicable.

Report only actionable findings. Order them by severity and provide path, line, impact, and a concrete remediation. Focus on defects, data loss, authorization, contract compatibility, transactions, error handling, and test gaps. If there are no findings, say so and name any residual testing limitations.
