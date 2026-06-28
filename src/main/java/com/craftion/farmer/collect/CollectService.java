package com.craftion.farmer.collect;

import com.craftion.farmer.collect.event.FarmerStorageFullEvent;
import com.craftion.farmer.collect.listener.CollectItemSpawnListener;
import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.farmer.StorageAddResult;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.module.ModuleManager;
import com.craftion.farmer.scheduler.ScheduledTaskHandle;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class CollectService {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final RegionProviderManager regionProviderManager;
    private final FarmerCache farmerCache;
    private final FarmerPersistenceService farmerPersistenceService;
    private final ModuleManager moduleManager;
    private final Set<String> dirtyFarmerIds = ConcurrentHashMap.newKeySet();
    private Listener listener;
    private ScheduledTaskHandle flushTask = ScheduledTaskHandle.cancelled();

    public CollectService(
        JavaPlugin plugin,
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        RegionProviderManager regionProviderManager,
        FarmerCache farmerCache,
        FarmerPersistenceService farmerPersistenceService,
        ModuleManager moduleManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.regionProviderManager = regionProviderManager;
        this.farmerCache = farmerCache;
        this.farmerPersistenceService = farmerPersistenceService;
        this.moduleManager = moduleManager;
    }

    public void initialize() {
        reload();
    }

    public void reload() {
        unregister();
        if (!this.configManager.isCollectEnabled()) {
            this.debugLogger.debug("Collect listener disabled by config.");
            return;
        }

        this.listener = new CollectItemSpawnListener(this);
        this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
        this.debugLogger.debug("Collect listener registered.");
    }

    public void shutdown() {
        unregister();
        flushNowBlocking();
    }

    public CollectResult collect(CollectContext context) {
        if (context == null || context.item() == null || context.itemStack() == null) {
            return CollectResult.skipped(CollectResult.Status.INVALID_ITEM, context);
        }
        if (!this.configManager.isCollectEnabled()) {
            return CollectResult.skipped(CollectResult.Status.DISABLED, context);
        }

        Item item = context.item();
        ItemStack itemStack = context.itemStack();
        if (itemStack.getAmount() <= 0 || itemStack.getType().isAir()) {
            return CollectResult.skipped(CollectResult.Status.EMPTY_ITEM, context);
        }
        if (this.configManager.ignorePlayerDrops() && item.getThrower() != null) {
            return CollectResult.skipped(CollectResult.Status.PLAYER_DROP, context);
        }
        if (this.configManager.ignoreItemsWithMeta() && itemStack.hasItemMeta()) {
            return CollectResult.skipped(CollectResult.Status.ITEM_HAS_META, context);
        }

        Material material = itemStack.getType();
        if (!this.configManager.allowedCollectMaterials().contains(material)) {
            return CollectResult.skipped(CollectResult.Status.MATERIAL_NOT_ALLOWED, context);
        }

        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            return CollectResult.skipped(CollectResult.Status.NO_REGION, context);
        }

        World world = context.location().getWorld();
        if (world == null || !provider.isSkyblockWorld(world)) {
            return CollectResult.skipped(CollectResult.Status.NOT_SKYBLOCK_WORLD, context);
        }

        Optional<String> regionId = provider.regionIdAt(context.location());
        if (regionId.isEmpty()) {
            return CollectResult.skipped(CollectResult.Status.NO_REGION, context);
        }

        Optional<Farmer> farmer = this.farmerCache.getByRegionId(regionId.get());
        if (farmer.isEmpty()) {
            return CollectResult.skipped(CollectResult.Status.NO_FARMER, context, regionId.get());
        }

        Farmer value = farmer.get();
        if (!value.collectingEnabled()) {
            return CollectResult.skipped(CollectResult.Status.FARMER_DISABLED, context, regionId.get());
        }

        MaterialKey materialKey = MaterialKey.of(material.name());
        if (!value.productCollectingEnabled(materialKey)) {
            return CollectResult.skipped(CollectResult.Status.PRODUCT_DISABLED, context, regionId.get());
        }

        long requestedAmount = itemStack.getAmount();
        long capacity = capacityFor(value, materialKey);
        StorageAddResult addResult = value.addStorageAmount(materialKey, requestedAmount, capacity);
        if (!addResult.changedStorage()) {
            callStorageFull(value, materialKey, requestedAmount, addResult.storageAmount(), capacity);
            return CollectResult.collected(
                CollectResult.Status.STORAGE_FULL,
                context,
                value.farmerId(),
                value.regionId(),
                materialKey,
                0L,
                addResult.remainingAmount(),
                addResult.storageAmount(),
                capacity
            );
        }

        markDirty(value.farmerId());
        this.moduleManager.recordCollect(value, materialKey, addResult.collectedAmount());

        if (addResult.remainingAmount() > 0L) {
            callStorageFull(value, materialKey, requestedAmount, addResult.storageAmount(), capacity);
        }

        return CollectResult.collected(
            addResult.remainingAmount() > 0L ? CollectResult.Status.PARTIAL : CollectResult.Status.COLLECTED,
            context,
            value.farmerId(),
            value.regionId(),
            materialKey,
            addResult.collectedAmount(),
            addResult.remainingAmount(),
            addResult.storageAmount(),
            capacity
        );
    }

    public void flushNow() {
        List<Farmer> farmers = dirtyFarmers();
        if (farmers.isEmpty()) {
            return;
        }

        this.farmerPersistenceService.saveAll(farmers).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                for (Farmer farmer : farmers) {
                    this.dirtyFarmerIds.add(farmer.farmerId());
                }
                this.plugin.getLogger().warning("Collect storage kaydedilemedi: " + readableMessage(throwable));
                scheduleFlush();
            }
        });
    }

    private void markDirty(String farmerId) {
        if (farmerId == null || farmerId.isBlank()) {
            return;
        }
        this.dirtyFarmerIds.add(farmerId);
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (this.flushTask != null && !this.flushTask.isCancelled()) {
            return;
        }
        this.flushTask = this.schedulerAdapter.runDelayed(() -> {
            this.flushTask = ScheduledTaskHandle.cancelled();
            flushNow();
        }, this.configManager.collectSaveDelayTicks());
    }

    private List<Farmer> dirtyFarmers() {
        Set<String> ids = Set.copyOf(this.dirtyFarmerIds);
        this.dirtyFarmerIds.removeAll(ids);
        List<Farmer> farmers = new ArrayList<>();
        for (String farmerId : ids) {
            this.farmerCache.get(farmerId).ifPresent(farmers::add);
        }
        return farmers;
    }

    private void flushNowBlocking() {
        if (this.flushTask != null && !this.flushTask.isCancelled()) {
            this.flushTask.cancel();
        }
        List<Farmer> farmers = dirtyFarmers();
        if (farmers.isEmpty()) {
            return;
        }
        try {
            this.farmerPersistenceService.saveAllBlocking(farmers);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Collect storage kapatilirken kaydedilemedi: " + readableMessage(exception));
        }
    }

    private long capacityFor(Farmer farmer, MaterialKey materialKey) {
        return this.configManager.maxStoragePerItem();
    }

    private void callStorageFull(Farmer farmer, MaterialKey materialKey, long requestedAmount, long storageAmount, long capacity) {
        this.plugin.getServer().getPluginManager().callEvent(new FarmerStorageFullEvent(farmer, materialKey, requestedAmount, storageAmount, capacity));
    }

    private void unregister() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }
}
