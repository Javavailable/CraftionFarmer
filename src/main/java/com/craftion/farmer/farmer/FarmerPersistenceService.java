package com.craftion.farmer.farmer;

import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FarmerPersistenceService {

    private static final List<String> REPLACE_CHILD_TABLES = List.of(
        "farmer_members",
        "farmer_storage",
        "farmer_settings",
        "farmer_modules",
        "farmer_product_states"
    );
    private static final List<String> DELETE_CHILD_TABLES = List.of(
        "farmer_members",
        "farmer_storage",
        "farmer_settings",
        "farmer_modules",
        "farmer_product_states",
        "farmer_logs"
    );

    private final DatabaseManager databaseManager;
    private final FarmerCache farmerCache;
    private final DebugLogger debugLogger;

    public FarmerPersistenceService(DatabaseManager databaseManager, FarmerCache farmerCache, DebugLogger debugLogger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.farmerCache = Objects.requireNonNull(farmerCache, "farmerCache");
        this.debugLogger = Objects.requireNonNull(debugLogger, "debugLogger");
    }

    public CompletableFuture<List<Farmer>> loadAll() {
        return this.databaseManager.supplyAsync(this::loadAll).thenApply(farmers -> {
            this.farmerCache.clear();
            for (Farmer farmer : farmers) {
                this.farmerCache.put(farmer);
            }
            this.debugLogger.debug("Loaded farmers: " + farmers.size());
            return farmers;
        });
    }

    public CompletableFuture<Optional<Farmer>> findById(String farmerId) {
        String normalizedFarmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        Optional<Farmer> cached = this.farmerCache.get(normalizedFarmerId);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return this.databaseManager.supplyAsync(connection -> findById(connection, normalizedFarmerId)).thenApply(result -> {
            result.ifPresent(this.farmerCache::put);
            return result;
        });
    }

    public CompletableFuture<Optional<Farmer>> findByRegionId(String regionId) {
        String normalizedRegionId = FarmerValidation.requireNonBlank(regionId, "regionId");
        Optional<Farmer> cached = this.farmerCache.getByRegionId(normalizedRegionId);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return this.databaseManager.supplyAsync(connection -> findByRegionId(connection, normalizedRegionId)).thenApply(result -> {
            result.ifPresent(this.farmerCache::put);
            return result;
        });
    }

    public CompletableFuture<Void> save(Farmer farmer) {
        Farmer validatedFarmer = FarmerValidation.requireNonNull(farmer, "farmer");
        return this.databaseManager.executeAsync(connection -> withTransaction(connection, () -> {
            saveFarmer(connection, validatedFarmer);
            return null;
        })).thenRun(() -> this.farmerCache.put(validatedFarmer));
    }

    public CompletableFuture<Void> saveAllCached() {
        List<Farmer> farmers = List.copyOf(this.farmerCache.snapshot().values());
        return saveAll(farmers);
    }

    public void saveAllCachedBlocking() throws SQLException {
        saveAllBlocking(List.copyOf(this.farmerCache.snapshot().values()));
    }

    public CompletableFuture<Void> saveAll(Collection<Farmer> farmers) {
        List<Farmer> validatedFarmers = List.copyOf(FarmerValidation.requireNonNull(farmers, "farmers"));
        if (validatedFarmers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return this.databaseManager.executeAsync(connection -> withTransaction(connection, () -> {
            for (Farmer farmer : validatedFarmers) {
                saveFarmer(connection, farmer);
            }
            return null;
        })).thenRun(() -> validatedFarmers.forEach(this.farmerCache::put));
    }

    public void saveAllBlocking(Collection<Farmer> farmers) throws SQLException {
        List<Farmer> validatedFarmers = List.copyOf(FarmerValidation.requireNonNull(farmers, "farmers"));
        if (validatedFarmers.isEmpty()) {
            return;
        }

        this.databaseManager.supplyBlocking(connection -> withTransaction(connection, () -> {
            for (Farmer farmer : validatedFarmers) {
                saveFarmer(connection, farmer);
            }
            return null;
        }));
        validatedFarmers.forEach(this.farmerCache::put);
    }

    public CompletableFuture<List<String>> saveExistingAll(Collection<Farmer> farmers) {
        List<Farmer> validatedFarmers = List.copyOf(FarmerValidation.requireNonNull(farmers, "farmers"));
        if (validatedFarmers.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return this.databaseManager.supplyAsync(connection -> withTransaction(connection, () -> {
            List<String> missingFarmerIds = new ArrayList<>();
            for (Farmer farmer : validatedFarmers) {
                if (!saveExistingFarmer(connection, farmer)) {
                    missingFarmerIds.add(farmer.farmerId());
                }
            }
            return List.copyOf(missingFarmerIds);
        }));
    }

    public List<String> saveExistingAllBlocking(Collection<Farmer> farmers) throws SQLException {
        List<Farmer> validatedFarmers = List.copyOf(FarmerValidation.requireNonNull(farmers, "farmers"));
        if (validatedFarmers.isEmpty()) {
            return List.of();
        }

        return this.databaseManager.supplyBlocking(connection -> withTransaction(connection, () -> {
            List<String> missingFarmerIds = new ArrayList<>();
            for (Farmer farmer : validatedFarmers) {
                if (!saveExistingFarmer(connection, farmer)) {
                    missingFarmerIds.add(farmer.farmerId());
                }
            }
            return List.copyOf(missingFarmerIds);
        }));
    }

    public CompletableFuture<Boolean> deleteByRegionId(String regionId) {
        String normalizedRegionId = FarmerValidation.requireNonBlank(regionId, "regionId");
        return this.databaseManager.supplyAsync(connection -> withTransaction(connection, () -> {
            List<String> farmerIds = findFarmerIdsByRegionId(connection, normalizedRegionId);
            for (String farmerId : farmerIds) {
                deleteChildRows(connection, farmerId, DELETE_CHILD_TABLES);
            }
            int deleted = deleteFarmersByRegionId(connection, normalizedRegionId);
            return deleted > 0 || !farmerIds.isEmpty();
        })).thenApply(deleted -> {
            if (deleted) {
                this.farmerCache.removeByRegionId(normalizedRegionId);
            }
            return deleted;
        });
    }

    private List<Farmer> loadAll(Connection connection) throws SQLException {
        List<Farmer> farmers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM farmers")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    farmers.add(loadFarmer(connection, resultSet));
                }
            }
        }
        return farmers;
    }

    private Optional<Farmer> findById(Connection connection, String farmerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM farmers WHERE id = ?")) {
            statement.setString(1, farmerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(loadFarmer(connection, resultSet));
            }
        }
    }

    private Optional<Farmer> findByRegionId(Connection connection, String regionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM farmers WHERE region_id = ?")) {
            statement.setString(1, regionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(loadFarmer(connection, resultSet));
            }
        }
    }

    private Farmer loadFarmer(Connection connection, ResultSet resultSet) throws SQLException {
        String farmerId = resultSet.getString("id");
        return new Farmer(
            farmerId,
            resultSet.getString("region_id"),
            UUID.fromString(resultSet.getString("owner_uuid")),
            LocationSnapshot.of(
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getFloat("yaw"),
                resultSet.getFloat("pitch")
            ),
            resultSet.getInt("level"),
            resultSet.getBoolean("collecting_enabled"),
            loadStorage(connection, farmerId),
            loadMembers(connection, farmerId),
            new FarmerSettings(loadSettings(connection, farmerId)),
            loadModules(connection, farmerId),
            loadProductCollectingStates(connection, farmerId),
            resultSet.getLong("xp_buffer"),
            FarmerStatistics.empty(),
            Instant.ofEpochMilli(resultSet.getLong("created_at")),
            Instant.ofEpochMilli(resultSet.getLong("updated_at"))
        );
    }

    private FarmerStorage loadStorage(Connection connection, String farmerId) throws SQLException {
        Map<MaterialKey, Long> amounts = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT material_key, amount FROM farmer_storage WHERE farmer_id = ?"
        )) {
            statement.setString(1, farmerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    amounts.put(MaterialKey.of(resultSet.getString("material_key")), resultSet.getLong("amount"));
                }
            }
        }
        return new FarmerStorage(amounts);
    }

    private List<FarmerMember> loadMembers(Connection connection, String farmerId) throws SQLException {
        List<FarmerMember> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT player_uuid, role, added_at FROM farmer_members WHERE farmer_id = ?"
        )) {
            statement.setString(1, farmerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(new FarmerMember(
                        farmerId,
                        UUID.fromString(resultSet.getString("player_uuid")),
                        FarmerRole.valueOf(resultSet.getString("role")),
                        Instant.ofEpochMilli(resultSet.getLong("added_at"))
                    ));
                }
            }
        }
        return members;
    }

    private Map<String, String> loadSettings(Connection connection, String farmerId) throws SQLException {
        Map<String, String> settings = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT setting_key, setting_value FROM farmer_settings WHERE farmer_id = ?"
        )) {
            statement.setString(1, farmerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    settings.put(resultSet.getString("setting_key"), resultSet.getString("setting_value"));
                }
            }
        }
        return settings;
    }

    private Map<String, Boolean> loadModules(Connection connection, String farmerId) throws SQLException {
        Map<String, Boolean> modules = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT module_key, enabled FROM farmer_modules WHERE farmer_id = ?"
        )) {
            statement.setString(1, farmerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    modules.put(resultSet.getString("module_key"), resultSet.getBoolean("enabled"));
                }
            }
        }
        return modules;
    }

    private Map<MaterialKey, Boolean> loadProductCollectingStates(Connection connection, String farmerId) throws SQLException {
        Map<MaterialKey, Boolean> states = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT material_key, collecting_enabled FROM farmer_product_states WHERE farmer_id = ?"
        )) {
            statement.setString(1, farmerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    states.put(MaterialKey.of(resultSet.getString("material_key")), resultSet.getBoolean("collecting_enabled"));
                }
            }
        }
        return states;
    }

    private void saveFarmer(Connection connection, Farmer farmer) throws SQLException {
        int updated = updateFarmer(connection, farmer);
        if (updated == 0) {
            insertFarmer(connection, farmer);
        }
        deleteChildRows(connection, farmer.farmerId(), REPLACE_CHILD_TABLES);
        insertMembers(connection, farmer);
        insertStorage(connection, farmer);
        insertSettings(connection, farmer);
        insertModules(connection, farmer);
        insertProductCollectingStates(connection, farmer);
    }

    private boolean saveExistingFarmer(Connection connection, Farmer farmer) throws SQLException {
        int updated = updateFarmer(connection, farmer);
        if (updated == 0) {
            return false;
        }
        deleteChildRows(connection, farmer.farmerId(), REPLACE_CHILD_TABLES);
        insertMembers(connection, farmer);
        insertStorage(connection, farmer);
        insertSettings(connection, farmer);
        insertModules(connection, farmer);
        insertProductCollectingStates(connection, farmer);
        return true;
    }

    private int updateFarmer(Connection connection, Farmer farmer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE farmers SET region_id = ?, owner_uuid = ?, world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, "
                + "level = ?, collecting_enabled = ?, xp_buffer = ?, created_at = ?, updated_at = ? WHERE id = ?"
        )) {
            bindFarmer(statement, farmer);
            statement.setString(14, farmer.farmerId());
            return statement.executeUpdate();
        }
    }

    private void insertFarmer(Connection connection, Farmer farmer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO farmers (region_id, owner_uuid, world, x, y, z, yaw, pitch, level, collecting_enabled, xp_buffer, created_at, updated_at, id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            bindFarmer(statement, farmer);
            statement.setString(14, farmer.farmerId());
            statement.executeUpdate();
        }
    }

    private void bindFarmer(PreparedStatement statement, Farmer farmer) throws SQLException {
        LocationSnapshot location = farmer.location();
        statement.setString(1, farmer.regionId());
        statement.setString(2, farmer.ownerUuid().toString());
        statement.setString(3, location.world());
        statement.setDouble(4, location.x());
        statement.setDouble(5, location.y());
        statement.setDouble(6, location.z());
        statement.setFloat(7, location.yaw());
        statement.setFloat(8, location.pitch());
        statement.setInt(9, farmer.level());
        statement.setBoolean(10, farmer.collectingEnabled());
        statement.setLong(11, farmer.xpBuffer());
        statement.setLong(12, farmer.createdAt().toEpochMilli());
        statement.setLong(13, farmer.updatedAt().toEpochMilli());
    }

    private void insertMembers(Connection connection, Farmer farmer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO farmer_members (farmer_id, player_uuid, role, added_at) VALUES (?, ?, ?, ?)"
        )) {
            for (FarmerMember member : farmer.members().values()) {
                statement.setString(1, farmer.farmerId());
                statement.setString(2, member.playerUuid().toString());
                statement.setString(3, member.role().name());
                statement.setLong(4, member.addedAt().toEpochMilli());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertStorage(Connection connection, Farmer farmer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO farmer_storage (farmer_id, material_key, amount) VALUES (?, ?, ?)"
        )) {
            for (Map.Entry<MaterialKey, Long> entry : farmer.storage().snapshot().entrySet()) {
                statement.setString(1, farmer.farmerId());
                statement.setString(2, entry.getKey().toString());
                statement.setLong(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertSettings(Connection connection, Farmer farmer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO farmer_settings (farmer_id, setting_key, setting_value) VALUES (?, ?, ?)"
        )) {
            for (Map.Entry<String, String> entry : farmer.settings().snapshot().entrySet()) {
                statement.setString(1, farmer.farmerId());
                statement.setString(2, entry.getKey());
                statement.setString(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertModules(Connection connection, Farmer farmer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO farmer_modules (farmer_id, module_key, enabled) VALUES (?, ?, ?)"
        )) {
            for (Map.Entry<String, Boolean> entry : farmer.moduleStates().entrySet()) {
                statement.setString(1, farmer.farmerId());
                statement.setString(2, entry.getKey());
                statement.setBoolean(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertProductCollectingStates(Connection connection, Farmer farmer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO farmer_product_states (farmer_id, material_key, collecting_enabled) VALUES (?, ?, ?)"
        )) {
            for (Map.Entry<MaterialKey, Boolean> entry : farmer.productCollectingStates().entrySet()) {
                statement.setString(1, farmer.farmerId());
                statement.setString(2, entry.getKey().toString());
                statement.setBoolean(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
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

    private int deleteFarmersByRegionId(Connection connection, String regionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM farmers WHERE region_id = ?")) {
            statement.setString(1, regionId);
            return statement.executeUpdate();
        }
    }

    private void deleteChildRows(Connection connection, String farmerId, List<String> tables) throws SQLException {
        for (String table : tables) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE farmer_id = ?")) {
                statement.setString(1, farmerId);
                statement.executeUpdate();
            }
        }
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
}
