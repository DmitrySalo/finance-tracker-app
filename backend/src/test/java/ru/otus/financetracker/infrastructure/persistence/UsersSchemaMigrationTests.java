package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

class UsersSchemaMigrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Миграция создает таблицу пользователей с обязательными колонками")
    void shouldCreateUsersTableWithRequiredColumns() {
        var columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'users'",
                String.class
        );

        assertThat(columns).containsExactlyInAnyOrder(
                "id",
                "email",
                "password_hash",
                "display_name",
                "base_currency",
                "created_at",
                "updated_at",
                "version"
        );

        var nullability = jdbcTemplate.query(
                "SELECT column_name, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'users'",
                (resultSet, rowNum) -> Map.entry(
                        resultSet.getString("column_name"),
                        resultSet.getString("is_nullable")
                )
        );

        assertThat(nullability).containsOnly(
                Map.entry("id", "NO"),
                Map.entry("email", "NO"),
                Map.entry("password_hash", "NO"),
                Map.entry("display_name", "NO"),
                Map.entry("base_currency", "NO"),
                Map.entry("created_at", "NO"),
                Map.entry("updated_at", "NO"),
                Map.entry("version", "NO")
        );
    }

    @Test
    @DisplayName("Таблица пользователей обеспечивает нормализованную уникальность email и формат валюты")
    void shouldEnforceNormalizedUniqueEmailAndBaseCurrency() {
        insertUser("person@example.test", "USD");

        assertThatThrownBy(() -> insertUser("PERSON@example.test", "USD"))
                .hasStackTraceContaining("chk_users_email_normalized");
        assertThatThrownBy(() -> insertUser("another@example.test", "usd"))
                .hasStackTraceContaining("chk_users_base_currency_format");
        assertThatThrownBy(() -> insertUser("person@example.test", "USD"))
                .hasStackTraceContaining("uq_users_email");
    }

    @Test
    @DisplayName("Таблица пользователей требует обязательные поля и заполняет аудит по умолчанию")
    void shouldRequireUserFieldsAndApplyAuditDefaults() {
        var userId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                userId,
                "defaults@example.test",
                "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                "Test user",
                "EUR"
        );

        var user = jdbcTemplate.queryForMap(
                "SELECT created_at, updated_at, version FROM users WHERE id = ?",
                userId
        );

        assertThat(user).containsEntry("version", 0L);
        assertThat(user.get("created_at")).isNotNull();
        assertThat(user.get("updated_at")).isNotNull();
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        "",
                        "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                        "Test user",
                        "USD"
                )
        ).hasStackTraceContaining("chk_users_email_not_blank");
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        "blank-name@example.test",
                        "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                        "   ",
                        "USD"
                )
        ).hasStackTraceContaining("chk_users_display_name_not_blank");
    }

    @Test
    @DisplayName("Изменение пользователя обновляет метку времени")
    void shouldUpdateTimestampWhenUserChanges() {
        var userId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                    userId,
                    "updated@example.test",
                    "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                    "Test user",
                    "USD"
            );
            jdbcTemplate.update("UPDATE users SET display_name = ? WHERE id = ?", "Updated user", userId);
        });

        var updatedAtChanged = jdbcTemplate.queryForObject(
                "SELECT updated_at > created_at FROM users WHERE id = ?",
                Boolean.class,
                userId
        );

        assertThat(updatedAtChanged).isTrue();
    }

    private void insertUser(String email, String baseCurrency) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                email,
                "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                "Test user",
                baseCurrency
        );
    }
}
