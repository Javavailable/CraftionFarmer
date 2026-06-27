package com.craftion.farmer.farmer;

public record FarmerStatistics(
    long collectedItems,
    long storedItems,
    long withdrawnItems
) {

    public FarmerStatistics {
        FarmerValidation.requireNonNegative(collectedItems, "collectedItems");
        FarmerValidation.requireNonNegative(storedItems, "storedItems");
        FarmerValidation.requireNonNegative(withdrawnItems, "withdrawnItems");
    }

    public static FarmerStatistics empty() {
        return new FarmerStatistics(0L, 0L, 0L);
    }
}
