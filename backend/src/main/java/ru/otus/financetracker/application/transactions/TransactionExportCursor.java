package ru.otus.financetracker.application.transactions;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionExportCursor(LocalDate transactionDate, UUID transactionId) {
}
