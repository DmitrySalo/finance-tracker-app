package ru.otus.financetracker.api.transactions.imports;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

public record ImportColumnMappingRequest(@NotNull Map<String, String> columns) {
}
