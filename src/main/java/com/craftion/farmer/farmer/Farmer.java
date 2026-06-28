package com.craftion.farmer.farmer;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Farmer {

    private final String farmerId;
    private final String regionId;
    private final UUID ownerUuid;
    private final FarmerStorage storage;
    private final ConcurrentMap<UUID, FarmerMember> members = new ConcurrentHashMap<>();
    private final FarmerSettings settings;
    private final ConcurrentMap<String, Boolean> moduleStates = new ConcurrentHashMap<>();
    private final Instant createdAt;
    private volatile LocationSnapshot location;
    private volatile int level;
    private volatile boolean collectingEnabled;
    private volatile FarmerStatistics statistics;
    private volatile Instant updatedAt;

    public Farmer(
        String farmerId,
        String regionId,
        UUID ownerUuid,
        LocationSnapshot location,
        int level,
        boolean collectingEnabled,
        FarmerStorage storage,
        Collection<FarmerMember> members,
        FarmerSettings settings,
        Map<String, Boolean> moduleStates,
        FarmerStatistics statistics,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.farmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        this.regionId = FarmerValidation.requireNonBlank(regionId, "regionId");
        this.ownerUuid = FarmerValidation.requireUuid(ownerUuid, "ownerUuid");
        this.location = FarmerValidation.requireNonNull(location, "location");
        this.level = FarmerValidation.requirePositive(level, "level");
        this.collectingEnabled = collectingEnabled;
        this.storage = FarmerValidation.requireNonNull(storage, "storage").copy();
        this.settings = FarmerValidation.requireNonNull(settings, "settings").copy();
        this.statistics = FarmerValidation.requireNonNull(statistics, "statistics");
        this.createdAt = FarmerValidation.requireNonNull(createdAt, "createdAt");
        this.updatedAt = FarmerValidation.requireNonNull(updatedAt, "updatedAt");
        FarmerValidation.requireNonNull(members, "members").forEach(this::loadMember);
        FarmerValidation.requireNonNull(moduleStates, "moduleStates").forEach(this::loadModuleState);
    }

    public static Farmer create(String farmerId, String regionId, UUID ownerUuid, LocationSnapshot location) {
        Instant now = Instant.now();
        return new Farmer(
            farmerId,
            regionId,
            ownerUuid,
            location,
            1,
            false,
            new FarmerStorage(),
            List.of(),
            new FarmerSettings(),
            Map.of(),
            FarmerStatistics.empty(),
            now,
            now
        );
    }

    public String farmerId() {
        return this.farmerId;
    }

    public String regionId() {
        return this.regionId;
    }

    public UUID ownerUuid() {
        return this.ownerUuid;
    }

    public Farmer withOwnerUuid(UUID ownerUuid) {
        return new Farmer(
            this.farmerId,
            this.regionId,
            FarmerValidation.requireUuid(ownerUuid, "ownerUuid"),
            this.location,
            this.level,
            this.collectingEnabled,
            this.storage,
            this.members.values(),
            this.settings,
            this.moduleStates,
            this.statistics,
            this.createdAt,
            Instant.now()
        );
    }

    public LocationSnapshot location() {
        return this.location;
    }

    public int level() {
        return this.level;
    }

    public boolean collectingEnabled() {
        return this.collectingEnabled;
    }

    public FarmerStorage storage() {
        synchronized (this) {
            return this.storage.copy();
        }
    }

    public long storageAmount(MaterialKey materialKey) {
        synchronized (this) {
            return this.storage.amount(materialKey);
        }
    }

    public StorageAddResult addStorageAmount(MaterialKey materialKey, long requestedAmount, long capacity) {
        FarmerValidation.requireNonNull(materialKey, "materialKey");
        FarmerValidation.requireNonNegative(requestedAmount, "requestedAmount");

        synchronized (this) {
            long currentAmount = this.storage.amount(materialKey);
            long collectableAmount = collectableAmount(requestedAmount, currentAmount, capacity);
            if (collectableAmount <= 0L) {
                return new StorageAddResult(materialKey, requestedAmount, 0L, requestedAmount, currentAmount, capacity);
            }

            long newAmount = currentAmount + collectableAmount;
            this.storage.setAmount(materialKey, newAmount);
            touch();
            return new StorageAddResult(materialKey, requestedAmount, collectableAmount, requestedAmount - collectableAmount, newAmount, capacity);
        }
    }

    public Map<UUID, FarmerMember> members() {
        return Map.copyOf(this.members);
    }

    public FarmerSettings settings() {
        return this.settings.copy();
    }

    public Map<String, Boolean> moduleStates() {
        return Map.copyOf(this.moduleStates);
    }

    public FarmerStatistics statistics() {
        return this.statistics;
    }

    public Instant createdAt() {
        return this.createdAt;
    }

    public Instant updatedAt() {
        return this.updatedAt;
    }

    public void updateLocation(LocationSnapshot location) {
        this.location = FarmerValidation.requireNonNull(location, "location");
        touch();
    }

    public void updateLevel(int level) {
        this.level = FarmerValidation.requirePositive(level, "level");
        touch();
    }

    public void setCollectingEnabled(boolean collectingEnabled) {
        this.collectingEnabled = collectingEnabled;
        touch();
    }

    public void setStorageAmount(MaterialKey materialKey, long amount) {
        synchronized (this) {
            this.storage.setAmount(materialKey, amount);
            touch();
        }
    }

    public void removeStorage(MaterialKey materialKey) {
        synchronized (this) {
            this.storage.remove(materialKey);
            touch();
        }
    }

    public void putMember(FarmerMember member) {
        loadMember(member);
        touch();
    }

    public void removeMember(UUID playerUuid) {
        this.members.remove(FarmerValidation.requireUuid(playerUuid, "playerUuid"));
        touch();
    }

    public void setSetting(String key, String value) {
        this.settings.set(key, value);
        touch();
    }

    public void removeSetting(String key) {
        this.settings.remove(key);
        touch();
    }

    public void setModuleState(String moduleKey, boolean enabled) {
        loadModuleState(moduleKey, enabled);
        touch();
    }

    public void removeModuleState(String moduleKey) {
        this.moduleStates.remove(FarmerValidation.requireNonBlank(moduleKey, "moduleKey"));
        touch();
    }

    public void updateStatistics(FarmerStatistics statistics) {
        this.statistics = FarmerValidation.requireNonNull(statistics, "statistics");
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private long collectableAmount(long requestedAmount, long currentAmount, long capacity) {
        if (capacity < 0L) {
            if (currentAmount >= Long.MAX_VALUE) {
                return 0L;
            }
            return Math.min(requestedAmount, Long.MAX_VALUE - currentAmount);
        }
        if (currentAmount >= capacity) {
            return 0L;
        }
        return Math.min(requestedAmount, capacity - currentAmount);
    }

    private void loadMember(FarmerMember member) {
        FarmerValidation.requireNonNull(member, "member");
        if (!this.farmerId.equals(member.farmerId())) {
            throw new IllegalArgumentException("member farmerId must match farmerId.");
        }
        this.members.put(member.playerUuid(), member);
    }

    private void loadModuleState(String moduleKey, boolean enabled) {
        this.moduleStates.put(FarmerValidation.requireNonBlank(moduleKey, "moduleKey"), enabled);
    }
}
