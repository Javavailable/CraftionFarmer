package com.craftion.farmer.hook.placeholder;

import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.module.ModuleManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaceholderProviderManager {

    private static final String PLACEHOLDER_API_PLUGIN = "PlaceholderAPI";

    private final JavaPlugin plugin;
    private final DebugLogger debugLogger;
    private final FarmerCache farmerCache;
    private final ModuleManager moduleManager;
    private PlaceholderProvider provider = new NoPlaceholderProvider();

    public PlaceholderProviderManager(JavaPlugin plugin, DebugLogger debugLogger, FarmerCache farmerCache, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.debugLogger = debugLogger;
        this.farmerCache = farmerCache;
        this.moduleManager = moduleManager;
    }

    public void initialize() {
        this.provider = createProvider();
        this.provider.initialize();
        this.debugLogger.debug("Placeholder provider: " + this.provider.name());
    }

    public void reload() {
        this.provider.shutdown();
        initialize();
    }

    public void shutdown() {
        this.provider.shutdown();
        this.provider = new NoPlaceholderProvider();
    }

    public PlaceholderProvider provider() {
        return this.provider;
    }

    private PlaceholderProvider createProvider() {
        Plugin placeholderApi = this.plugin.getServer().getPluginManager().getPlugin(PLACEHOLDER_API_PLUGIN);
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return new NoPlaceholderProvider();
        }
        return new PapiPlaceholderProvider(this.plugin, this.farmerCache, this.moduleManager);
    }
}
