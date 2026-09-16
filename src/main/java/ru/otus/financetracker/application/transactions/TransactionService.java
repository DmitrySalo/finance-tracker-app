package ru.otus.financetracker.application.transactions;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.domain.transactions.Transaction;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional
    public Transaction create(UUID userId, CreateTransactionCommand command) {
        var category = categoryRepository.findByIdAndUserId(command.categoryId(), userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (category.transactionType() != command.transactionType()) {
            throw new TransactionCategoryTypeMismatchException();
        }

        Instant now = clock.instant();
        return transactionRepository.save(new Transaction(
                UUID.randomUUID(), userId, category.id(), command.amount(), command.currency(), command.exchangeRateToBase(),
                command.transactionDate(), command.description() == null ? null : command.description().strip(),
                command.transactionType(), now, now, 0
        ));
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> list(UUID userId, TransactionFilter filter, Pageable pageable) {
        validateFilter(filter);
        if (filter.categoryId() != null) {
            categoryRepository.findByIdAndUserId(filter.categoryId(), userId)
                    .orElseThrow(ResourceNotFoundException::new);
        }
        return transactionRepository.findAllByUserId(userId, filter, pageable);
    }

    private void validateFilter(TransactionFilter filter) {
        if (filter.fromDate() != null && filter.toDate() != null && filter.fromDate().isAfter(filter.toDate())) {
            throw new InvalidTransactionFilterException();
        }
        if (filter.minAmount() != null && filter.maxAmount() != null && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw new InvalidTransactionFilterException();
        }
    }
}
