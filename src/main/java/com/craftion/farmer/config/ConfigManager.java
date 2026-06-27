package com.craftion.farmer.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        this.config = this.plugin.getConfig();
    }

    public boolean isDebugEnabled() {
        return this.config.getBoolean("settings.debug", false);
    }

    public String language() {
        return this.config.getString("settings.language", "tr");
    }

    public String mainCommand() {
        return this.config.getString("commands.main-command", "farmer");
    }
}
