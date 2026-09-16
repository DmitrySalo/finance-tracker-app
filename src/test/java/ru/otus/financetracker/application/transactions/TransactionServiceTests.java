package ru.otus.financetracker.application.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.application.audit.TransactionAuditService;
import ru.otus.financetracker.domain.categories.Category;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.domain.transactions.Transaction;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

class TransactionServiceTests {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final TransactionAuditService transactionAuditService = mock(TransactionAuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    private final TransactionService service = new TransactionService(
            transactionRepository, categoryRepository, transactionAuditService, clock
    );

    @Test
    void shouldCreateTransactionWithFixedExchangeRateForOwnedMatchingCategory() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        var command = new CreateTransactionCommand(categoryId, new BigDecimal("12.3400"), "EUR",
                new BigDecimal("1.08500000"), LocalDate.of(2026, 9, 16), " Groceries ", TransactionType.EXPENSE);
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category(userId, categoryId)));
        when(transactionRepository.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var transaction = service.create(userId, command);

        assertThat(transaction.exchangeRateToBase()).isEqualByComparingTo("1.08500000");
        assertThat(transaction.description()).isEqualTo("Groceries");
        assertThat(transaction.createdAt()).isEqualTo(clock.instant());
        var transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().exchangeRateToBase()).isEqualByComparingTo("1.08500000");
    }

    @Test
    void shouldRejectTransactionWhenCategoryTypeDiffersFromTransactionType() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category(userId, categoryId)));

        assertThatThrownBy(() -> service.create(userId, command(categoryId, TransactionType.INCOME)))
                .isInstanceOf(TransactionCategoryTypeMismatchException.class);
    }

    @Test
    void shouldHideTransactionWhenItBelongsToAnotherUser() {
        UUID transactionId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(transactionRepository.findByIdAndUserId(transactionId, currentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(currentUserId, transactionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Category category(UUID userId, UUID categoryId) {
        Instant now = clock.instant();
        return new Category(categoryId, userId, "Food", TransactionType.EXPENSE, "utensils", "#0A1B2C", now, now, 0);
    }

    private CreateTransactionCommand command(UUID categoryId, TransactionType transactionType) {
        return new CreateTransactionCommand(categoryId, new BigDecimal("12.3400"), "EUR", new BigDecimal("1.08500000"),
                LocalDate.of(2026, 9, 16), "Groceries", transactionType);
    }
}
