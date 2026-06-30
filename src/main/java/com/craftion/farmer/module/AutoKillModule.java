package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoKillModule implements FarmerModule, Listener {

    public static final String KEY = "auto-kill";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DebugLogger debugLogger;
    private final FarmerCache farmerCache;
    private final ModuleStateService moduleStateService;
    private final RegionProviderManager regionProviderManager;
    private final AutoKillDeathTracker deathTracker;
    private boolean registered;

    public AutoKillModule(
        JavaPlugin plugin,
        ConfigManager configManager,
        DebugLogger debugLogger,
        FarmerCache farmerCache,
        ModuleStateService moduleStateService,
        RegionProviderManager regionProviderManager,
        AutoKillDeathTracker deathTracker
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debugLogger = debugLogger;
        this.farmerCache = farmerCache;
        this.moduleStateService = moduleStateService;
        this.regionProviderManager = regionProviderManager;
        this.deathTracker = deathTracker;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String iconMaterial() {
        return "IRON_SWORD";
    }

    @Override
    public void initialize() {
        registerIfEnabled();
    }

    @Override
    public void reload() {
        shutdown();
        registerIfEnabled();
    }

    @Override
    public void shutdown() {
        if (!this.registered) {
            return;
        }

        HandlerList.unregisterAll(this);
        this.registered = false;
        this.debugLogger.debug("AutoKill listener unregistered.");
    }

    private void registerIfEnabled() {
        if (this.registered) {
            return;
        }
        if (!this.configManager.moduleEnabled(KEY)) {
            debugSkip("module disabled", null, null);
            return;
        }

        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        this.registered = true;
        this.debugLogger.debug("AutoKill listener registered.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!this.configManager.autoKillKillSpawnerMobs()) {
            return;
        }
        
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        
        handleSpawn(livingEntity, event.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Only handle natural spawns if enabled
        if (!this.configManager.autoKillKillNaturalMobs()) {
            return;
        }
        
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            // Already handled by SpawnerSpawnEvent if enabled
            return;
        }

        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            // Only NATURAL spawns are considered natural
            return;
        }

        handleSpawn(event.getEntity(), event.getLocation());
    }

    private void handleSpawn(LivingEntity entity, Location location) {
        if (entity == null || entity.isDead()) {
            return;
        }

        if (isUnsafeEntityType(entity.getType())) {
            debugSkip("unsafe entity type", location, entity.getType().name());
            return;
        }

        if (entity.customName() != null) {
            debugSkip("entity has custom name", location, entity.getType().name());
            return;
        }

        if (entity.hasMetadata("NPC") || entity.hasMetadata("CitizensNPC")) {
            debugSkip("entity is NPC", location, entity.getType().name());
            return;
        }

        if (!this.configManager.moduleEnabled(KEY)) {
            debugSkip("module disabled", location, entity.getType().name());
            return;
        }

        // Filtering
        String mode = this.configManager.autoKillMode();
        List<String> mobs = this.configManager.autoKillMobs();
        
        boolean contains = false;
        for (String mobName : mobs) {
            if (mobName.equalsIgnoreCase(entity.getType().name())) {
                contains = true;
                break;
            }
        }
        
        boolean whitelist = !"BLACKLIST".equalsIgnoreCase(mode);
        if (whitelist && !contains) {
            debugSkip("not in whitelist", location, entity.getType().name());
            return;
        }
        if (!whitelist && contains) {
            debugSkip("in blacklist", location, entity.getType().name());
            return;
        }

        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            debugSkip("no region", location, entity.getType().name());
            return;
        }

        World world = location.getWorld();
        if (world == null || !provider.isSkyblockWorld(world)) {
            debugSkip("not skyblock world", location, entity.getType().name());
            return;
        }

        Optional<String> regionId = provider.regionIdAt(location);
        if (regionId.isEmpty()) {
            debugSkip("no region", location, entity.getType().name());
            return;
        }

        Optional<Farmer> farmer = this.farmerCache.getByRegionId(regionId.get());
        if (farmer.isEmpty()) {
            debugSkip("no farmer", location, entity.getType().name());
            return;
        }

        Farmer value = farmer.get();
        if (!value.collectingEnabled()) {
            debugSkip("farmer collection disabled", location, entity.getType().name());
            return;
        }

        if (!this.moduleStateService.state(value, this)) {
            debugSkip("module state disabled", location, entity.getType().name());
            return;
        }

        this.deathTracker.mark(entity, value);
        try {
            entity.damage(Double.MAX_VALUE);
        } finally {
            this.deathTracker.unmark(entity);
        }
    }

    private void debugSkip(String reason, Location location, String detail) {
        StringBuilder message = new StringBuilder("AutoKill skipped: ").append(reason);
        if (detail != null && !detail.isBlank()) {
            message.append(" entity=").append(detail);
        }
        if (location != null) {
            World world = location.getWorld();
            message.append(" location=")
                .append(world == null ? "unknown" : world.getName())
                .append(':')
                .append(location.getBlockX())
                .append(',')
                .append(location.getBlockY())
                .append(',')
                .append(location.getBlockZ());
        }
        this.debugLogger.debug(message.toString());
    }

    private boolean isUnsafeEntityType(EntityType type) {
        return type == EntityType.PLAYER
            || type == EntityType.ARMOR_STAND
            || type == EntityType.VILLAGER
            || type == EntityType.WANDERING_TRADER;
    }
}
