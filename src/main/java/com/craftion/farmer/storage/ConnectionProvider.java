package com.craftion.farmer.storage;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider extends AutoCloseable {

    DatabaseType type();

    void initialize() throws SQLException;

    Connection getConnection() throws SQLException;

    @Override
    void close() throws SQLException;
}
