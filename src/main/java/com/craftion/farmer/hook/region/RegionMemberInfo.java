package com.craftion.farmer.hook.region;

import com.craftion.farmer.farmer.FarmerRole;
import java.util.UUID;

public record RegionMemberInfo(
    String regionId,
    UUID playerUuid,
    FarmerRole role,
    boolean owner,
    boolean trusted
) {

    public RegionMemberInfo {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("regionId cannot be blank.");
        }
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role cannot be null.");
        }
        regionId = regionId.trim();
    }
}
