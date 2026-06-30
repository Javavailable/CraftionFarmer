package com.craftion.farmer.storage.migration;

import com.craftion.farmer.storage.DatabaseType;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V3FarmerXpSchema implements Migration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String name() {
        return "farmer_xp_schema";
    }

    @Override
    public void migrate(Connection connection, DatabaseType databaseType) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE farmers ADD COLUMN xp_buffer BIGINT NOT NULL DEFAULT 0");
        }
    }
}
