package com.craftion.farmer.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

    private final String menuId;
    private final String previousMenuId;
    private final Map<Integer, MenuAction> actions;
    private Inventory inventory;

    public MenuHolder(String menuId, String previousMenuId, Map<Integer, MenuAction> actions) {
        if (!MenuAction.isKnownMenu(menuId)) {
            throw new IllegalArgumentException("Unsupported menu id: " + menuId);
        }
        this.menuId = menuId;
        this.previousMenuId = previousMenuId == null || previousMenuId.isBlank() ? null : previousMenuId;
        this.actions = Map.copyOf(Objects.requireNonNull(actions, "actions"));
    }

    public String menuId() {
        return this.menuId;
    }

    public Optional<String> previousMenuId() {
        return Optional.ofNullable(this.previousMenuId);
    }

    public Optional<MenuAction> actionAt(int slot) {
        return Optional.ofNullable(this.actions.get(slot));
    }

    public Map<Integer, MenuAction> actions() {
        return this.actions;
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Menu inventory is already bound.");
        }
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
