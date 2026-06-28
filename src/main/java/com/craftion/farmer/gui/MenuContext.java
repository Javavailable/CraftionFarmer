package com.craftion.farmer.gui;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public record MenuContext(Player player, MenuHolder holder, int slot, ClickType clickType) {

    public MenuContext(Player player, MenuHolder holder, int slot) {
        this(player, holder, slot, ClickType.LEFT);
    }

    public MenuContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(holder, "holder");
        clickType = clickType == null ? ClickType.LEFT : clickType;
    }

    public String menuId() {
        return this.holder.menuId();
    }

    public Optional<FarmerMenuSession> session() {
        return this.holder.session();
    }
}
