package com.craftion.farmer.economy;

public record EconomyDepositResult(boolean success, String providerName, String errorMessage) {

    public EconomyDepositResult {
        providerName = providerName == null || providerName.isBlank() ? "unknown" : providerName.trim();
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
    }

    public static EconomyDepositResult success(String providerName) {
        return new EconomyDepositResult(true, providerName, "");
    }

    public static EconomyDepositResult failure(String providerName, String errorMessage) {
        return new EconomyDepositResult(false, providerName, errorMessage);
    }
}
