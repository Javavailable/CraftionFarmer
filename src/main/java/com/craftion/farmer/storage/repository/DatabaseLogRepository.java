package com.craftion.farmer.storage.repository;

import com.craftion.farmer.storage.DatabaseManager;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DatabaseLogRepository implements LogRepository {

    private final DatabaseManager databaseManager;

    public DatabaseLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public CompletableFuture<Void> append(String farmerId, UUID actorUuid, String action, String detail) {
        String normalizedFarmerId = requireNonBlank(farmerId, "farmerId");
        String normalizedAction = action == null || action.isBlank() ? "UNKNOWN" : action.trim();
        String normalizedDetail = detail == null ? "" : detail.trim();
        long createdAt = Instant.now().toEpochMilli();

        return this.databaseManager.executeUpdate(
            "INSERT INTO farmer_logs (farmer_id, actor_uuid, action, detail, created_at) VALUES (?, ?, ?, ?, ?)",
            statement -> {
                statement.setString(1, normalizedFarmerId);
                if (actorUuid == null) {
                    statement.setNull(2, Types.VARCHAR);
                } else {
                    statement.setString(2, actorUuid.toString());
                }
                statement.setString(3, normalizedAction);
                statement.setString(4, normalizedDetail);
                statement.setLong(5, createdAt);
            }
        ).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<List<FarmerLogEntry>> recent(String farmerId, int limit) {
        String normalizedFarmerId = requireNonBlank(farmerId, "farmerId");
        int normalizedLimit = Math.max(1, Math.min(50, limit));

        return this.databaseManager.query(
            "SELECT id, farmer_id, actor_uuid, action, detail, created_at FROM farmer_logs WHERE farmer_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
            statement -> {
                statement.setString(1, normalizedFarmerId);
                statement.setInt(2, normalizedLimit);
            },
            resultSet -> readEntries(resultSet)
        );
    }

    private List<FarmerLogEntry> readEntries(ResultSet resultSet) throws java.sql.SQLException {
        List<FarmerLogEntry> entries = new ArrayList<>();
        while (resultSet.next()) {
            entries.add(new FarmerLogEntry(
                resultSet.getLong("id"),
                resultSet.getString("farmer_id"),
                actorUuid(resultSet.getString("actor_uuid")),
                resultSet.getString("action"),
                resultSet.getString("detail"),
                resultSet.getLong("created_at")
            ));
        }
        return List.copyOf(entries);
    }

    private UUID actorUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}
