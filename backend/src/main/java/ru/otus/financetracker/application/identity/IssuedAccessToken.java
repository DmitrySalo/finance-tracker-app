package ru.otus.financetracker.application.identity;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}
