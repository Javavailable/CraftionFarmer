package com.craftion.farmer.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

    private final String menuId;
    private final String previousMenuId;
    private final Map<Integer, MenuAction> actions;
    private final Map<Integer, MenuAction> rightActions;
    private final FarmerMenuSession session;
    private Inventory inventory;

    public MenuHolder(String menuId, String previousMenuId, Map<Integer, MenuAction> actions) {
        this(menuId, previousMenuId, actions, null);
    }

    public MenuHolder(String menuId, String previousMenuId, Map<Integer, MenuAction> actions, FarmerMenuSession session) {
        this(menuId, previousMenuId, actions, Map.of(), session);
    }

    public MenuHolder(
        String menuId,
        String previousMenuId,
        Map<Integer, MenuAction> actions,
        Map<Integer, MenuAction> rightActions,
        FarmerMenuSession session
    ) {
        if (!MenuAction.isKnownMenu(menuId)) {
            throw new IllegalArgumentException("Unsupported menu id: " + menuId);
        }
        this.menuId = menuId;
        this.previousMenuId = previousMenuId == null || previousMenuId.isBlank() ? null : previousMenuId;
        this.actions = Map.copyOf(Objects.requireNonNull(actions, "actions"));
        this.rightActions = Map.copyOf(Objects.requireNonNull(rightActions, "rightActions"));
        this.session = session;
    }

    public String menuId() {
        return this.menuId;
    }

    public Optional<String> previousMenuId() {
        return Optional.ofNullable(this.previousMenuId);
    }

    public Optional<MenuAction> actionAt(int slot) {
        return actionAt(slot, ClickType.LEFT);
    }

    public Optional<MenuAction> actionAt(int slot, ClickType clickType) {
        if (clickType == ClickType.RIGHT && this.rightActions.containsKey(slot)) {
            return Optional.of(this.rightActions.get(slot));
        }
        return Optional.ofNullable(this.actions.get(slot));
    }

    public Map<Integer, MenuAction> actions() {
        return this.actions;
    }

    public Map<Integer, MenuAction> rightActions() {
        return this.rightActions;
    }

    public Optional<FarmerMenuSession> session() {
        return Optional.ofNullable(this.session);
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
