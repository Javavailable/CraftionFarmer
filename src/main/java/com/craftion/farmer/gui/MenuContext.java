package com.craftion.farmer.gui;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;

public record MenuContext(Player player, MenuHolder holder, int slot) {

    public MenuContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(holder, "holder");
    }

    public String menuId() {
        return this.holder.menuId();
    }

    public Optional<FarmerMenuSession> session() {
        return this.holder.session();
    }
}
