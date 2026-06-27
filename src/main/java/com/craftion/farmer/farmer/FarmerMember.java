package com.craftion.farmer.farmer;

import java.time.Instant;
import java.util.UUID;

public record FarmerMember(
    String farmerId,
    UUID playerUuid,
    FarmerRole role,
    Instant addedAt
) {

    public FarmerMember {
        farmerId = FarmerValidation.requireNonBlank(farmerId, "farmerId");
        playerUuid = FarmerValidation.requireUuid(playerUuid, "playerUuid");
        role = FarmerValidation.requireNonNull(role, "role");
        addedAt = FarmerValidation.requireNonNull(addedAt, "addedAt");
    }
}
