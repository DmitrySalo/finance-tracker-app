package ru.otus.financetracker.api.auth;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String email, String displayName, String baseCurrency) {
}
