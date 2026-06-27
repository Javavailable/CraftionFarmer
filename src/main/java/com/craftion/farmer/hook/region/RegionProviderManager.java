package com.craftion.farmer.hook.region;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionProviderManager {

    private static final String SKYLLIA_PLUGIN_NAME = "Skyllia";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DebugLogger debugLogger;
    private RegionProvider provider = new NoRegionProvider();

    public RegionProviderManager(JavaPlugin plugin, ConfigManager configManager, DebugLogger debugLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debugLogger = debugLogger;
    }

    public void initialize() {
        this.provider = createProvider();
        this.debugLogger.debug("Region provider: " + this.provider.type());
    }

    public void reload() {
        initialize();
    }

    public RegionProvider provider() {
        return this.provider;
    }

    private RegionProvider createProvider() {
        RegionProviderType type = RegionProviderType.from(this.configManager.regionProvider());
        if (type == RegionProviderType.NONE) {
            this.plugin.getLogger().info("Region provider disabled by config.");
            return new NoRegionProvider();
        }

        if (type == RegionProviderType.SKYLLIA) {
            Plugin skyllia = this.plugin.getServer().getPluginManager().getPlugin(SKYLLIA_PLUGIN_NAME);
            if (skyllia == null || !skyllia.isEnabled()) {
                this.plugin.getLogger().warning("Skyllia region provider secildi fakat Skyllia plugin aktif degil. Region provider devre disi birakildi.");
                return new NoRegionProvider();
            }

            try {
                return new SkylliaRegionProvider(
                    this.configManager.allowTrustedPlayers(),
                    this.configManager.requireSkyblockWorld(),
                    this.configManager.syncMembersOnOpen()
                );
            } catch (LinkageError error) {
                this.plugin.getLogger().warning("Skyllia API yuklenemedi. Region provider devre disi birakildi: " + error.getClass().getSimpleName());
                return new NoRegionProvider();
            }
        }

        return new NoRegionProvider();
    }
}
