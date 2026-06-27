package com.craftion.farmer.storage.repository;

import com.craftion.farmer.farmer.FarmerStorage;
import com.craftion.farmer.farmer.MaterialKey;
import java.util.concurrent.CompletableFuture;

public interface StorageRepository {

    CompletableFuture<FarmerStorage> load(String farmerId);

    CompletableFuture<Long> amount(String farmerId, MaterialKey materialKey);

    CompletableFuture<Void> saveAmount(String farmerId, MaterialKey materialKey, long amount);

    CompletableFuture<Void> delete(String farmerId, MaterialKey materialKey);
}
