package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

class SyntheticDemoDataMigrationTests extends PostgresIntegrationTestSupport {

    @Test
    @DisplayName("Демонстрационная миграция создает требуемые синтетические данные")
    void shouldCreateRequiredSyntheticDemoData() throws SQLException {
        String schema = "demo_data_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
                .dataSource(postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .cleanDisabled(false)
                .locations("classpath:db/migration", "classpath:db/demo-migration")
                .load();

        try {
            flyway.migrate();
            try (var connection = DriverManager.getConnection(
                    postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword()
            ); var statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + schema);

                assertThat(count(statement, "SELECT count(*) FROM users")).isEqualTo(2);
                assertThat(count(statement, "SELECT count(*) FROM categories")).isEqualTo(18);
                assertThat(count(statement, """
                        SELECT count(*)
                        FROM (
                            SELECT user_id
                            FROM categories
                            WHERE user_id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002')
                            GROUP BY user_id
                            HAVING count(*) = 9
                               AND count(*) FILTER (WHERE transaction_type = 'INCOME') = 3
                               AND count(*) FILTER (WHERE transaction_type = 'EXPENSE') = 6
                        ) AS demo_user_categories
                        """)).isEqualTo(2);
                assertThat(count(statement, "SELECT count(*) FROM budgets")).isEqualTo(3);
                assertThat(count(statement, "SELECT count(*) FROM transactions")).isGreaterThanOrEqualTo(200);
                assertThat(count(statement, "SELECT count(DISTINCT date_trunc('month', transaction_date)) FROM transactions"))
                        .isEqualTo(6);
                assertThat(count(statement, """
                        SELECT count(*)
                        FROM (
                            SELECT user_id
                            FROM (
                                SELECT user_id,
                                       month,
                                       string_agg(category_id::text || ':' || total_amount::text, ',' ORDER BY category_id) AS composition
                                FROM (
                                    SELECT user_id, date_trunc('month', transaction_date) AS month, category_id, sum(amount) AS total_amount
                                    FROM transactions
                                    WHERE transaction_type = 'EXPENSE'
                                    GROUP BY user_id, date_trunc('month', transaction_date), category_id
                                ) AS category_totals
                                GROUP BY user_id, month
                            ) AS monthly_compositions
                            GROUP BY user_id
                            HAVING count(*) = 6
                               AND count(DISTINCT composition) = 6
                        ) AS varied_demo_compositions
                        """)).isEqualTo(2);
                assertThat(count(statement, """
                        SELECT count(*)
                        FROM transactions
                        WHERE user_id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002')
                          AND description LIKE 'Synthetic demo transaction %'
                          AND updated_at = TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00'
                        """)).isEqualTo(216);
                assertThat(demoAccountPasswordMatches(statement, "alex.demo@example.test")).isTrue();
                assertThat(demoAccountPasswordMatches(statement, "sam.demo@example.test")).isTrue();
            }
        } finally {
            flyway.clean();
        }
    }

    private int count(java.sql.Statement statement, String query) throws SQLException {
        try (var resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private boolean demoAccountPasswordMatches(java.sql.Statement statement, String email) throws SQLException {
        try (var resultSet = statement.executeQuery(
                "SELECT password_hash FROM users WHERE email = '" + email + "'"
        )) {
            resultSet.next();
            return new BCryptPasswordEncoder().matches("DemoPassword2026", resultSet.getString(1));
        }
    }
}
