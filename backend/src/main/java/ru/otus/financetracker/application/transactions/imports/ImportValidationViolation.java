package ru.otus.financetracker.application.transactions.imports;

public record ImportValidationViolation(String field, String code, String message) {
}
