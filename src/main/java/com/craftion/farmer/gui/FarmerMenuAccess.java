package com.craftion.farmer.gui;

import com.craftion.farmer.farmer.FarmerRole;

public enum FarmerMenuAccess {
    VIEWER(0),
    MEMBER(1),
    MANAGER(2),
    OWNER(3);

    private final int level;

    FarmerMenuAccess(int level) {
        this.level = level;
    }

    public boolean allows(FarmerRole role) {
        return fromRole(role).level >= this.level;
    }

    public static FarmerMenuAccess fromRole(FarmerRole role) {
        if (role == null) {
            return VIEWER;
        }
        return switch (role) {
            case OWNER -> OWNER;
            case MANAGER -> MANAGER;
            case MEMBER -> MEMBER;
            case VIEWER -> VIEWER;
        };
    }
}
