package ru.otus.financetracker.application.transactions;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.application.audit.TransactionAuditService;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.domain.transactions.Transaction;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionAuditService transactionAuditService;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository,
                               TransactionAuditService transactionAuditService, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionAuditService = transactionAuditService;
        this.clock = clock;
    }

    @Transactional
    public Transaction create(UUID userId, CreateTransactionCommand command) {
        return createValidated(userId, command);
    }

    @Transactional
    public List<Transaction> createAll(UUID userId, List<CreateTransactionCommand> commands) {
        for (CreateTransactionCommand command : commands) {
            validateCategory(userId, command);
        }
        return commands.stream().map(command -> createValidated(userId, command)).toList();
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID userId, UUID transactionId) {
        return findOwnedTransaction(userId, transactionId);
    }

    @Transactional
    public Transaction update(UUID userId, UUID transactionId, UpdateTransactionCommand command) {
        var transaction = findOwnedTransaction(userId, transactionId);
        if (transaction.version() != command.version()) {
            throw new OptimisticLockingFailureException("Transaction has been modified.");
        }

        var category = command.categoryId() == null
                ? categoryRepository.findByIdAndUserId(transaction.categoryId(), userId).orElseThrow(ResourceNotFoundException::new)
                : categoryRepository.findByIdAndUserId(command.categoryId(), userId).orElseThrow(ResourceNotFoundException::new);
        var transactionType = command.transactionType() == null ? transaction.transactionType() : command.transactionType();
        if (category.transactionType() != transactionType) {
            throw new TransactionCategoryTypeMismatchException();
        }

        var updatedTransaction = transactionRepository.save(new Transaction(
                transaction.id(), transaction.userId(), category.id(),
                command.amount() == null ? transaction.amount() : command.amount(),
                command.currency() == null ? transaction.currency() : command.currency(),
                command.exchangeRateToBase() == null ? transaction.exchangeRateToBase() : command.exchangeRateToBase(),
                command.transactionDate() == null ? transaction.transactionDate() : command.transactionDate(),
                command.description() == null ? transaction.description() : command.description().strip(), transactionType, transaction.recurringTransactionId(),
                transaction.createdAt(), clock.instant(), transaction.version()
        ));
        transactionAuditService.recordUpdate(userId, transaction, updatedTransaction);
        return updatedTransaction;
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId, long version) {
        var transaction = findOwnedTransaction(userId, transactionId);
        if (transaction.version() != version) {
            throw new OptimisticLockingFailureException("Transaction has been modified.");
        }
        transactionAuditService.recordDelete(userId, transaction);
        transactionRepository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> list(UUID userId, TransactionFilter filter, Pageable pageable) {
        validateAndAuthorizeFilter(userId, filter);
        return transactionRepository.findAllByUserId(userId, filter, pageable);
    }

    @Transactional(readOnly = true)
    public void validateExport(UUID userId, TransactionFilter filter) {
        validateAndAuthorizeFilter(userId, filter);
    }

    @Transactional(readOnly = true)
    public List<Transaction> exportChunk(UUID userId, TransactionFilter filter, TransactionExportCursor cursor, int chunkSize) {
        return transactionRepository.findExportChunkByUserId(userId, filter, cursor, chunkSize);
    }

    private void validateAndAuthorizeFilter(UUID userId, TransactionFilter filter) {
        validateFilter(filter);
        if (filter.categoryId() != null) {
            categoryRepository.findByIdAndUserId(filter.categoryId(), userId)
                    .orElseThrow(ResourceNotFoundException::new);
        }
    }

    private void validateFilter(TransactionFilter filter) {
        if (filter.fromDate() != null && filter.toDate() != null && filter.fromDate().isAfter(filter.toDate())) {
            throw new InvalidTransactionFilterException();
        }
        if (filter.minAmount() != null && filter.maxAmount() != null && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw new InvalidTransactionFilterException();
        }
    }

    private Transaction findOwnedTransaction(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private Transaction createValidated(UUID userId, CreateTransactionCommand command) {
        validateCategory(userId, command);
        Instant now = clock.instant();
        var transaction = transactionRepository.save(new Transaction(
                UUID.randomUUID(), userId, command.categoryId(), command.amount(), command.currency(), command.exchangeRateToBase(),
                command.transactionDate(), command.description() == null ? null : command.description().strip(),
                command.transactionType(), null, now, now, 0
        ));
        transactionAuditService.recordCreate(userId, transaction);
        return transaction;
    }

    private void validateCategory(UUID userId, CreateTransactionCommand command) {
        var category = categoryRepository.findByIdAndUserId(command.categoryId(), userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (category.transactionType() != command.transactionType()) {
            throw new TransactionCategoryTypeMismatchException();
        }
    }
}
