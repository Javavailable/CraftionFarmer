package com.craftion.farmer.gui;

public final class ManageMenu implements FarmerMenu {

    @Override
    public String id() {
        return "manage";
    }

    @Override
    public FarmerMenuAccess requiredAccess() {
        return FarmerMenuAccess.MANAGER;
    }
}
