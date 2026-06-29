package com.craftion.farmer.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyProvider {

    String name();

    boolean isAvailable();

    EconomyDepositResult deposit(OfflinePlayer player, double amount);

    default String format(double amount) {
        return String.format(java.util.Locale.US, "%,.2f", amount);
    }

    default void refresh() {
    }
}
