package ru.otus.financetracker.application.transactions.imports;

import java.util.List;

public record TransactionImportPreview(List<Row> rows, List<LineError> lineErrors) {

    public record Row(int lineNumber, String categoryId, String amount, String currency, String exchangeRateToBase,
                      String transactionDate, String description, String transactionType) {
    }

    public record LineError(int lineNumber, String field, String code, String message) {
    }
}
