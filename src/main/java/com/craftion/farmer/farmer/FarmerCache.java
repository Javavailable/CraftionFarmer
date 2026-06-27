package com.craftion.farmer.farmer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class FarmerCache {

    private final ConcurrentMap<String, Farmer> farmers = new ConcurrentHashMap<>();

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
        return this.farmers.values().stream()
            .filter(farmer -> normalizedRegionId.equals(farmer.regionId()))
            .findFirst();
    }

    public Farmer put(Farmer farmer) {
        Farmer validatedFarmer = FarmerValidation.requireNonNull(farmer, "farmer");
        this.farmers.put(validatedFarmer.farmerId(), validatedFarmer);
        return validatedFarmer;
    }

    public Optional<Farmer> remove(String farmerId) {
        return Optional.ofNullable(this.farmers.remove(FarmerValidation.requireNonBlank(farmerId, "farmerId")));
    }

    public Optional<Farmer> removeByRegionId(String regionId) {
        Optional<Farmer> farmer = getByRegionId(regionId);
        farmer.ifPresent(value -> this.farmers.remove(value.farmerId()));
        return farmer;
    }

    public boolean contains(String farmerId) {
        return this.farmers.containsKey(FarmerValidation.requireNonBlank(farmerId, "farmerId"));
    }

    public Map<String, Farmer> snapshot() {
        return Map.copyOf(this.farmers);
    }

    public void clear() {
        this.farmers.clear();
    }
}
