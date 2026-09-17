package ru.otus.financetracker.shared.web;

import java.util.List;

import ru.otus.financetracker.shared.ErrorCode;

public record ApiErrorResponse(
        ErrorCode code,
        String message,
        String traceId,
        List<Violation> violations
) {

    public record Violation(
            String field,
            String code,
            String message
    ) {
    }
}
