package com.craftion.farmer.config;

import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.gui.MenuAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigValidationService {

    private static final long SAFE_AUTO_SELL_INTERVAL_SECONDS = 5L;

    private final JavaPlugin plugin;
    private final DebugLogger debugLogger;

    public ConfigValidationService(JavaPlugin plugin, DebugLogger debugLogger) {
        this.plugin = plugin;
        this.debugLogger = debugLogger;
    }

    public void validate() {
        FileConfiguration config = this.plugin.getConfig();
        List<String> warnings = new ArrayList<>();
        validateCollect(config, warnings);
        validateEconomy(config, warnings);
        validatePrices(config, warnings);
        validateAutoSell(config, warnings);
        validateGui(config, warnings);

        if (warnings.isEmpty()) {
            this.debugLogger.debug("Config validation completed without warnings.");
            return;
        }
        for (String warning : warnings) {
            this.plugin.getLogger().warning("Config warning: " + warning);
        }
    }

    private void validateCollect(FileConfiguration config, List<String> warnings) {
        List<String> materialNames = config.getStringList("collect.allowed-materials");
        if (materialNames.isEmpty()) {
            warnings.add("collect.allowed-materials is empty; collector will skip every item.");
            return;
        }

        int validMaterials = 0;
        for (String materialName : materialNames) {
            Material material = material(materialName);
            if (material == null || material.isAir()) {
                warnings.add("Invalid collect material: collect.allowed-materials -> " + materialName);
                continue;
            }
            validMaterials++;
            String priceKey = normalizeKey(material.name());
            if (!hasValidPrice(config, priceKey)) {
                warnings.add("Missing or invalid price entry for collect material: prices." + priceKey);
            }
        }
        if (validMaterials == 0) {
            warnings.add("collect.allowed-materials has no valid materials; collector will skip every item.");
        }
    }

    private void validateEconomy(FileConfiguration config, List<String> warnings) {
        Number taxRate = number(config.get("economy.tax.rate"));
        if (taxRate == null) {
            warnings.add("economy.tax.rate is not numeric; it will be treated as 0.");
            return;
        }
        double rawRate = taxRate.doubleValue();
        if (!Double.isFinite(rawRate) || rawRate < 0.0D || rawRate > 1.0D) {
            warnings.add("economy.tax.rate is outside 0-1 before clamp: " + rawRate);
        }
    }

    private void validatePrices(FileConfiguration config, List<String> warnings) {
        ConfigurationSection prices = config.getConfigurationSection("prices");
        if (prices == null || prices.getKeys(false).isEmpty()) {
            warnings.add("prices section is empty; sell actions will not have valid prices.");
            return;
        }
        for (String key : prices.getKeys(false)) {
            Material material = material(key);
            if (material == null || material.isAir()) {
                warnings.add("Invalid material key in prices: prices." + key);
            }
            if (!hasValidPrice(config, key)) {
                warnings.add("Invalid price value: prices." + key + " must be greater than 0.");
            }
        }
    }

    private void validateAutoSell(FileConfiguration config, List<String> warnings) {
        Number interval = number(config.get("modules.auto-sell.interval-seconds"));
        if (interval == null) {
            warnings.add("modules.auto-sell.interval-seconds is not numeric; safe fallback will be used.");
            return;
        }
        if (interval.longValue() < SAFE_AUTO_SELL_INTERVAL_SECONDS) {
            warnings.add("modules.auto-sell.interval-seconds is below safe minimum before clamp: " + interval.longValue());
        }
    }

    private void validateGui(FileConfiguration config, List<String> warnings) {
        ConfigurationSection menus = config.getConfigurationSection("gui.menus");
        if (menus == null) {
            warnings.add("gui.menus section is missing; menus will fall back poorly.");
            return;
        }
        Set<String> moduleKeys = moduleKeys(config);
        for (String menuId : menus.getKeys(false)) {
            ConfigurationSection menu = menus.getConfigurationSection(menuId);
            if (menu == null) {
                continue;
            }
            int size = menu.getInt("size", 54);
            if (size < 9 || size > 54 || size % 9 != 0) {
                warnings.add("Invalid GUI menu size at gui.menus." + menuId + ".size: " + size);
            }
            validateStaticItems(menuId, menu, size, moduleKeys, warnings);
            validateDynamicItems(menuId, menu, size, moduleKeys, warnings);
        }
    }

    private void validateStaticItems(String menuId, ConfigurationSection menu, int size, Set<String> moduleKeys, List<String> warnings) {
        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        Set<Integer> slots = new HashSet<>();
        for (String slotKey : items.getKeys(false)) {
            Integer slot = slot(slotKey);
            if (slot == null) {
                warnings.add("Invalid GUI slot key at gui.menus." + menuId + ".items." + slotKey);
                continue;
            }
            if (!slots.add(slot)) {
                warnings.add("Duplicate GUI slot at gui.menus." + menuId + ".items." + slot);
            }
            if (slot < 0 || slot >= size) {
                warnings.add("GUI slot outside menu size at gui.menus." + menuId + ".items." + slot);
            }
            ConfigurationSection item = items.getConfigurationSection(slotKey);
            validateItem("gui.menus." + menuId + ".items." + slotKey, item, moduleKeys, warnings);
        }
    }

    private void validateDynamicItems(String menuId, ConfigurationSection menu, int size, Set<String> moduleKeys, List<String> warnings) {
        Set<Integer> staticSlots = configuredSlots(menu.getConfigurationSection("items"));
        validateSlotList(menuId, "storage-items.slots", menu.getIntegerList("storage-items.slots"), staticSlots, size, warnings);
        validateSlotList(menuId, "member-items.slots", menu.getIntegerList("member-items.slots"), staticSlots, size, warnings);
        validateSlotList(menuId, "module-items.slots", menu.getIntegerList("module-items.slots"), staticSlots, size, warnings);
        validateItem("gui.menus." + menuId + ".storage-items.filled", menu.getConfigurationSection("storage-items.filled"), moduleKeys, warnings);
        validateItem("gui.menus." + menuId + ".storage-items.empty", menu.getConfigurationSection("storage-items.empty"), moduleKeys, warnings);
        validateItem("gui.menus." + menuId + ".member-items.item", menu.getConfigurationSection("member-items.item"), moduleKeys, warnings);
        validateItem("gui.menus." + menuId + ".module-items.enabled", menu.getConfigurationSection("module-items.enabled"), moduleKeys, warnings);
        validateItem("gui.menus." + menuId + ".module-items.disabled", menu.getConfigurationSection("module-items.disabled"), moduleKeys, warnings);
        validateItem("gui.menus." + menuId + ".module-items.unavailable", menu.getConfigurationSection("module-items.unavailable"), moduleKeys, warnings);
        validateItem("gui.menus." + menuId + ".module-items.empty", menu.getConfigurationSection("module-items.empty"), moduleKeys, warnings);
    }

    private void validateSlotList(String menuId, String path, List<Integer> slots, Set<Integer> staticSlots, int size, List<String> warnings) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer slot : slots) {
            if (slot == null) {
                continue;
            }
            if (!seen.add(slot)) {
                warnings.add("Duplicate GUI slot at gui.menus." + menuId + "." + path + ": " + slot);
            }
            if (staticSlots.contains(slot)) {
                warnings.add("Dynamic GUI slot overlaps static item at gui.menus." + menuId + "." + path + ": " + slot);
            }
            if (slot < 0 || slot >= size) {
                warnings.add("Dynamic GUI slot outside menu size at gui.menus." + menuId + "." + path + ": " + slot);
            }
        }
    }

    private void validateItem(String path, ConfigurationSection item, Set<String> moduleKeys, List<String> warnings) {
        if (item == null) {
            return;
        }
        String materialName = item.getString("material", "");
        if (!materialName.contains("%")) {
            Material material = material(materialName);
            if (material == null || material.isAir()) {
                warnings.add("Invalid GUI material at " + path + ".material: " + materialName);
            }
        }
        validateAction(path + ".action", item.getString("action"), moduleKeys, warnings);
        validateAction(path + ".right-action", item.getString("right-action"), moduleKeys, warnings);
    }

    private void validateAction(String path, String rawAction, Set<String> moduleKeys, List<String> warnings) {
        if (rawAction == null || rawAction.isBlank()) {
            return;
        }
        String sampledAction = samplePlaceholders(rawAction);
        MenuAction.parse(sampledAction).ifPresentOrElse(action -> {
            if (action.type() == MenuAction.Type.MODULE_TOGGLE && !moduleKeys.contains(action.target())) {
                warnings.add("Unknown module key in GUI action at " + path + ": " + rawAction);
            }
        }, () -> warnings.add("Invalid GUI action at " + path + ": " + rawAction));
    }

    private Set<Integer> configuredSlots(ConfigurationSection items) {
        Set<String> keys = items == null ? Set.of() : items.getKeys(false);
        Set<Integer> slots = new HashSet<>();
        for (String key : keys) {
            Integer slot = slot(key);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private Set<String> moduleKeys(FileConfiguration config) {
        ConfigurationSection modules = config.getConfigurationSection("modules");
        if (modules == null) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        for (String key : modules.getKeys(false)) {
            keys.add(normalizeKey(key).replace('_', '-'));
        }
        return keys;
    }

    private boolean hasValidPrice(FileConfiguration config, String key) {
        Number value = number(config.get("prices." + normalizeKey(key)));
        return value != null && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0D;
    }

    private String samplePlaceholders(String value) {
        return value
            .replace("%material%", "wheat")
            .replace("%module_key%", "auto-sell")
            .replace("%module_material%", "emerald");
    }

    private Material material(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        return Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT));
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : null;
    }

    private Integer slot(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
