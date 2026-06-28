package com.craftion.farmer.economy;

import org.bukkit.OfflinePlayer;

public final class NoEconomyProvider implements EconomyProvider {

    private final String reason;

    public NoEconomyProvider(String reason) {
        this.reason = reason == null || reason.isBlank() ? "Economy provider is unavailable." : reason.trim();
    }

    @Override
    public String name() {
        return "NONE";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public EconomyDepositResult deposit(OfflinePlayer player, double amount) {
        return EconomyDepositResult.failure(name(), this.reason);
    }
}
