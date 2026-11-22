package dev.agentworkshq.rag.jpa.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaMigrationTest {

    @TempDir
    Path hostDir;

    private String url;

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        hostMigration("V1__one.sql", "CREATE TABLE HOST_ONE (ID INT);");
        hostMigration("V2__two.sql", "CREATE TABLE HOST_TWO (ID INT);");
    }

    @Test
    void ourMigrationCoexistsWithHostMigrationsWithoutOutOfOrder() throws Exception {
        assertThat(flyway(hostLocation()).migrate().migrationsExecuted).isEqualTo(2);

        Flyway both = flyway(hostLocation(), "classpath:db/rag");
        assertThat(both.migrate().migrationsExecuted).isEqualTo(1);
        both.validate();

        hostMigration("V3__three.sql", "CREATE TABLE HOST_THREE (ID INT);");
        assertThat(both.migrate().migrationsExecuted).isEqualTo(1);
        both.validate();

        assertThat(count("RAG_SOURCE_DOCUMENT")).isZero();
        assertThat(count("RAG_INGESTION_JOB")).isZero();
        assertThat(count("HOST_THREE")).isZero();
    }

    @Test
    void ourMigrationIsIdempotent() throws Exception {
        Flyway both = flyway(hostLocation(), "classpath:db/rag");
        both.migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM \"flyway_schema_history\" WHERE \"version\" IS NULL");
        }

        assertThat(both.migrate().migrationsExecuted).isEqualTo(1);
        both.validate();
        assertThat(count("RAG_SOURCE_DOCUMENT")).isZero();
    }

    private Flyway flyway(String... locations) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(locations)
                .outOfOrder(false)
                .load();
    }

    private String hostLocation() {
        return "filesystem:" + hostDir.toAbsolutePath();
    }

    private void hostMigration(String name, String sql) throws Exception {
        Files.writeString(hostDir.resolve(name), sql);
    }

    private int count(String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }
}
