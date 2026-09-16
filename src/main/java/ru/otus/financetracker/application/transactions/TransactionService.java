package ru.otus.financetracker.application.transactions;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

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
}
