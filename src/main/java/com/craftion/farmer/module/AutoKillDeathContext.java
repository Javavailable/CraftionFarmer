package com.craftion.farmer.module;

import com.craftion.farmer.farmer.Farmer;
import java.util.Objects;
import org.bukkit.entity.EntityType;

public record AutoKillDeathContext(Farmer farmer, String regionId, EntityType entityType) {

    public AutoKillDeathContext {
        Objects.requireNonNull(farmer, "farmer");
        regionId = regionId == null ? "" : regionId.trim();
        Objects.requireNonNull(entityType, "entityType");
    }
}
