package ru.otus.financetracker.api.transactions.imports;

import java.util.List;

public record TransactionImportPreviewResponse(
        List<Row> rows,
        List<LineError> lineErrors
) {

    public static TransactionImportPreviewResponse from(
            ru.otus.financetracker.application.transactions.imports.TransactionImportPreview preview) {
        return new TransactionImportPreviewResponse(
                preview.rows().stream()
                        .map(row -> new Row(
                                row.lineNumber(),
                                row.categoryId(),
                                row.amount(),
                                row.currency(),
                                row.exchangeRateToBase(),
                                row.transactionDate(),
                                row.description(),
                                row.transactionType()
                        ))
                        .toList(),
                preview.lineErrors().stream()
                        .map(error -> new LineError(
                                error.lineNumber(),
                                error.field(),
                                error.code(),
                                error.message()
                        ))
                        .toList()
        );
    }

    public record Row(
            int lineNumber,
            String categoryId,
            String amount,
            String currency,
            String exchangeRateToBase,
            String transactionDate,
            String description,
            String transactionType
    ) {
    }

    public record LineError(
            int lineNumber,
            String field,
            String code,
            String message
    ) {
    }
}
