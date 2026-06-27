package com.craftion.farmer.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class MySQLConnectionProvider implements ConnectionProvider {

    private final DatabaseSettings settings;
    private HikariDataSource dataSource;

    public MySQLConnectionProvider(DatabaseSettings settings) {
        this.settings = settings;
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.MYSQL;
    }

    @Override
    public void initialize() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("CraftionFarmer-MySQL");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(jdbcUrl());
        config.setUsername(this.settings.mysqlUsername());
        config.setPassword(this.settings.mysqlPassword());
        config.setMaximumPoolSize(this.settings.mysqlPoolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (this.dataSource == null) {
            throw new SQLException("MySQL connection pool henuz hazir degil.");
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

    private String jdbcUrl() {
        return "jdbc:mysql://" + this.settings.mysqlHost() + ":" + this.settings.mysqlPort() + "/" + this.settings.mysqlDatabase()
            + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC";
    }
}
