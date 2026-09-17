package ru.otus.financetracker.application.recurring;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.application.audit.TransactionAuditService;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.application.transactions.TransactionRepository;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.domain.recurring.RecurringTransaction;
import ru.otus.financetracker.domain.transactions.Transaction;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

@Service
public class RecurringTransactionService {

    private static final int MAX_OCCURRENCES_PER_RULE_PER_RUN = 12;

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionOccurrenceRepository occurrenceRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionAuditService transactionAuditService;
    private final Clock clock;

    public RecurringTransactionService(
            RecurringTransactionRepository recurringTransactionRepository,
            RecurringTransactionOccurrenceRepository occurrenceRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            TransactionAuditService transactionAuditService,
            Clock clock
    ) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.transactionAuditService = transactionAuditService;
        this.clock = clock;
    }

    @Transactional
    public RecurringTransaction create(UUID userId, CreateRecurringTransactionCommand command) {
        requireMatchingCategory(userId, command.categoryId(), command.transactionType());
        Instant now = clock.instant();
        return recurringTransactionRepository.save(new RecurringTransaction(
                UUID.randomUUID(),
                userId,
                command.categoryId(),
                command.amount(),
                command.currency(),
                command.exchangeRateToBase(),
                strip(command.description()),
                command.transactionType(),
                command.dayOfMonth(),
                command.startDate(),
                firstOccurrence(command.startDate(), command.dayOfMonth()),
                command.active(),
                now,
                now,
                0
        ));
    }

    @Transactional(readOnly = true)
    public RecurringTransaction get(UUID userId, UUID id) {
        return findOwned(userId, id);
    }

    @Transactional(readOnly = true)
    public Page<RecurringTransaction> list(UUID userId, Pageable pageable) {
        return recurringTransactionRepository.findAllByUserId(userId, pageable);
    }

    @Transactional
    public RecurringTransaction update(UUID userId, UUID id, UpdateRecurringTransactionCommand command) {
        var current = findOwned(userId, id);
        if (current.version() != command.version()) {
            throw new OptimisticLockingFailureException("Recurring transaction has been modified.");
        }
        var categoryId = command.categoryId() == null ? current.categoryId() : command.categoryId();
        var transactionType = command.transactionType() == null ? current.transactionType() : command.transactionType();
        requireMatchingCategory(userId, categoryId, transactionType);
        int dayOfMonth = command.dayOfMonth() == null ? current.dayOfMonth() : command.dayOfMonth();
        LocalDate startDate = command.startDate() == null ? current.startDate() : command.startDate();
        boolean active = command.active() == null ? current.active() : command.active();
        LocalDate nextOccurrenceDate = command.startDate() != null || command.dayOfMonth() != null
                ? firstOccurrence(max(startDate, current.nextOccurrenceDate()), dayOfMonth)
                : current.nextOccurrenceDate();
        return recurringTransactionRepository.save(new RecurringTransaction(
                current.id(),
                current.userId(),
                categoryId,
                command.amount() == null ? current.amount() : command.amount(),
                command.currency() == null ? current.currency() : command.currency(),
                command.exchangeRateToBase() == null
                        ? current.exchangeRateToBase()
                        : command.exchangeRateToBase(),
                command.description() == null ? current.description() : strip(command.description()),
                transactionType,
                dayOfMonth,
                startDate,
                nextOccurrenceDate,
                active,
                current.createdAt(),
                clock.instant(),
                current.version()
        ));
    }

    @Transactional
    public void delete(UUID userId, UUID id, long version) {
        var current = findOwned(userId, id);
        if (current.version() != version) {
            throw new OptimisticLockingFailureException("Recurring transaction has been modified.");
        }
        recurringTransactionRepository.delete(current);
    }

    @Transactional
    public void createDueOccurrences(LocalDate businessDate) {
        recurringTransactionRepository.findActiveDueOnOrBefore(businessDate)
                .forEach(rule -> createDueOccurrences(rule, businessDate));
    }

    private void createDueOccurrences(RecurringTransaction rule, LocalDate businessDate) {
        RecurringTransaction updated = rule;
        int createdOccurrences = 0;
        while (createdOccurrences < MAX_OCCURRENCES_PER_RULE_PER_RUN
                && !updated.nextOccurrenceDate().isAfter(businessDate)) {
            Instant now = clock.instant();
            UUID transactionId = UUID.randomUUID();
            Transaction transaction = transactionRepository.save(new Transaction(
                    transactionId,
                    updated.userId(),
                    updated.categoryId(),
                    updated.amount(),
                    updated.currency(),
                    updated.exchangeRateToBase(),
                    updated.nextOccurrenceDate(),
                    updated.description(),
                    updated.transactionType(),
                    updated.id(),
                    now,
                    now,
                    0
            ));
            transactionAuditService.recordCreate(updated.userId(), transaction);
            occurrenceRepository.save(updated.id(), updated.nextOccurrenceDate(), transactionId, now);
            updated = new RecurringTransaction(
                    updated.id(),
                    updated.userId(),
                    updated.categoryId(),
                    updated.amount(),
                    updated.currency(),
                    updated.exchangeRateToBase(),
                    updated.description(),
                    updated.transactionType(),
                    updated.dayOfMonth(),
                    updated.startDate(),
                    nextOccurrence(updated.nextOccurrenceDate(), updated.dayOfMonth()),
                    updated.active(),
                    updated.createdAt(),
                    now,
                    updated.version()
            );
            updated = recurringTransactionRepository.save(updated);
            createdOccurrences++;
        }
    }

    private void requireMatchingCategory(UUID userId, UUID categoryId, TransactionType transactionType) {
        var category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (category.transactionType() != transactionType) {
            throw new RecurringTransactionCategoryTypeMismatchException();
        }
    }

    private RecurringTransaction findOwned(UUID userId, UUID id) {
        return recurringTransactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private LocalDate firstOccurrence(LocalDate startDate, int dayOfMonth) {
        LocalDate candidate = occurrenceInMonth(YearMonth.from(startDate), dayOfMonth);
        return candidate.isBefore(startDate)
                ? occurrenceInMonth(YearMonth.from(startDate).plusMonths(1), dayOfMonth)
                : candidate;
    }

    private LocalDate nextOccurrence(LocalDate occurrenceDate, int dayOfMonth) {
        return occurrenceInMonth(YearMonth.from(occurrenceDate).plusMonths(1), dayOfMonth);
    }

    private LocalDate occurrenceInMonth(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private String strip(String description) {
        return description == null ? null : description.strip();
    }
}
