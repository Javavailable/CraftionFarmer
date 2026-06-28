package com.craftion.farmer.storage.repository;

import com.craftion.farmer.storage.DatabaseManager;
import java.sql.Types;
import java.time.Instant;
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

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}
