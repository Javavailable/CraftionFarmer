package com.craftion.farmer.hook.skyllia;

public record FarmerReconcileResult(
    Status status,
    int ownersUpdated,
    int membersAdded,
    int membersUpdated,
    int membersRemoved
) {

    public FarmerReconcileResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null.");
        }
        if (ownersUpdated < 0 || membersAdded < 0 || membersUpdated < 0 || membersRemoved < 0) {
            throw new IllegalArgumentException("change counts cannot be negative.");
        }
    }

    public static FarmerReconcileResult noRegion() {
        return new FarmerReconcileResult(Status.NO_REGION, 0, 0, 0, 0);
    }

    public static FarmerReconcileResult noFarmer() {
        return new FarmerReconcileResult(Status.NO_FARMER, 0, 0, 0, 0);
    }

    public static FarmerReconcileResult unchanged() {
        return new FarmerReconcileResult(Status.UNCHANGED, 0, 0, 0, 0);
    }

    public static FarmerReconcileResult updated(int ownersUpdated, int membersAdded, int membersUpdated, int membersRemoved) {
        return new FarmerReconcileResult(Status.UPDATED, ownersUpdated, membersAdded, membersUpdated, membersRemoved);
    }

    public enum Status {
        UPDATED,
        UNCHANGED,
        NO_REGION,
        NO_FARMER
    }
}
