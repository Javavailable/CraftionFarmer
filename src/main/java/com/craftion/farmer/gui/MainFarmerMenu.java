package com.craftion.farmer.gui;

public final class MainFarmerMenu implements FarmerMenu {

    @Override
    public String id() {
        return "main";
    }

    @Override
    public FarmerMenuAccess requiredAccess() {
        return FarmerMenuAccess.VIEWER;
    }
}
