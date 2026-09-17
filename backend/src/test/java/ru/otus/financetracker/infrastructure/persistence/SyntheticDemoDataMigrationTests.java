package ru.otus.financetracker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
                assertThat(count(statement, "SELECT count(*) FROM categories")).isEqualTo(12);
                assertThat(count(statement, "SELECT count(*) FROM budgets")).isEqualTo(3);
                assertThat(count(statement, "SELECT count(*) FROM transactions")).isGreaterThanOrEqualTo(200);
                assertThat(count(statement, "SELECT count(DISTINCT date_trunc('month', transaction_date)) FROM transactions"))
                        .isEqualTo(6);
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
}
