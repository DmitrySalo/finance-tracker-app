package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
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
class UsersSchemaMigrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
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
