package ru.otus.financetracker.application.identity;

public record RegisterUserCommand(
        String email,
        String password,
        String displayName,
        String baseCurrency
) {
}
