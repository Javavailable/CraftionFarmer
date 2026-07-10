package com.craftion.farmer.collect;

import com.craftion.farmer.collect.event.FarmerStorageFullEvent;
import com.craftion.farmer.collect.listener.CollectItemSpawnListener;
import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.module.ModuleManager;
import com.craftion.farmer.scheduler.ScheduledTaskHandle;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class CollectService {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final FarmerCache farmerCache;
    private final FarmerPersistenceService farmerPersistenceService;
    private final FarmerDepositService depositService;
    private final Runnable flushScheduler;
    private final CollectRecorder collectRecorder;
    private final StorageFullDispatcher storageFullDispatcher;
    private final FailureLogger failureLogger;
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
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.schedulerAdapter = Objects.requireNonNull(schedulerAdapter, "schedulerAdapter");
        this.debugLogger = Objects.requireNonNull(debugLogger, "debugLogger");
        this.farmerCache = Objects.requireNonNull(farmerCache, "farmerCache");
        this.farmerPersistenceService = Objects.requireNonNull(farmerPersistenceService, "farmerPersistenceService");
        this.depositService = new FarmerDepositService(configManager, regionProviderManager, farmerCache);
        this.flushScheduler = this::scheduleFlush;
        this.collectRecorder = Objects.requireNonNull(moduleManager, "moduleManager")::recordCollect;
        this.storageFullDispatcher = this::callStorageFull;
        this.failureLogger = message -> this.plugin.getLogger().warning(message);
    }

    CollectService(
        FarmerDepositService depositService,
        Runnable flushScheduler,
        CollectRecorder collectRecorder,
        StorageFullDispatcher storageFullDispatcher,
        FailureLogger failureLogger
    ) {
        this.plugin = null;
        this.configManager = null;
        this.schedulerAdapter = null;
        this.debugLogger = null;
        this.farmerCache = null;
        this.farmerPersistenceService = null;
        this.depositService = Objects.requireNonNull(depositService, "depositService");
        this.flushScheduler = Objects.requireNonNull(flushScheduler, "flushScheduler");
        this.collectRecorder = Objects.requireNonNull(collectRecorder, "collectRecorder");
        this.storageFullDispatcher = Objects.requireNonNull(storageFullDispatcher, "storageFullDispatcher");
        this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
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
        return collect(context, null);
    }

    public CollectResult collect(CollectContext context, CollectCommitTracker commitTracker) {
        FarmerDepositOutcome outcome = this.depositService.deposit(context);
        CollectResult result = outcome.result();
        if (commitTracker != null) {
            commitTracker.record(result);
        }
        Farmer farmer = outcome.farmer();

        if (result.collectedAmount() > 0L && farmer != null) {
            this.dirtyFarmerIds.add(farmer.farmerId());
            runPostCommitEffect("dirty save scheduling", this.flushScheduler);
            runPostCommitEffect(
                "Production Calc notification",
                () -> this.collectRecorder.record(farmer, result.materialKey(), result.collectedAmount())
            );
        }

        if ((result.status() == CollectResult.Status.PARTIAL || result.status() == CollectResult.Status.STORAGE_FULL)
            && farmer != null && result.materialKey() != null) {
            runPostCommitEffect(
                "storage-full event dispatch",
                () -> this.storageFullDispatcher.dispatch(
                    farmer,
                    result.materialKey(),
                    result.requestedAmount(),
                    result.storageAmount(),
                    result.capacity()
                )
            );
        }
        return result;
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

    boolean isDirtyFarmer(String farmerId) {
        return farmerId != null && this.dirtyFarmerIds.contains(farmerId);
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

    private void callStorageFull(Farmer farmer, MaterialKey materialKey, long requestedAmount, long storageAmount, long capacity) {
        this.plugin.getServer().getPluginManager().callEvent(
            new FarmerStorageFullEvent(farmer, materialKey, requestedAmount, storageAmount, capacity)
        );
    }

    private void unregister() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
    }

    private void runPostCommitEffect(String effectName, Runnable effect) {
        try {
            effect.run();
        } catch (RuntimeException | LinkageError failure) {
            logPostCommitFailure(effectName, failure);
        }
    }

    private void logPostCommitFailure(String effectName, Throwable failure) {
        try {
            this.failureLogger.log("Collect " + effectName + " failed: " + readableMessage(failure));
        } catch (RuntimeException | LinkageError ignored) {
            // Logging is best-effort and must not replay or invalidate an accepted deposit.
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

    @FunctionalInterface
    interface CollectRecorder {
        void record(Farmer farmer, MaterialKey materialKey, long amount);
    }

    @FunctionalInterface
    interface StorageFullDispatcher {
        void dispatch(Farmer farmer, MaterialKey materialKey, long requestedAmount, long storageAmount, long capacity);
    }

    @FunctionalInterface
    interface FailureLogger {
        void log(String message);
    }
}
