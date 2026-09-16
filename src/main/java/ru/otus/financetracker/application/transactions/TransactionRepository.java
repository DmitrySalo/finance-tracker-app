package ru.otus.financetracker.application.transactions;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.otus.financetracker.domain.transactions.Transaction;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(UUID transactionId, UUID userId);

    Page<Transaction> findAllByUserId(UUID userId, TransactionFilter filter, Pageable pageable);
}
