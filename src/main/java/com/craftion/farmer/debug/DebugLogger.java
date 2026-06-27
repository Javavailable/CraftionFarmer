package com.craftion.farmer.debug;

import com.craftion.farmer.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class DebugLogger {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public DebugLogger(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void debug(String message) {
        if (this.configManager.isDebugEnabled()) {
            this.plugin.getLogger().info("[Debug] " + message);
        }
    }
}
