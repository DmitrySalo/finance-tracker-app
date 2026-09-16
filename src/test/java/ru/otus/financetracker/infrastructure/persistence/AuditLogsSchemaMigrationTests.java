package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class AuditLogsSchemaMigrationTests {

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
    void shouldCreateAuditLogsWithJsonStatesAndRequiredIndexes() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'audit_logs'",
                String.class
        )).containsExactlyInAnyOrder(
                "id", "actor_user_id", "entity_type", "entity_id", "action", "occurred_at", "before_state", "after_state"
        );
        assertThat(columnType("before_state")).isEqualTo("jsonb");
        assertThat(columnType("after_state")).isEqualTo("jsonb");
        assertThat(indexDefinition("idx_audit_logs_actor_user_occurred_at"))
                .contains("actor_user_id, occurred_at DESC");
        assertThat(indexDefinition("idx_audit_logs_entity_type_entity_id_occurred_at"))
                .contains("entity_type, entity_id, occurred_at DESC");
    }

    @Test
    void shouldRejectAuditLogUpdatesAndDeletes() {
        UUID actorUserId = UUID.randomUUID();
        UUID auditLogId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                actorUserId, actorUserId + "@example.test", "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                "Test user", "USD"
        );
        jdbcTemplate.update(
                "INSERT INTO audit_logs (id, actor_user_id, entity_type, entity_id, action, occurred_at, after_state) "
                        + "VALUES (?, ?, 'TRANSACTION', ?, 'CREATE', CURRENT_TIMESTAMP, '{\"amount\": \"1.0000\"}')",
                auditLogId, actorUserId, UUID.randomUUID()
        );

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_logs SET action = 'DELETE' WHERE id = ?", auditLogId))
                .hasStackTraceContaining("Audit logs are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", auditLogId))
                .hasStackTraceContaining("Audit logs are immutable");
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE TABLE audit_logs"))
                .hasStackTraceContaining("Audit logs are immutable");
    }

    private String columnType(String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'audit_logs' AND column_name = ?",
                String.class,
                columnName
        );
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'audit_logs' AND indexname = ?",
                String.class,
                indexName
        );
    }
}
