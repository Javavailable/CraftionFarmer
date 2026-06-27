package com.craftion.farmer.farmer;

public record FarmerRemoveResult(
    Status status,
    String regionId
) {

    public static FarmerRemoveResult of(Status status, String regionId) {
        return new FarmerRemoveResult(status, regionId);
    }

    public static FarmerRemoveResult of(Status status) {
        return new FarmerRemoveResult(status, null);
    }

    public enum Status {
        CONFIRM_REQUIRED,
        REMOVED,
        PROVIDER_UNAVAILABLE,
        NO_REGION,
        NOT_ALLOWED,
        NO_FARMER,
        NO_PENDING,
        EXPIRED
    }
}
