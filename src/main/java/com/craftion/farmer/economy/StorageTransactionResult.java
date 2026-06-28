package com.craftion.farmer.economy;

import com.craftion.farmer.farmer.MaterialKey;

public record StorageTransactionResult(
    Status status,
    MaterialKey materialKey,
    long amount,
    long materialCount,
    double gross,
    double tax,
    double net,
    String providerName,
    String errorMessage
) {

    public StorageTransactionResult {
        status = status == null ? Status.FAILED : status;
        amount = Math.max(0L, amount);
        materialCount = Math.max(0L, materialCount);
        gross = clean(gross);
        tax = clean(tax);
        net = clean(net);
        providerName = providerName == null ? "" : providerName.trim();
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
    }

    public boolean success() {
        return this.status == Status.SUCCESS;
    }

    private static double clean(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    public enum Status {
        SUCCESS,
        DENIED,
        EMPTY_STORAGE,
        INVENTORY_FULL,
        INVALID_ACTION,
        NO_PRICE,
        ECONOMY_UNAVAILABLE,
        DEPOSIT_FAILED,
        FAILED
    }
}
