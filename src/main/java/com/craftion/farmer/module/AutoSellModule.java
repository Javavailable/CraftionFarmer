package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.economy.EconomyProvider;
import com.craftion.farmer.economy.EconomyProviderManager;
import com.craftion.farmer.economy.StorageTransactionService;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.hook.region.RegionMemberInfo;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.scheduler.ScheduledTaskHandle;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import java.util.Optional;
import org.bukkit.Bukkit;

public final class AutoSellModule implements FarmerModule {

    public static final String KEY = "auto-sell";

    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final FarmerCache farmerCache;
    private final ModuleStateService moduleStateService;
    private final RegionProviderManager regionProviderManager;
    private final EconomyProviderManager economyProviderManager;
    private final StorageTransactionService storageTransactionService;
    private ScheduledTaskHandle task = ScheduledTaskHandle.cancelled();

    public AutoSellModule(
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        FarmerCache farmerCache,
        ModuleStateService moduleStateService,
        RegionProviderManager regionProviderManager,
        EconomyProviderManager economyProviderManager,
        StorageTransactionService storageTransactionService
    ) {
        this.configManager = configManager;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.farmerCache = farmerCache;
        this.moduleStateService = moduleStateService;
        this.regionProviderManager = regionProviderManager;
        this.economyProviderManager = economyProviderManager;
        this.storageTransactionService = storageTransactionService;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String iconMaterial() {
        return "EMERALD";
    }

    @Override
    public void initialize() {
        schedule();
    }

    @Override
    public void reload() {
        shutdown();
        schedule();
    }

    @Override
    public void shutdown() {
        if (this.task != null && !this.task.isCancelled()) {
            this.task.cancel();
        }
        this.task = ScheduledTaskHandle.cancelled();
    }

    private void schedule() {
        if (this.task != null && !this.task.isCancelled()) {
            this.task.cancel();
            this.task = ScheduledTaskHandle.cancelled();
        }
        if (!this.configManager.moduleEnabled(KEY)) {
            this.debugLogger.debug("AutoSell module disabled by config.");
            return;
        }

        long intervalTicks = Math.max(1L, this.configManager.autoSellIntervalSeconds()) * 20L;
        this.task = this.schedulerAdapter.runRepeating(this::run, intervalTicks, intervalTicks);
        this.debugLogger.debug("AutoSell module scheduled every " + this.configManager.autoSellIntervalSeconds() + " seconds.");
    }

    private void run() {
        if (!this.configManager.moduleEnabled(KEY)) {
            return;
        }

        EconomyProvider economyProvider = this.economyProviderManager.provider();
        if (economyProvider == null || !economyProvider.isAvailable()) {
            return;
        }

        RegionProvider regionProvider = this.regionProviderManager.provider();
        if (regionProvider == null || !regionProvider.isAvailable()) {
            return;
        }

        for (Farmer farmer : this.farmerCache.snapshot().values()) {
            if (!this.moduleStateService.state(farmer, this) || !isValidFarmer(regionProvider, farmer)) {
                continue;
            }
            this.storageTransactionService.sellAll(farmer, farmer.ownerUuid(), Bukkit.getOfflinePlayer(farmer.ownerUuid()));
        }
    }

    private boolean isValidFarmer(RegionProvider regionProvider, Farmer farmer) {
        if (farmer == null || farmer.regionId() == null || farmer.regionId().isBlank()) {
            return false;
        }

        Optional<RegionMemberInfo> owner = regionProvider.owner(farmer.regionId());
        return owner.isPresent() && farmer.ownerUuid().equals(owner.get().playerUuid());
    }
}
