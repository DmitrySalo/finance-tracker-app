---
name: secure-development
description: Use when work touches authentication, authorization, secrets, untrusted input, files, outbound URLs, dependencies, logging, or sensitive data.
---

# Secure Development

Read `.opencode/instructions/security.md`. Never expose or add secrets, production data, credentials, or sensitive values to source, logs, test fixtures, URLs, or responses. Treat external input as untrusted and use allowlists, bounds, parameterized queries, and safe output encoding.

Enforce authentication and resource-level authorization on the server. Do not weaken security controls, access restrictions, TLS verification, CORS, CSRF, or rate limiting without explicit confirmation after describing the risk. Add negative tests for meaningful security boundaries.
