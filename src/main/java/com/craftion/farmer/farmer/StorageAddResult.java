package com.craftion.farmer.farmer;

public record StorageAddResult(
    MaterialKey materialKey,
    long requestedAmount,
    long collectedAmount,
    long remainingAmount,
    long storageAmount,
    long capacity
) {

    public StorageAddResult {
        materialKey = FarmerValidation.requireNonNull(materialKey, "materialKey");
        requestedAmount = FarmerValidation.requireNonNegative(requestedAmount, "requestedAmount");
        collectedAmount = FarmerValidation.requireNonNegative(collectedAmount, "collectedAmount");
        remainingAmount = FarmerValidation.requireNonNegative(remainingAmount, "remainingAmount");
        storageAmount = FarmerValidation.requireNonNegative(storageAmount, "storageAmount");
    }

    public boolean changedStorage() {
        return this.collectedAmount > 0L;
    }
}
