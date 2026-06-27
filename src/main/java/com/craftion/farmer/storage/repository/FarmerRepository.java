package com.craftion.farmer.storage.repository;

import java.util.concurrent.CompletableFuture;

public interface FarmerRepository {

    CompletableFuture<Boolean> exists(String farmerId);
}
