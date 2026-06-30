package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.FarmerSaveRetryService;
import java.util.Optional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class XpCollectorModule implements FarmerModule, Listener {

    public static final String KEY = "xp-collector";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DebugLogger debugLogger;
    private final FarmerPersistenceService farmerPersistenceService;
    private final FarmerSaveRetryService farmerSaveRetryService;
    private final ModuleStateService moduleStateService;
    private final AutoKillDeathTracker deathTracker;
    private boolean registered;

    public XpCollectorModule(
        JavaPlugin plugin,
        ConfigManager configManager,
        DebugLogger debugLogger,
        FarmerPersistenceService farmerPersistenceService,
        FarmerSaveRetryService farmerSaveRetryService,
        ModuleStateService moduleStateService,
        AutoKillDeathTracker deathTracker
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debugLogger = debugLogger;
        this.farmerPersistenceService = farmerPersistenceService;
        this.farmerSaveRetryService = farmerSaveRetryService;
        this.moduleStateService = moduleStateService;
        this.deathTracker = deathTracker;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String iconMaterial() {
        return "SCULK";
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
        this.debugLogger.debug("XP Collector listener unregistered.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        Optional<AutoKillDeathContext> context = this.deathTracker.context(event.getEntity());
        if (context.isEmpty()) {
            return;
        }

        Farmer farmer = context.get().farmer();
        if (!farmer.collectingEnabled() || !this.moduleStateService.state(farmer, this)) {
            return;
        }

        int droppedXp = Math.max(0, event.getDroppedExp());
        long resolvedXp = droppedXp > 0 ? droppedXp : this.configManager.xpCollectorXp(context.get().entityType());
        if (resolvedXp <= 0L) {
            return;
        }

        long addedXp = farmer.addXp(resolvedXp);
        if (addedXp <= 0L) {
            return;
        }

        if (droppedXp > 0) {
            long remainingXp = Math.max(0L, droppedXp - addedXp);
            event.setDroppedExp((int) Math.min(Integer.MAX_VALUE, remainingXp));
        } else {
            event.setDroppedExp(0);
        }

        persistCapturedXp(farmer, addedXp, context.get(), droppedXp, resolvedXp);
    }

    private void registerIfEnabled() {
        if (this.registered) {
            return;
        }
        if (!this.configManager.moduleEnabled(KEY)) {
            this.debugLogger.debug("XP Collector listener disabled by config.");
            return;
        }

        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        this.registered = true;
        this.debugLogger.debug("XP Collector listener registered.");
    }

    private void persistCapturedXp(Farmer farmer, long addedXp, AutoKillDeathContext context, int droppedXp, long resolvedXp) {
        this.debugLogger.debug(
            "XP Collector captured: farmer=" + farmer.farmerId()
                + " region=" + context.regionId()
                + " entity=" + context.entityType()
                + " dropped=" + droppedXp
                + " resolved=" + resolvedXp
                + " added=" + addedXp
        );
        this.farmerPersistenceService.save(farmer).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("XP buffer kaydedilemedi: " + readableMessage(throwable));
                this.farmerSaveRetryService.markDirty(farmer, "xp capture");
            }
        });
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
