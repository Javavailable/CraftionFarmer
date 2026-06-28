package com.craftion.farmer.config;

import org.bukkit.configuration.ConfigurationSection;
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

    public String regionProvider() {
        return this.config.getString("hooks.region.provider", "SKYLLIA");
    }

    public boolean allowTrustedPlayers() {
        return this.config.getBoolean("hooks.region.skyllia.allow-trusted-players", true);
    }

    public boolean requireSkyblockWorld() {
        return this.config.getBoolean("hooks.region.skyllia.require-skyblock-world", true);
    }

    public boolean syncMembersOnOpen() {
        return this.config.getBoolean("hooks.region.skyllia.sync-members-on-open", true);
    }

    public boolean isSkylliaSyncEnabled() {
        return this.config.getBoolean("hooks.skyllia-sync.enabled", true);
    }

    public boolean removeFarmerOnIslandDelete() {
        return this.config.getBoolean("hooks.skyllia-sync.remove-farmer-on-island-delete", true);
    }

    public boolean reconcileOnCommand() {
        return this.config.getBoolean("hooks.skyllia-sync.reconcile-on-command", true);
    }

    public boolean reconcileOnMenuOpen() {
        return this.config.getBoolean("hooks.skyllia-sync.reconcile-on-menu-open", true);
    }

    public String visualProvider() {
        return this.config.getString("hooks.visual.provider", "FANCY_NPCS");
    }

    public String fancyNpcsApiMode() {
        return this.config.getString("hooks.visual.fancynpcs.api-mode", "AUTO");
    }

    public String fancyNpcsIdPrefix() {
        return this.config.getString("hooks.visual.fancynpcs.id-prefix", "craftion-farmer-");
    }

    public boolean saveFancyNpcsToFile() {
        return this.config.getBoolean("hooks.visual.fancynpcs.save-npcs-to-file", false);
    }

    public boolean removeFancyNpcOnFarmerDelete() {
        return this.config.getBoolean("hooks.visual.fancynpcs.remove-on-farmer-delete", true);
    }

    public boolean isNpcEnabled() {
        return this.config.getBoolean("npc.enabled", true);
    }

    public String npcType() {
        return this.config.getString("npc.type", "VILLAGER");
    }

    public String npcName() {
        return this.config.getString("npc.name", "<#38BDF8>ᴄʀᴀғᴛɪᴏɴ ᴄɪғᴛᴄɪ");
    }

    public boolean npcGlowing() {
        return this.config.getBoolean("npc.glowing", true);
    }

    public String npcGlowingColor() {
        return this.config.getString("npc.glowing-color", "AQUA");
    }

    public boolean npcTurnToPlayer() {
        return this.config.getBoolean("npc.turn-to-player", true);
    }

    public double npcInteractionCooldown() {
        return this.config.getDouble("npc.interaction-cooldown", 0.5D);
    }

    public int npcVisibilityDistance() {
        return this.config.getInt("npc.visibility-distance", 48);
    }

    public ConfigurationSection guiMenu(String menuId) {
        if (menuId == null || menuId.isBlank()) {
            return null;
        }
        return this.config.getConfigurationSection("gui.menus." + menuId);
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
