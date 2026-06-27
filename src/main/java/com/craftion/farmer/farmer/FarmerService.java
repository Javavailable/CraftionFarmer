package com.craftion.farmer.farmer;

import com.craftion.farmer.storage.repository.FarmerRepository;
import com.craftion.farmer.storage.repository.LogRepository;
import com.craftion.farmer.storage.repository.MemberRepository;
import com.craftion.farmer.storage.repository.StorageRepository;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class FarmerService {

    private final FarmerRepository farmerRepository;
    private final MemberRepository memberRepository;
    private final StorageRepository storageRepository;
    private final LogRepository logRepository;
    private final FarmerCache cache;

    public FarmerService(
        FarmerRepository farmerRepository,
        MemberRepository memberRepository,
        StorageRepository storageRepository,
        LogRepository logRepository,
        FarmerCache cache
    ) {
        this.farmerRepository = FarmerValidation.requireNonNull(farmerRepository, "farmerRepository");
        this.memberRepository = FarmerValidation.requireNonNull(memberRepository, "memberRepository");
        this.storageRepository = FarmerValidation.requireNonNull(storageRepository, "storageRepository");
        this.logRepository = FarmerValidation.requireNonNull(logRepository, "logRepository");
        this.cache = FarmerValidation.requireNonNull(cache, "cache");
    }

    public CompletableFuture<Optional<Farmer>> load(String farmerId) {
        String normalizedFarmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        return this.cache.load(normalizedFarmerId, () -> this.farmerRepository.findById(normalizedFarmerId));
    }

    public Optional<Farmer> getCached(String farmerId) {
        return this.cache.get(farmerId);
    }

    public Farmer putCached(Farmer farmer) {
        return this.cache.put(farmer);
    }

    public CompletableFuture<Void> save(Farmer farmer) {
        Farmer validatedFarmer = FarmerValidation.requireNonNull(farmer, "farmer");
        return this.farmerRepository.save(validatedFarmer).thenRun(() -> this.cache.put(validatedFarmer));
    }

    public CompletableFuture<Void> update(Farmer farmer) {
        Farmer validatedFarmer = FarmerValidation.requireNonNull(farmer, "farmer");
        return this.farmerRepository.update(validatedFarmer).thenRun(() -> this.cache.put(validatedFarmer));
    }

    public CompletableFuture<Void> delete(String farmerId) {
        String normalizedFarmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        return this.farmerRepository.delete(normalizedFarmerId).thenRun(() -> this.cache.remove(normalizedFarmerId));
    }

    public FarmerCache cache() {
        return this.cache;
    }

    public FarmerRepository farmerRepository() {
        return this.farmerRepository;
    }

    public MemberRepository memberRepository() {
        return this.memberRepository;
    }

    public StorageRepository storageRepository() {
        return this.storageRepository;
    }

    public LogRepository logRepository() {
        return this.logRepository;
    }
}
