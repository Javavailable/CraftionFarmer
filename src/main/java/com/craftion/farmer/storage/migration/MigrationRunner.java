package com.craftion.farmer.storage.migration;

import com.craftion.farmer.storage.ConnectionProvider;
import com.craftion.farmer.storage.DatabaseType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class MigrationRunner {

    private static final String MIGRATION_TABLE = "craftionfarmer_migrations";

    private final List<Migration> migrations;

    public MigrationRunner(List<Migration> migrations) {
        this.migrations = migrations.stream()
            .sorted(Comparator.comparingInt(Migration::version))
            .toList();
    }

    public void run(ConnectionProvider provider, DatabaseType databaseType) throws SQLException {
        try (Connection connection = provider.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                ensureMigrationTable(connection, databaseType);
                for (Migration migration : this.migrations) {
                    if (!isApplied(connection, migration.version())) {
                        migration.migrate(connection, databaseType);
                        markApplied(connection, migration);
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void ensureMigrationTable(Connection connection, DatabaseType databaseType) throws SQLException {
        String nameType = databaseType == DatabaseType.MYSQL ? "VARCHAR(128)" : "TEXT";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " ("
                + "version INTEGER PRIMARY KEY, "
                + "name " + nameType + " NOT NULL, "
                + "applied_at BIGINT NOT NULL"
                + ")");
        }
    }

    private boolean isApplied(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + MIGRATION_TABLE + " WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void markApplied(Connection connection, Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + MIGRATION_TABLE + " (version, name, applied_at) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.name());
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }
}
