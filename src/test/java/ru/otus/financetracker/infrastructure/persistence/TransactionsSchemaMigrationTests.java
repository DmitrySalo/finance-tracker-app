package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "JWT_ISSUER=https://issuer.test",
        "JWT_AUDIENCE=finance-tracker-test",
        "JWT_SECRET=test-signing-secret-with-at-least-32-characters",
        "CORS_ALLOWED_ORIGINS=https://frontend.test",
        "MAX_REQUEST_SIZE=1MB",
        "MAX_CSV_FILE_SIZE=512KB",
        "MAX_CSV_ROWS=100",
        "MAX_REQUEST_HEADER_SIZE=8KB"
})
@Testcontainers
class TransactionsSchemaMigrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldCreateTransactionsTableWithRequiredIndexes() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'transactions'",
                String.class
        )).containsExactlyInAnyOrder(
                "id", "user_id", "category_id", "amount", "currency", "exchange_rate_to_base", "transaction_date",
                "description", "transaction_type", "recurring_transaction_id", "created_at", "updated_at", "version"
        );
        assertThat(indexDefinition("idx_transactions_user_transaction_date_id"))
                .contains("user_id, transaction_date DESC, id DESC");
        assertThat(indexDefinition("idx_transactions_user_category_transaction_date"))
                .contains("user_id, category_id, transaction_date DESC");
    }

    @Test
    void shouldRejectNonPositiveTransactionAmount() {
        var transactionReferences = insertTransactionReferences();

        assertThatThrownBy(() -> insertTransaction(transactionReferences, new BigDecimal("0.0000"), new BigDecimal("1.00000000")))
                .hasStackTraceContaining("chk_transactions_amount_positive");
    }

    @Test
    void shouldRejectNonPositiveExchangeRate() {
        var transactionReferences = insertTransactionReferences();

        assertThatThrownBy(() -> insertTransaction(transactionReferences, new BigDecimal("1.0000"), new BigDecimal("0.00000000")))
                .hasStackTraceContaining("chk_transactions_exchange_rate_to_base_positive");
    }

    @Test
    void shouldRejectInvalidCurrency() {
        var transactionReferences = insertTransactionReferences();

        assertThatThrownBy(() -> insertTransaction(transactionReferences, new BigDecimal("1.0000"),
                new BigDecimal("1.00000000"), "usd", "EXPENSE"))
                .hasStackTraceContaining("chk_transactions_currency_format");
    }

    @Test
    void shouldRejectInvalidTransactionType() {
        var transactionReferences = insertTransactionReferences();

        assertThatThrownBy(() -> insertTransaction(transactionReferences, new BigDecimal("1.0000"),
                new BigDecimal("1.00000000"), "USD", "OTHER"))
                .hasStackTraceContaining("chk_transactions_transaction_type");
    }

    @Test
    void shouldRejectTransactionWithUnknownUserOrCategory() {
        var transactionReferences = insertTransactionReferences();

        assertThatThrownBy(() -> insertTransaction(new TransactionReferences(UUID.randomUUID(), transactionReferences.categoryId()),
                new BigDecimal("1.0000"), new BigDecimal("1.00000000"))).hasStackTraceContaining("fk_transactions_user");
        assertThatThrownBy(() -> insertTransaction(new TransactionReferences(transactionReferences.userId(), UUID.randomUUID()),
                new BigDecimal("1.0000"), new BigDecimal("1.00000000")))
                .hasStackTraceContaining("fk_transactions_category_owner_and_type");
    }

    @Test
    void shouldRejectTransactionForAnotherUsersCategoryOrMismatchedCategoryType() {
        var transactionReferences = insertTransactionReferences();
        var otherUserId = insertUser();
        var incomeCategoryId = insertCategory(transactionReferences.userId(), "Salary", "INCOME");
        var otherUserCategoryId = insertCategory(otherUserId, "Transport", "EXPENSE");

        assertThatThrownBy(() -> insertTransaction(
                new TransactionReferences(transactionReferences.userId(), otherUserCategoryId),
                new BigDecimal("1.0000"), new BigDecimal("1.00000000")
        )).hasStackTraceContaining("fk_transactions_category_owner_and_type");
        assertThatThrownBy(() -> insertTransaction(
                new TransactionReferences(transactionReferences.userId(), incomeCategoryId),
                new BigDecimal("1.0000"), new BigDecimal("1.00000000")
        )).hasStackTraceContaining("fk_transactions_category_owner_and_type");
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'transactions' "
                        + "AND indexname = ?",
                String.class,
                indexName
        );
    }

    private TransactionReferences insertTransactionReferences() {
        var userId = UUID.randomUUID();
        insertUser(userId);
        var categoryId = insertCategory(userId, "Food", "EXPENSE");
        return new TransactionReferences(userId, categoryId);
    }

    private UUID insertUser() {
        var userId = UUID.randomUUID();
        insertUser(userId);
        return userId;
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                userId, userId + "@example.test", "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                "Test user", "USD"
        );
    }

    private UUID insertCategory(UUID userId, String name, String transactionType) {
        var categoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                categoryId, userId, name, transactionType, "utensils", "#0A1B2C"
        );
        return categoryId;
    }

    private void insertTransaction(TransactionReferences transactionReferences, BigDecimal amount, BigDecimal exchangeRateToBase) {
        insertTransaction(transactionReferences, amount, exchangeRateToBase, "USD", "EXPENSE");
    }

    private void insertTransaction(TransactionReferences transactionReferences, BigDecimal amount, BigDecimal exchangeRateToBase,
                                   String currency, String transactionType) {
        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, category_id, amount, currency, exchange_rate_to_base, "
                        + "transaction_date, transaction_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), transactionReferences.userId(), transactionReferences.categoryId(), amount, currency,
                exchangeRateToBase, LocalDate.of(2026, 9, 16), transactionType
        );
    }

    private record TransactionReferences(UUID userId, UUID categoryId) {
    }
}
