package com.craftion.farmer.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyProvider {

    String name();

    boolean isAvailable();

    EconomyDepositResult deposit(OfflinePlayer player, double amount);

    default void refresh() {
    }
}
