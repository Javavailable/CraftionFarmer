package com.craftion.farmer.storage.repository;

import java.util.UUID;

public record FarmerLogEntry(
    long id,
    String farmerId,
    UUID actorUuid,
    String action,
    String detail,
    long createdAt
) {

    public FarmerLogEntry {
        farmerId = farmerId == null ? "" : farmerId.trim();
        action = action == null || action.isBlank() ? "UNKNOWN" : action.trim();
        detail = detail == null ? "" : detail.trim();
        createdAt = Math.max(0L, createdAt);
    }
}
