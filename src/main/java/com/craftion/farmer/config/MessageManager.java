package com.craftion.farmer.config;

import java.io.File;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageManager {

    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(this.plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            this.plugin.saveResource(FILE_NAME, false);
        }

        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String string(String path) {
        return this.messages.getString(path);
    }

    public List<String> stringList(String path) {
        return this.messages.getStringList(path);
    }
}
