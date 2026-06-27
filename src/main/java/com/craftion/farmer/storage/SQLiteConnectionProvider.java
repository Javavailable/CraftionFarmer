package com.craftion.farmer.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

public final class SQLiteConnectionProvider implements ConnectionProvider {

    private final Path dataDirectory;
    private final String fileName;
    private HikariDataSource dataSource;

    public SQLiteConnectionProvider(Path dataDirectory, String fileName) {
        this.dataDirectory = dataDirectory;
        this.fileName = fileName;
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.SQLITE;
    }

    @Override
    public void initialize() throws SQLException {
        try {
            Files.createDirectories(this.dataDirectory);
        } catch (Exception exception) {
            throw new SQLException("SQLite data klasoru olusturulamadi.", exception);
        }

        Path databaseFile = this.dataDirectory.resolve(this.fileName).normalize();
        if (!databaseFile.startsWith(this.dataDirectory.normalize())) {
            throw new SQLException("SQLite dosyasi plugin klasoru disinda olamaz: " + this.fileName);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("CraftionFarmer-SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");
        config.addDataSourceProperty("busy_timeout", "5000");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (this.dataSource == null) {
            throw new SQLException("SQLite connection pool henuz hazir degil.");
        }
        return this.dataSource.getConnection();
    }

    @Override
    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
            this.dataSource = null;
        }
    }
}
