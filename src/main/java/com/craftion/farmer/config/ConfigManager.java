package com.craftion.farmer.config;

import com.craftion.farmer.farmer.FarmerRole;
import com.craftion.farmer.farmer.MaterialKey;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import org.bukkit.Material;
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

    public boolean isCollectEnabled() {
        return this.config.getBoolean("collect.enabled", true);
    }

    public boolean ignorePlayerDrops() {
        return this.config.getBoolean("collect.ignore-player-drops", true);
    }

    public boolean ignoreItemsWithMeta() {
        return this.config.getBoolean("collect.ignore-items-with-meta", true);
    }

    public long maxStoragePerItem() {
        return this.config.getLong("collect.max-storage-per-item", -1L);
    }

    public long collectSaveDelayTicks() {
        return Math.max(1L, this.config.getLong("collect.save-delay-ticks", 40L));
    }

    public Set<Material> allowedCollectMaterials() {
        Set<Material> materials = new LinkedHashSet<>();
        for (String materialName : this.config.getStringList("collect.allowed-materials")) {
            Material material = Material.matchMaterial(materialName);
            if (material != null && !material.isAir()) {
                materials.add(material);
            }
        }
        return Set.copyOf(materials);
    }

    public boolean allowMemberWithdraw() {
        return this.config.getBoolean("storage.withdraw.allow-members", true);
    }

    public String economyProvider() {
        return this.config.getString("economy.provider", "VAULT");
    }

    public boolean economyTaxEnabled() {
        return this.config.getBoolean("economy.tax.enabled", false);
    }

    public double economyTaxRate() {
        double rate = this.config.getDouble("economy.tax.rate", 0.0D);
        if (!Double.isFinite(rate)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, rate));
    }

    public OptionalDouble price(MaterialKey materialKey) {
        if (materialKey == null) {
            return OptionalDouble.empty();
        }
        return price(materialKey.toString());
    }

    public OptionalDouble price(String materialKey) {
        String normalizedKey = normalizeGuiKey(materialKey);
        double price = this.config.getDouble("prices." + normalizedKey, -1.0D);
        if (!Double.isFinite(price) || price <= 0.0D) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(price);
    }

    public boolean moduleEnabled(String moduleKey) {
        String normalizedKey = normalizeGuiKey(moduleKey);
        return this.config.getBoolean("modules." + normalizedKey + ".enabled", false);
    }

    public boolean moduleDefaultState(String moduleKey) {
        String normalizedKey = normalizeGuiKey(moduleKey);
        return this.config.getBoolean("modules." + normalizedKey + ".default-state", false);
    }

    public long autoSellIntervalSeconds() {
        return Math.max(5L, this.config.getLong("modules.auto-sell.interval-seconds", 60L));
    }

    public String guiModuleDescription(String moduleKey) {
        String normalizedKey = normalizeGuiKey(moduleKey);
        return this.config.getString("gui.module-descriptions." + normalizedKey, "ᴍᴏᴅᴜʟ ʙɪʟɢɪsɪ");
    }

    public ConfigurationSection guiMenu(String menuId) {
        if (menuId == null || menuId.isBlank()) {
            return null;
        }
        return this.config.getConfigurationSection("gui.menus." + menuId);
    }

    public String guiCollectingState(boolean enabled) {
        return guiLabel("collecting." + (enabled ? "enabled" : "disabled"), enabled ? "ᴀᴋᴛɪғ" : "ᴅᴜʀᴀᴋʟᴀᴛɪʟᴅɪ");
    }

    public String guiModuleState(boolean enabled) {
        return guiLabel("modules." + (enabled ? "enabled" : "disabled"), enabled ? "ᴀᴋᴛɪғ" : "ᴘᴀsɪғ");
    }

    public String guiRoleName(FarmerRole role) {
        FarmerRole safeRole = role == null ? FarmerRole.VIEWER : role;
        String fallback = switch (safeRole) {
            case OWNER -> "ᴀᴅᴀ sᴀʜɪʙɪ";
            case MANAGER -> "ʏᴏɴᴇᴛɪᴄɪ";
            case MEMBER -> "ᴜʏᴇ";
            case VIEWER -> "ɢᴏʀᴜɴᴛᴜʟᴇʏᴇɴ";
        };
        return guiLabel("roles." + safeRole.name().toLowerCase(Locale.ROOT), fallback);
    }

    public String guiMaterialName(String materialKey) {
        String normalizedKey = normalizeGuiKey(materialKey);
        return this.config.getString("gui.material-names." + normalizedKey, fallbackLabel(normalizedKey));
    }

    public String guiModuleName(String moduleKey) {
        String normalizedKey = normalizeGuiKey(moduleKey);
        return this.config.getString("gui.module-names." + normalizedKey, fallbackLabel(normalizedKey));
    }

    public String guiLabel(String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        return this.config.getString("gui.labels." + key, fallback);
    }

    public String databaseType() {
        return this.config.getString("database.type", "SQLITE");
    }

    private String normalizeGuiKey(String key) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }
        return key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String fallbackLabel(String key) {
        return key == null || key.isBlank() ? "-" : key.replace('_', ' ');
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
