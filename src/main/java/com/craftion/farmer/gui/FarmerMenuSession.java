package com.craftion.farmer.gui;

import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerRole;
import java.util.Objects;

public record FarmerMenuSession(Farmer farmer, FarmerRole role, boolean trusted) {

    public FarmerMenuSession {
        Objects.requireNonNull(farmer, "farmer");
        role = role == null ? FarmerRole.VIEWER : role;
    }

    public boolean canOpen(FarmerMenuAccess access) {
        return access == null || access.allows(this.role);
    }
}
