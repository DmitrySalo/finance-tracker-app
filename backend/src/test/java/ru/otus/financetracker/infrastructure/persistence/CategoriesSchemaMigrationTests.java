package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.domain.categories.Category;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

class CategoriesSchemaMigrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("Миграция категорий создает обязательные ограничения и индекс")
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
    @DisplayName("Имя категории уникально для пользователя и типа операции")
    void shouldEnforceCategoryNameUniquenessWithinUserAndTransactionType() {
        var userId = insertUser();

        insertCategory(userId, "Food", "EXPENSE");
        insertCategory(userId, "Food", "INCOME");

        assertThatThrownBy(() -> insertCategory(userId, "Food", "EXPENSE"))
                .hasStackTraceContaining("uq_categories_user_transaction_type_name");
    }

    @Test
    @DisplayName("Репозиторий возвращает только категории запрошенного пользователя")
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
    @DisplayName("Сохранение категории с устаревшей версией отклоняется")
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
