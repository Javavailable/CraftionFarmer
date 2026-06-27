package com.craftion.farmer.storage.repository;

import java.util.concurrent.CompletableFuture;

public interface StorageRepository {

    CompletableFuture<Long> amount(String farmerId, String materialKey);
}
