package com.craftion.farmer.gui;

public final class ProductMenu implements FarmerMenu {

    @Override
    public String id() {
        return "product";
    }

    @Override
    public FarmerMenuAccess requiredAccess() {
        return FarmerMenuAccess.MEMBER;
    }
}
