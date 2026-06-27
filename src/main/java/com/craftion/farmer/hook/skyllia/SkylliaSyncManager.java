package com.craftion.farmer.hook.skyllia;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkylliaSyncManager {

    private static final String SKYLLIA_PLUGIN_NAME = "Skyllia";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FarmerReconcileService reconcileService;
    private final DebugLogger debugLogger;
    private Listener listener;

    public SkylliaSyncManager(
        JavaPlugin plugin,
        ConfigManager configManager,
        FarmerReconcileService reconcileService,
        DebugLogger debugLogger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.reconcileService = reconcileService;
        this.debugLogger = debugLogger;
    }

    public void initialize() {
        reload();
    }

    public void reload() {
        unregister();
        if (!this.configManager.isSkylliaSyncEnabled()) {
            this.debugLogger.debug("Skyllia sync listener disabled by config.");
            return;
        }

        Plugin skyllia = this.plugin.getServer().getPluginManager().getPlugin(SKYLLIA_PLUGIN_NAME);
        if (skyllia == null || !skyllia.isEnabled()) {
            this.debugLogger.debug("Skyllia sync listener not registered because Skyllia is not active.");
            return;
        }

        try {
            this.listener = new SkylliaSyncListener(this.plugin, this.configManager, this.reconcileService, this.debugLogger);
            this.plugin.getServer().getPluginManager().registerEvents(this.listener, this.plugin);
            this.debugLogger.debug("Skyllia sync listener registered.");
        } catch (LinkageError error) {
            this.listener = null;
            this.plugin.getLogger().warning("Skyllia sync listener yuklenemedi: " + error.getClass().getSimpleName());
        }
    }

    public void shutdown() {
        unregister();
    }

    private void unregister() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
    }
}
