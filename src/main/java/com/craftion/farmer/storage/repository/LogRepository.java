package com.craftion.farmer.storage.repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LogRepository {

    CompletableFuture<Void> append(String farmerId, UUID actorUuid, String action, String detail);

    CompletableFuture<List<FarmerLogEntry>> recent(String farmerId, int limit);
}
