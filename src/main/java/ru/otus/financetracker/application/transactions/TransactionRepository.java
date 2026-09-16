package ru.otus.financetracker.application.transactions;

import java.util.Optional;
import java.util.UUID;

import ru.otus.financetracker.domain.transactions.Transaction;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(UUID transactionId, UUID userId);
}
