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

    public String databaseType() {
        return this.config.getString("database.type", "SQLITE");
    }

    public String sqliteFile() {
        return this.config.getString("database.sqlite.file", "craftionfarmer.db");
    }

    public String mysqlHost() {
        return this.config.getString("database.mysql.host", "localhost");
    }

    public int mysqlPort() {
        return this.config.getInt("database.mysql.port", 3306);
    }

    public String mysqlDatabase() {
        return this.config.getString("database.mysql.database", "craftionfarmer");
    }

    public String mysqlUsername() {
        return this.config.getString("database.mysql.username", "root");
    }

    public String mysqlPassword() {
        return this.config.getString("database.mysql.password", "");
    }

    public int mysqlPoolSize() {
        return this.config.getInt("database.mysql.pool-size", 10);
    }
}
