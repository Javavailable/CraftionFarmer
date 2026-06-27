package com.craftion.farmer.farmer;

public record FarmerCreateResult(
    Status status,
    Farmer farmer,
    String regionId
) {

    public static FarmerCreateResult created(Farmer farmer) {
        return new FarmerCreateResult(Status.CREATED, farmer, farmer.regionId());
    }

    public static FarmerCreateResult failed(Status status) {
        return failed(status, null);
    }

    public static FarmerCreateResult failed(Status status, String regionId) {
        return new FarmerCreateResult(status, null, regionId);
    }

    public enum Status {
        CREATED,
        PROVIDER_UNAVAILABLE,
        NO_REGION,
        NOT_ALLOWED,
        DUPLICATE
    }
}
