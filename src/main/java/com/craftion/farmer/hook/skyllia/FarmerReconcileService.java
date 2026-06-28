package com.craftion.farmer.hook.skyllia;

import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.FarmerMember;
import com.craftion.farmer.farmer.FarmerRole;
import com.craftion.farmer.hook.region.RegionMemberInfo;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.hook.visual.VisualProviderManager;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FarmerReconcileService {

    private static final List<String> FARMER_CHILD_TABLES = List.of(
        "farmer_members",
        "farmer_storage",
        "farmer_settings",
        "farmer_modules",
        "farmer_logs"
    );

    private final SchedulerAdapter schedulerAdapter;
    private final DatabaseManager databaseManager;
    private final FarmerCache farmerCache;
    private final RegionProviderManager regionProviderManager;
    private final VisualProviderManager visualProviderManager;
    private final DebugLogger debugLogger;

    public FarmerReconcileService(
        SchedulerAdapter schedulerAdapter,
        DatabaseManager databaseManager,
        FarmerCache farmerCache,
        RegionProviderManager regionProviderManager,
        VisualProviderManager visualProviderManager,
        DebugLogger debugLogger
    ) {
        this.schedulerAdapter = Objects.requireNonNull(schedulerAdapter, "schedulerAdapter");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.farmerCache = Objects.requireNonNull(farmerCache, "farmerCache");
        this.regionProviderManager = Objects.requireNonNull(regionProviderManager, "regionProviderManager");
        this.visualProviderManager = Objects.requireNonNull(visualProviderManager, "visualProviderManager");
        this.debugLogger = Objects.requireNonNull(debugLogger, "debugLogger");
    }

    public CompletableFuture<FarmerReconcileResult> reconcileRegion(String regionId) {
        Optional<String> normalizedRegionId = normalize(regionId);
        if (normalizedRegionId.isEmpty()) {
            return CompletableFuture.completedFuture(FarmerReconcileResult.noRegion());
        }

        return loadRegionSnapshot(normalizedRegionId.get()).thenCompose(optionalSnapshot -> {
            if (optionalSnapshot.isEmpty()) {
                return CompletableFuture.completedFuture(FarmerReconcileResult.noRegion());
            }
            return syncSnapshot(optionalSnapshot.get());
        });
    }

    public CompletableFuture<Boolean> deleteFarmerByRegionId(String regionId) {
        Optional<String> normalizedRegionId = normalize(regionId);
        if (normalizedRegionId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        String region = normalizedRegionId.get();
        return this.databaseManager.supplyAsync(connection -> withTransaction(connection, () -> {
            List<String> farmerIds = findFarmerIdsByRegionId(connection, region);
            for (String farmerId : farmerIds) {
                deleteFarmerChildren(connection, farmerId);
            }
            int deleted = deleteFarmersByRegionId(connection, region);
            return deleted > 0 || !farmerIds.isEmpty() ? farmerIds : List.<String>of();
        })).thenApply(deletedFarmerIds -> {
            if (!deletedFarmerIds.isEmpty()) {
                removeCachedRegion(region);
                for (String farmerId : deletedFarmerIds) {
                    this.visualProviderManager.remove(farmerId);
                }
            }
            return !deletedFarmerIds.isEmpty();
        });
    }

    public CompletableFuture<Boolean> disableFarmerByRegionId(String regionId) {
        Optional<String> normalizedRegionId = normalize(regionId);
        if (normalizedRegionId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        String region = normalizedRegionId.get();
        return this.databaseManager.supplyAsync(connection -> updateCollectingStateByRegionId(connection, region, false, Instant.now()) > 0)
            .thenApply(updated -> {
                disableCachedRegion(region);
                return updated;
            });
    }

    public CompletableFuture<Boolean> removeMemberByRegionId(String regionId, UUID playerUuid) {
        Optional<String> normalizedRegionId = normalize(regionId);
        if (normalizedRegionId.isEmpty() || playerUuid == null) {
            return CompletableFuture.completedFuture(false);
        }

        String region = normalizedRegionId.get();
        return this.databaseManager.supplyAsync(connection -> withTransaction(connection, () -> {
            int removed = 0;
            for (String farmerId : findFarmerIdsByRegionId(connection, region)) {
                removed += deleteMember(connection, farmerId, playerUuid);
            }
            return removed > 0;
        })).thenApply(removed -> {
            removeCachedMember(region, playerUuid);
            return removed;
        });
    }

    private CompletableFuture<Optional<RegionSnapshot>> loadRegionSnapshot(String regionId) {
        CompletableFuture<Optional<RegionSnapshot>> future = new CompletableFuture<>();
        this.schedulerAdapter.runGlobal(() -> {
            try {
                RegionProvider provider = this.regionProviderManager.provider();
                Optional<RegionMemberInfo> optionalOwner = provider.owner(regionId);
                if (optionalOwner.isEmpty()) {
                    future.complete(Optional.empty());
                    return;
                }

                RegionMemberInfo owner = optionalOwner.get();
                Map<UUID, FarmerRole> members = new LinkedHashMap<>();
                members.put(owner.playerUuid(), FarmerRole.OWNER);
                for (RegionMemberInfo member : provider.members(regionId)) {
                    if (isPersistableMember(member)) {
                        members.put(member.playerUuid(), member.owner() ? FarmerRole.OWNER : member.role());
                    }
                }
                members.put(owner.playerUuid(), FarmerRole.OWNER);
                future.complete(Optional.of(new RegionSnapshot(regionId, owner.playerUuid(), Map.copyOf(members))));
            } catch (RuntimeException | LinkageError exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private CompletableFuture<FarmerReconcileResult> syncSnapshot(RegionSnapshot snapshot) {
        return this.databaseManager.supplyAsync(connection -> withTransaction(connection, () -> {
            Optional<FarmerRow> optionalFarmer = findFarmerByRegionId(connection, snapshot.regionId());
            if (optionalFarmer.isEmpty()) {
                return FarmerReconcileResult.noFarmer();
            }

            FarmerRow farmer = optionalFarmer.get();
            Map<UUID, FarmerRole> currentMembers = loadMemberRoles(connection, farmer.farmerId());
            Instant now = Instant.now();
            int ownersUpdated = 0;
            int membersAdded = 0;
            int membersUpdated = 0;
            int membersRemoved = 0;

            if (!farmer.ownerUuid().equals(snapshot.ownerUuid())) {
                updateOwner(connection, farmer.farmerId(), snapshot.ownerUuid(), now);
                ownersUpdated = 1;
            }

            for (UUID currentMember : currentMembers.keySet()) {
                if (!snapshot.members().containsKey(currentMember)) {
                    membersRemoved += deleteMember(connection, farmer.farmerId(), currentMember);
                }
            }

            for (Map.Entry<UUID, FarmerRole> entry : snapshot.members().entrySet()) {
                FarmerRole currentRole = currentMembers.get(entry.getKey());
                if (currentRole == null) {
                    insertMember(connection, farmer.farmerId(), entry.getKey(), entry.getValue(), now);
                    membersAdded++;
                } else if (currentRole != entry.getValue()) {
                    updateMemberRole(connection, farmer.farmerId(), entry.getKey(), entry.getValue());
                    membersUpdated++;
                }
            }

            if (ownersUpdated > 0 || membersAdded > 0 || membersUpdated > 0 || membersRemoved > 0) {
                touchFarmer(connection, farmer.farmerId(), now);
                return FarmerReconcileResult.updated(ownersUpdated, membersAdded, membersUpdated, membersRemoved);
            }

            return FarmerReconcileResult.unchanged();
        })).thenApply(result -> {
            if (result.status() == FarmerReconcileResult.Status.UPDATED || result.status() == FarmerReconcileResult.Status.UNCHANGED) {
                applyCachedSnapshot(snapshot);
            }
            this.debugLogger.debug("Skyllia reconcile result for " + snapshot.regionId() + ": " + result.status());
            return result;
        });
    }

    private Optional<FarmerRow> findFarmerByRegionId(Connection connection, String regionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, owner_uuid FROM farmers WHERE region_id = ?")) {
            statement.setString(1, regionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FarmerRow(resultSet.getString("id"), UUID.fromString(resultSet.getString("owner_uuid"))));
            }
        }
    }

    private List<String> findFarmerIdsByRegionId(Connection connection, String regionId) throws SQLException {
        List<String> farmerIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM farmers WHERE region_id = ?")) {
            statement.setString(1, regionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    farmerIds.add(resultSet.getString("id"));
                }
            }
        }
        return farmerIds;
    }

    private Map<UUID, FarmerRole> loadMemberRoles(Connection connection, String farmerId) throws SQLException {
        Map<UUID, FarmerRole> members = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid, role FROM farmer_members WHERE farmer_id = ?")) {
            statement.setString(1, farmerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                    FarmerRole role = FarmerRole.valueOf(resultSet.getString("role"));
                    members.put(playerUuid, role);
                }
            }
        }
        return members;
    }

    private void updateOwner(Connection connection, String farmerId, UUID ownerUuid, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE farmers SET owner_uuid = ?, updated_at = ? WHERE id = ?")) {
            statement.setString(1, ownerUuid.toString());
            statement.setLong(2, now.toEpochMilli());
            statement.setString(3, farmerId);
            statement.executeUpdate();
        }
    }

    private void touchFarmer(Connection connection, String farmerId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE farmers SET updated_at = ? WHERE id = ?")) {
            statement.setLong(1, now.toEpochMilli());
            statement.setString(2, farmerId);
            statement.executeUpdate();
        }
    }

    private void insertMember(Connection connection, String farmerId, UUID playerUuid, FarmerRole role, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO farmer_members (farmer_id, player_uuid, role, added_at) VALUES (?, ?, ?, ?)"
        )) {
            statement.setString(1, farmerId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, role.name());
            statement.setLong(4, now.toEpochMilli());
            statement.executeUpdate();
        }
    }

    private void updateMemberRole(Connection connection, String farmerId, UUID playerUuid, FarmerRole role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE farmer_members SET role = ? WHERE farmer_id = ? AND player_uuid = ?"
        )) {
            statement.setString(1, role.name());
            statement.setString(2, farmerId);
            statement.setString(3, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private int deleteMember(Connection connection, String farmerId, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM farmer_members WHERE farmer_id = ? AND player_uuid = ?")) {
            statement.setString(1, farmerId);
            statement.setString(2, playerUuid.toString());
            return statement.executeUpdate();
        }
    }

    private int updateCollectingStateByRegionId(Connection connection, String regionId, boolean collectingEnabled, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE farmers SET collecting_enabled = ?, updated_at = ? WHERE region_id = ?"
        )) {
            statement.setBoolean(1, collectingEnabled);
            statement.setLong(2, now.toEpochMilli());
            statement.setString(3, regionId);
            return statement.executeUpdate();
        }
    }

    private void deleteFarmerChildren(Connection connection, String farmerId) throws SQLException {
        for (String table : FARMER_CHILD_TABLES) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE farmer_id = ?")) {
                statement.setString(1, farmerId);
                statement.executeUpdate();
            }
        }
    }

    private int deleteFarmersByRegionId(Connection connection, String regionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM farmers WHERE region_id = ?")) {
            statement.setString(1, regionId);
            return statement.executeUpdate();
        }
    }

    private void applyCachedSnapshot(RegionSnapshot snapshot) {
        for (Farmer cachedFarmer : this.farmerCache.snapshot().values()) {
            if (!snapshot.regionId().equals(cachedFarmer.regionId())) {
                continue;
            }

            Farmer farmer = cachedFarmer;
            if (!farmer.ownerUuid().equals(snapshot.ownerUuid())) {
                farmer = farmer.withOwnerUuid(snapshot.ownerUuid());
                this.farmerCache.put(farmer);
            }

            Map<UUID, FarmerMember> cachedMembers = farmer.members();
            for (UUID cachedMember : cachedMembers.keySet()) {
                if (!snapshot.members().containsKey(cachedMember)) {
                    farmer.removeMember(cachedMember);
                }
            }

            Instant now = Instant.now();
            for (Map.Entry<UUID, FarmerRole> entry : snapshot.members().entrySet()) {
                FarmerMember currentMember = farmer.members().get(entry.getKey());
                if (currentMember == null || currentMember.role() != entry.getValue()) {
                    Instant addedAt = currentMember == null ? now : currentMember.addedAt();
                    farmer.putMember(new FarmerMember(farmer.farmerId(), entry.getKey(), entry.getValue(), addedAt));
                }
            }
        }
    }

    private void removeCachedRegion(String regionId) {
        for (Farmer farmer : this.farmerCache.snapshot().values()) {
            if (regionId.equals(farmer.regionId())) {
                this.farmerCache.remove(farmer.farmerId());
            }
        }
    }

    private void disableCachedRegion(String regionId) {
        for (Farmer farmer : this.farmerCache.snapshot().values()) {
            if (regionId.equals(farmer.regionId())) {
                farmer.setCollectingEnabled(false);
            }
        }
    }

    private void removeCachedMember(String regionId, UUID playerUuid) {
        for (Farmer farmer : this.farmerCache.snapshot().values()) {
            if (regionId.equals(farmer.regionId())) {
                farmer.removeMember(playerUuid);
            }
        }
    }

    private boolean isPersistableMember(RegionMemberInfo member) {
        if (member == null || member.trusted()) {
            return false;
        }
        return member.role() == FarmerRole.OWNER || member.role() == FarmerRole.MANAGER || member.role() == FarmerRole.MEMBER;
    }

    private Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private <T> T withTransaction(Connection connection, SqlOperation<T> operation) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = operation.run();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {

        T run() throws SQLException;
    }

    private record RegionSnapshot(String regionId, UUID ownerUuid, Map<UUID, FarmerRole> members) {
    }

    private record FarmerRow(String farmerId, UUID ownerUuid) {
    }
}
