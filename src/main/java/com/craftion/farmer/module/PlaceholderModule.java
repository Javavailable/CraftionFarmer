package com.craftion.farmer.module;

public final class PlaceholderModule implements FarmerModule {

    private final String key;
    private final String iconMaterial;

    public PlaceholderModule(String key, String iconMaterial) {
        this.key = key;
        this.iconMaterial = iconMaterial;
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String iconMaterial() {
        return this.iconMaterial;
    }

    @Override
    public boolean placeholder() {
        return true;
    }
}
