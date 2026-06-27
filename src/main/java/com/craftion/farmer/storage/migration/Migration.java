package com.craftion.farmer.storage.migration;

import com.craftion.farmer.storage.DatabaseType;
import java.sql.Connection;
import java.sql.SQLException;

public interface Migration {

    int version();

    String name();

    void migrate(Connection connection, DatabaseType databaseType) throws SQLException;
}
