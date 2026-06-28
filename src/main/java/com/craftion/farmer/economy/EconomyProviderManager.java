package com.craftion.farmer.economy;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import java.util.Locale;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyProviderManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DebugLogger debugLogger;
    private volatile EconomyProvider provider = new NoEconomyProvider("Economy provider is not initialized.");

    public EconomyProviderManager(JavaPlugin plugin, ConfigManager configManager, DebugLogger debugLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debugLogger = debugLogger;
    }

    public void initialize() {
        reload();
    }

    public void reload() {
        this.provider = createProvider();
        this.provider.refresh();
        this.debugLogger.debug("Economy provider selected: " + this.provider.name());
    }

    public EconomyProvider provider() {
        return this.provider;
    }

    private EconomyProvider createProvider() {
        String configuredProvider = this.configManager.economyProvider();
        String normalizedProvider = configuredProvider == null ? "VAULT" : configuredProvider.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedProvider) {
            case "VAULT" -> new VaultEconomyProvider(this.plugin, this.debugLogger);
            case "NONE", "DISABLED", "OFF" -> new NoEconomyProvider("Economy provider is disabled.");
            default -> new NoEconomyProvider("Unsupported economy provider: " + normalizedProvider);
        };
    }
}
