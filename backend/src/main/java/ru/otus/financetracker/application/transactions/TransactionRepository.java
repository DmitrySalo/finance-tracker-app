package ru.otus.financetracker.application.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.otus.financetracker.domain.transactions.Transaction;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(UUID transactionId, UUID userId);

    Page<Transaction> findAllByUserId(UUID userId, TransactionFilter filter, Pageable pageable);

    List<Transaction> findExportChunkByUserId(
            UUID userId,
            TransactionFilter filter,
            TransactionExportCursor cursor,
            int limit
    );

    BigDecimal sumExpenseAmountInBaseCurrency(
            UUID userId,
            UUID categoryId,
            LocalDate fromInclusive,
            LocalDate toExclusive
    );

    List<TransactionMonthlyExpenseTotal> sumExpenseAmountsInBaseCurrencyByMonth(
            UUID userId,
            List<UUID> categoryIds,
            LocalDate fromInclusive,
            LocalDate toExclusive
    );

    void delete(Transaction transaction);
}
