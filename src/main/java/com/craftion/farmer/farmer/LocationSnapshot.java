package com.craftion.farmer.farmer;

public record LocationSnapshot(
    String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {

    public LocationSnapshot {
        world = FarmerValidation.requireNonBlank(world, "world");
    }

    public static LocationSnapshot of(String world, double x, double y, double z, float yaw, float pitch) {
        return new LocationSnapshot(world, x, y, z, yaw, pitch);
    }
}
