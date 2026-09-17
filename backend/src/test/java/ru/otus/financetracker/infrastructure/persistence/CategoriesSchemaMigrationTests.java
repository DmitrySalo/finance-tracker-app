package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.domain.categories.Category;
import ru.otus.financetracker.domain.categories.TransactionType;

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
class CategoriesSchemaMigrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldCreateCategoriesTableWithRequiredConstraintsAndIndex() {
        var userId = insertUser();

        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'categories'",
                String.class
        )).containsExactlyInAnyOrder(
                "id",
                "user_id",
                "name",
                "transaction_type",
                "icon",
                "color",
                "created_at",
                "updated_at",
                "version"
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'categories' "
                        + "AND indexname = 'idx_categories_user_id')",
                Boolean.class
        )).isTrue();
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        userId,
                        "Food",
                        "OTHER",
                        "utensils",
                        "#0A1B2C"
                )
        ).hasStackTraceContaining("chk_categories_transaction_type");
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        userId,
                        "Food",
                        "EXPENSE",
                        "utensils",
                        "blue"
                )
        ).hasStackTraceContaining("chk_categories_color_format");
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        userId,
                        "   ",
                        "EXPENSE",
                        "utensils",
                        "#0A1B2C"
                )
        ).hasStackTraceContaining("chk_categories_name_not_blank");
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        userId,
                        "Food",
                        "EXPENSE",
                        "   ",
                        "#0A1B2C"
                )
        ).hasStackTraceContaining("chk_categories_icon_not_blank");
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Food",
                        "EXPENSE",
                        "utensils",
                        "#0A1B2C"
                )
        ).hasStackTraceContaining("fk_categories_user");
    }

    @Test
    void shouldEnforceCategoryNameUniquenessWithinUserAndTransactionType() {
        var userId = insertUser();

        insertCategory(userId, "Food", "EXPENSE");
        insertCategory(userId, "Food", "INCOME");

        assertThatThrownBy(() -> insertCategory(userId, "Food", "EXPENSE"))
                .hasStackTraceContaining("uq_categories_user_transaction_type_name");
    }

    @Test
    void shouldReturnOnlyCategoriesOwnedByRequestedUser() {
        var firstUserId = insertUser();
        var secondUserId = insertUser();
        var firstCategory = category(firstUserId, "Food");
        var secondCategory = category(secondUserId, "Salary");

        categoryRepository.save(firstCategory);
        categoryRepository.save(secondCategory);

        var categories = categoryRepository.findAllByUserId(
                firstUserId,
                PageRequest.of(0, 20, Sort.by("name").ascending())
        );

        assertThat(categories.getContent()).containsExactly(firstCategory);
        assertThat(categoryRepository.findByIdAndUserId(secondCategory.id(), firstUserId)).isEmpty();
    }

    @Test
    void shouldRejectSavingCategoryWithStaleVersion() {
        var userId = insertUser();
        var category = category(userId, "Food");

        categoryRepository.save(category);
        var updatedCategory = new Category(
                category.id(),
                userId,
                "Groceries",
                TransactionType.EXPENSE,
                "cart",
                "#112233",
                category.createdAt(),
                category.updatedAt(),
                category.version()
        );
        categoryRepository.save(updatedCategory);

        assertThatThrownBy(() -> categoryRepository.save(category))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private UUID insertUser() {
        var userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                userId,
                userId + "@example.test",
                "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                "Test user",
                "USD"
        );
        return userId;
    }

    private void insertCategory(UUID userId, String name, String transactionType) {
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, transaction_type, icon, color) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                name,
                transactionType,
                "utensils",
                "#0A1B2C"
        );
    }

    private Category category(UUID userId, String name) {
        var now = Instant.parse("2026-09-16T00:00:00Z");
        return new Category(
                UUID.randomUUID(),
                userId,
                name,
                TransactionType.EXPENSE,
                "utensils",
                "#0A1B2C",
                now,
                now,
                0
        );
    }
}
