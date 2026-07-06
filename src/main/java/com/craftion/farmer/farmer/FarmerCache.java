package com.craftion.farmer.farmer;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class FarmerCache {

    private final ConcurrentMap<String, Farmer> farmers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> farmerIdsByPlayerUuid = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<UUID>> indexedPlayerUuidsByFarmerId = new ConcurrentHashMap<>();
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
        String farmerId = validatedFarmer.farmerId();
        synchronized (this.mutationLock) {
            Farmer previousFarmer = this.farmers.get(farmerId);
            if (previousFarmer != null) {
                this.farmerIdsByRegionId.remove(previousFarmer.regionId(), farmerId);
            }
            removePlayerIndexes(farmerId);

            this.farmers.put(farmerId, validatedFarmer);
            this.farmerIdsByRegionId.put(validatedFarmer.regionId(), farmerId);

            Set<UUID> indexedPlayerUuids = indexedPlayerUuids(validatedFarmer);
            indexedPlayerUuids.forEach(playerUuid -> this.farmerIdsByPlayerUuid.put(playerUuid, farmerId));
            this.indexedPlayerUuidsByFarmerId.put(farmerId, indexedPlayerUuids);
            return validatedFarmer;
        }
    }

    public Optional<Farmer> remove(String farmerId) {
        String normalizedFarmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        synchronized (this.mutationLock) {
            Farmer removedFarmer = this.farmers.remove(normalizedFarmerId);
            if (removedFarmer != null) {
                this.farmerIdsByRegionId.remove(removedFarmer.regionId(), normalizedFarmerId);
            }
            removePlayerIndexes(normalizedFarmerId);
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
            if (farmer == null) {
                this.farmerIdsByRegionId.remove(normalizedRegionId, farmerId);
                removePlayerIndexes(farmerId);
                return Optional.empty();
            }
            if (!normalizedRegionId.equals(farmer.regionId())) {
                this.farmerIdsByRegionId.remove(normalizedRegionId, farmerId);
                return Optional.empty();
            }

            this.farmers.remove(farmerId);
            this.farmerIdsByRegionId.remove(normalizedRegionId, farmerId);
            removePlayerIndexes(farmerId);
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
            this.indexedPlayerUuidsByFarmerId.clear();
            this.farmerIdsByRegionId.clear();
        }
    }

    private Set<UUID> indexedPlayerUuids(Farmer farmer) {
        Set<UUID> playerUuids = new HashSet<>(farmer.members().keySet());
        playerUuids.add(farmer.ownerUuid());
        return Set.copyOf(playerUuids);
    }

    private void removePlayerIndexes(String farmerId) {
        Set<UUID> indexedPlayerUuids = this.indexedPlayerUuidsByFarmerId.remove(farmerId);
        if (indexedPlayerUuids == null) {
            return;
        }
        indexedPlayerUuids.forEach(playerUuid -> this.farmerIdsByPlayerUuid.remove(playerUuid, farmerId));
    }
}
