package com.craftion.farmer.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public final class MenuLayoutBuilder {

    private final String menuId;
    private final String previousMenuId;
    private final FarmerMenuSession session;
    private final int size;
    private final String title;
    private final Map<Integer, MenuAction> actions = new HashMap<>();
    private final Map<Integer, MenuAction> rightActions = new HashMap<>();
    private final Map<Integer, ItemStack> items = new HashMap<>();

    public MenuLayoutBuilder(String menuId, String previousMenuId, FarmerMenuSession session, int size, String title) {
        this.menuId = menuId;
        this.previousMenuId = previousMenuId;
        this.session = session;
        this.size = size;
        this.title = title;
    }

    public int size() {
        return this.size;
    }

    public void putConfiguredItem(int slot, ConfigurationSection section, Map<String, String> placeholders) {
        putConfiguredItem(slot, section, placeholders, null);
    }

    public void putConfiguredItem(int slot, ConfigurationSection section, Map<String, String> placeholders, String fallbackMaterial) {
        if (!isValidSlot(slot) || section == null) {
            return;
        }

        Optional<ItemStack> item = item(section, placeholders, fallbackMaterial);
        if (item.isEmpty()) {
            return;
        }

        putItem(slot, item.get(), action(section, "action", placeholders).orElse(null), action(section, "right-action", placeholders).orElse(null));
    }

    public void putItem(int slot, ItemStack item, MenuAction action) {
        putItem(slot, item, action, null);
    }

    public void putItem(int slot, ItemStack item, MenuAction action, MenuAction rightAction) {
        if (!isValidSlot(slot) || item == null || item.getType().isAir()) {
            return;
        }
        this.items.put(slot, item);
        if (action != null) {
            this.actions.put(slot, action);
        }
        if (rightAction != null) {
            this.rightActions.put(slot, rightAction);
        }
    }

    public MenuLayout build() {
        MenuHolder holder = new MenuHolder(this.menuId, this.previousMenuId, this.actions, this.rightActions, this.session);
        return new MenuLayout(holder, this.size, this.title, Map.copyOf(this.items));
    }

    private Optional<ItemStack> item(ConfigurationSection section, Map<String, String> placeholders, String fallbackMaterial) {
        Material material = material(applyPlaceholders(section.getString("material", ""), placeholders));
        if ((material == null || material.isAir()) && fallbackMaterial != null && !fallbackMaterial.isBlank()) {
            material = material(fallbackMaterial);
        }
        if (material == null || material.isAir()) {
            return Optional.empty();
        }

        return Optional.of(MenuItemBuilder.of(material)
            .amount(clamp(section.getInt("amount", 1), 1, 64))
            .displayName(applyPlaceholders(section.getString("name"), placeholders))
            .lore(applyPlaceholders(section.getStringList("lore"), placeholders))
            .build());
    }

    private Optional<MenuAction> action(ConfigurationSection section, String key, Map<String, String> placeholders) {
        String rawAction = applyPlaceholders(section.getString(key), placeholders);
        return MenuAction.parse(rawAction);
    }

    private Material material(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        return Material.matchMaterial(materialName);
    }

    private String applyPlaceholders(String value, Map<String, String> placeholders) {
        if (value == null) {
            return null;
        }
        String result = value;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return result;
    }

    private List<String> applyPlaceholders(List<String> values, Map<String, String> placeholders) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(value -> applyPlaceholders(value, placeholders)).toList();
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < this.size;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record MenuLayout(MenuHolder holder, int size, String title, Map<Integer, ItemStack> items) {
    }
}
