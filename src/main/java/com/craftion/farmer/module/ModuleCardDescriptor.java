package com.craftion.farmer.module;

import java.util.Objects;

public record ModuleCardDescriptor(String key, String iconMaterial, boolean lifecycleModule, boolean unavailable) {

    public ModuleCardDescriptor {
        key = Objects.requireNonNull(key, "key").trim().toLowerCase(java.util.Locale.ROOT);
        iconMaterial = iconMaterial == null || iconMaterial.isBlank() ? "AMETHYST_SHARD" : iconMaterial.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static ModuleCardDescriptor available(FarmerModule module) {
        Objects.requireNonNull(module, "module");
        return new ModuleCardDescriptor(module.key(), module.iconMaterial(), true, false);
    }

    public static ModuleCardDescriptor unavailable(String key, String iconMaterial) {
        return new ModuleCardDescriptor(key, iconMaterial, false, true);
    }
}
