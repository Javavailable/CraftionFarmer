package com.craftion.farmer.storage.repository;

import com.craftion.farmer.farmer.Farmer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface FarmerRepository {

    CompletableFuture<Boolean> exists(String farmerId);

    CompletableFuture<Optional<Farmer>> findById(String farmerId);

    CompletableFuture<Void> save(Farmer farmer);

    CompletableFuture<Void> update(Farmer farmer);

    CompletableFuture<Void> delete(String farmerId);
}
