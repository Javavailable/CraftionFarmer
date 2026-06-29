package com.craftion.farmer.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageManager {

    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private FileConfiguration messages;
    private FileConfiguration defaultMessages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(this.plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            this.plugin.saveResource(FILE_NAME, false);
        }

        this.messages = YamlConfiguration.loadConfiguration(file);
        this.defaultMessages = loadDefaultMessages();
    }

    public String string(String path) {
        return messageString(path, null);
    }

    public List<String> stringList(String path) {
        return messageList(path, List.of());
    }

    public String messageString(String path, String fallback) {
        if (path == null || path.isBlank()) {
            return fallback;
        }

        String configured = stringFrom(this.messages, path);
        if (configured != null) {
            return configured;
        }

        String bundled = stringFrom(this.defaultMessages, path);
        return bundled == null ? fallback : bundled;
    }

    public List<String> messageList(String path, List<String> fallbackList) {
        if (path == null || path.isBlank()) {
            return fallbackList == null ? List.of() : List.copyOf(fallbackList);
        }

        List<String> configured = listFrom(this.messages, path);
        if (configured != null) {
            return configured;
        }

        List<String> bundled = listFrom(this.defaultMessages, path);
        if (bundled != null) {
            return bundled;
        }
        return fallbackList == null ? List.of() : List.copyOf(fallbackList);
    }

    private FileConfiguration loadDefaultMessages() {
        InputStream stream = this.plugin.getResource(FILE_NAME);
        if (stream == null) {
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Default messages.yml could not be loaded: " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private String stringFrom(FileConfiguration source, String path) {
        if (source == null || !source.contains(path)) {
            return null;
        }
        return source.getString(path);
    }

    private List<String> listFrom(FileConfiguration source, String path) {
        if (source == null || !source.contains(path)) {
            return null;
        }
        if (source.isList(path)) {
            return source.getStringList(path);
        }
        String singleValue = source.getString(path);
        return singleValue == null ? List.of() : List.of(singleValue);
    }
}
