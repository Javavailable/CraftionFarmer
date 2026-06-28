package com.craftion.farmer.farmer;

public record StorageRemoveResult(
    MaterialKey materialKey,
    long requestedAmount,
    long removedAmount,
    long missingAmount,
    long storageAmount
) {

    public StorageRemoveResult {
        materialKey = FarmerValidation.requireNonNull(materialKey, "materialKey");
        requestedAmount = FarmerValidation.requireNonNegative(requestedAmount, "requestedAmount");
        removedAmount = FarmerValidation.requireNonNegative(removedAmount, "removedAmount");
        missingAmount = FarmerValidation.requireNonNegative(missingAmount, "missingAmount");
        storageAmount = FarmerValidation.requireNonNegative(storageAmount, "storageAmount");
    }

    public boolean changedStorage() {
        return this.removedAmount > 0L;
    }
}
