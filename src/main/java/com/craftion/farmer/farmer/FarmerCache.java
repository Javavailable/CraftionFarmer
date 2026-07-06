package com.craftion.farmer.farmer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class FarmerCache {

    private final ConcurrentMap<String, Farmer> farmers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> farmerIdsByPlayerUuid = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> farmerIdsByRegionId = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();

    public CompletableFuture<Optional<Farmer>> load(String farmerId, Supplier<CompletableFuture<Optional<Farmer>>> loader) {
        String normalizedFarmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        Farmer cachedFarmer = this.farmers.get(normalizedFarmerId);
        if (cachedFarmer != null) {
            return CompletableFuture.completedFuture(Optional.of(cachedFarmer));
        }

        CompletableFuture<Optional<Farmer>> loadedFarmer = FarmerValidation.requireNonNull(loader, "loader").get();
        return FarmerValidation.requireNonNull(loadedFarmer, "loadedFarmer").thenApply(result -> {
            Optional<Farmer> optionalFarmer = FarmerValidation.requireNonNull(result, "loadedFarmerResult");
            optionalFarmer.ifPresent(this::put);
            return optionalFarmer;
        });
    }

    public Optional<Farmer> get(String farmerId) {
        return Optional.ofNullable(this.farmers.get(FarmerValidation.requireNonBlank(farmerId, "farmerId")));
    }

    public Optional<Farmer> getByRegionId(String regionId) {
        String normalizedRegionId = FarmerValidation.requireNonBlank(regionId, "regionId");
        String farmerId = this.farmerIdsByRegionId.get(normalizedRegionId);
        if (farmerId == null || farmerId.isBlank()) {
            return Optional.empty();
        }

        Farmer farmer = this.farmers.get(farmerId);
        if (farmer == null || !normalizedRegionId.equals(farmer.regionId())) {
            return Optional.empty();
        }
        return Optional.of(farmer);
    }

    public Optional<Farmer> getByPlayerUuid(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        String farmerId = this.farmerIdsByPlayerUuid.get(playerUuid);
        if (farmerId == null || farmerId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.farmers.get(farmerId));
    }

    public Farmer put(Farmer farmer) {
        Farmer validatedFarmer = FarmerValidation.requireNonNull(farmer, "farmer");
        synchronized (this.mutationLock) {
            Farmer previousFarmer = this.farmers.get(validatedFarmer.farmerId());
            if (previousFarmer != null) {
                removeIndexes(previousFarmer);
            }

            this.farmers.put(validatedFarmer.farmerId(), validatedFarmer);
            index(validatedFarmer);
            return validatedFarmer;
        }
    }

    public Optional<Farmer> remove(String farmerId) {
        String normalizedFarmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        synchronized (this.mutationLock) {
            Farmer removedFarmer = this.farmers.remove(normalizedFarmerId);
            if (removedFarmer != null) {
                removeIndexes(removedFarmer);
            }
            return Optional.ofNullable(removedFarmer);
        }
    }

    public Optional<Farmer> removeByRegionId(String regionId) {
        String normalizedRegionId = FarmerValidation.requireNonBlank(regionId, "regionId");
        synchronized (this.mutationLock) {
            String farmerId = this.farmerIdsByRegionId.get(normalizedRegionId);
            if (farmerId == null || farmerId.isBlank()) {
                return Optional.empty();
            }

            Farmer farmer = this.farmers.get(farmerId);
            if (farmer == null || !normalizedRegionId.equals(farmer.regionId())) {
                this.farmerIdsByRegionId.remove(normalizedRegionId, farmerId);
                return Optional.empty();
            }

            this.farmers.remove(farmerId);
            removeIndexes(farmer);
            return Optional.of(farmer);
        }
    }

    public boolean contains(String farmerId) {
        return this.farmers.containsKey(FarmerValidation.requireNonBlank(farmerId, "farmerId"));
    }

    public Map<String, Farmer> snapshot() {
        return Map.copyOf(this.farmers);
    }

    public void clear() {
        synchronized (this.mutationLock) {
            this.farmers.clear();
            this.farmerIdsByPlayerUuid.clear();
            this.farmerIdsByRegionId.clear();
        }
    }

    private void index(Farmer farmer) {
        this.farmerIdsByRegionId.put(farmer.regionId(), farmer.farmerId());
        this.farmerIdsByPlayerUuid.put(farmer.ownerUuid(), farmer.farmerId());
        farmer.members().keySet().forEach(playerUuid -> this.farmerIdsByPlayerUuid.put(playerUuid, farmer.farmerId()));
    }

    private void removeIndexes(Farmer farmer) {
        String farmerId = farmer.farmerId();
        this.farmerIdsByRegionId.remove(farmer.regionId(), farmerId);
        this.farmerIdsByPlayerUuid.remove(farmer.ownerUuid(), farmerId);
        farmer.members().keySet().forEach(playerUuid -> this.farmerIdsByPlayerUuid.remove(playerUuid, farmerId));
    }
}
