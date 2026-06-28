package com.craftion.farmer.module;

import com.craftion.farmer.farmer.Farmer;

public interface FarmerModule {

    String key();

    String iconMaterial();

    default void initialize() {
    }

    default void reload() {
        shutdown();
        initialize();
    }

    default void shutdown() {
    }

    default void onStateChanged(Farmer farmer, boolean enabled) {
    }

    default boolean placeholder() {
        return false;
    }
}
