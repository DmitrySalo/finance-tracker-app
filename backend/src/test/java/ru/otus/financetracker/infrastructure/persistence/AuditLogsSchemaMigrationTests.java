package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

class AuditLogsSchemaMigrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Миграция журнала аудита создает JSON-состояния и обязательные индексы")
    void shouldCreateAuditLogsWithJsonStatesAndRequiredIndexes() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'audit_logs'",
                String.class
        )).containsExactlyInAnyOrder(
                "id",
                "actor_user_id",
                "entity_type",
                "entity_id",
                "action",
                "occurred_at",
                "before_state",
                "after_state"
        );
        assertThat(columnType("before_state")).isEqualTo("jsonb");
        assertThat(columnType("after_state")).isEqualTo("jsonb");
        assertThat(indexDefinition("idx_audit_logs_actor_user_occurred_at"))
                .contains("actor_user_id, occurred_at DESC");
        assertThat(indexDefinition("idx_audit_logs_entity_type_entity_id_occurred_at"))
                .contains("entity_type, entity_id, occurred_at DESC");
    }

    @Test
    @DisplayName("Журнал аудита запрещает изменение и удаление записей")
    void shouldRejectAuditLogUpdatesAndDeletes() {
        UUID actorUserId = UUID.randomUUID();
        UUID auditLogId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, base_currency) VALUES (?, ?, ?, ?, ?)",
                actorUserId,
                actorUserId + "@example.test",
                "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghijklmn",
                "Test user",
                "USD"
        );
        jdbcTemplate.update(
                "INSERT INTO audit_logs (id, actor_user_id, entity_type, entity_id, action, occurred_at, after_state) "
                        + "VALUES (?, ?, 'TRANSACTION', ?, 'CREATE', CURRENT_TIMESTAMP, '{\"amount\": \"1.0000\"}')",
                auditLogId, actorUserId, UUID.randomUUID()
        );

        assertThatThrownBy(
                () -> jdbcTemplate.update("UPDATE audit_logs SET action = 'DELETE' WHERE id = ?", auditLogId)
        )
                .hasStackTraceContaining("Audit logs are immutable");
        assertThatThrownBy(
                () -> jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", auditLogId)
        )
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
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'audit_logs' "
                        + "AND indexname = ?",
                String.class,
                indexName
        );
    }
}
