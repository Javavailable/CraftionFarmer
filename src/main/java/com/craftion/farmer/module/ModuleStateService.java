package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ModuleStateService {

    private final ConfigManager configManager;
    private final FarmerPersistenceService farmerPersistenceService;

    public ModuleStateService(ConfigManager configManager, FarmerPersistenceService farmerPersistenceService) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.farmerPersistenceService = Objects.requireNonNull(farmerPersistenceService, "farmerPersistenceService");
    }

    public boolean state(Farmer farmer, FarmerModule module) {
        if (farmer == null || module == null || !this.configManager.moduleEnabled(module.key())) {
            return false;
        }
        return farmer.moduleStates().getOrDefault(normalize(module.key()), this.configManager.moduleDefaultState(module.key()));
    }

    public boolean ensureDefaults(Farmer farmer, Collection<FarmerModule> modules) {
        if (farmer == null || modules == null || modules.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (FarmerModule module : modules) {
            if (module == null) {
                continue;
            }
            String moduleKey = normalize(module.key());
            if (!farmer.moduleStates().containsKey(moduleKey)) {
                farmer.setModuleState(moduleKey, this.configManager.moduleDefaultState(moduleKey));
                changed = true;
            }
        }
        return changed;
    }

    public CompletableFuture<ModuleStateResult> toggle(Farmer farmer, FarmerModule module) {
        if (farmer == null || module == null) {
            return CompletableFuture.completedFuture(new ModuleStateResult(ModuleStateResult.Status.UNKNOWN_MODULE, "", false));
        }
        if (!this.configManager.moduleEnabled(module.key())) {
            return CompletableFuture.completedFuture(new ModuleStateResult(ModuleStateResult.Status.MODULE_DISABLED, module.key(), false));
        }

        String moduleKey = normalize(module.key());
        boolean previousState = state(farmer, module);
        boolean nextState = !previousState;
        farmer.setModuleState(moduleKey, nextState);
        return this.farmerPersistenceService.save(farmer)
            .thenApply(ignored -> new ModuleStateResult(ModuleStateResult.Status.SUCCESS, module.key(), nextState))
            .exceptionally(throwable -> {
                farmer.setModuleState(moduleKey, previousState);
                return new ModuleStateResult(ModuleStateResult.Status.FAILED, module.key(), previousState);
            });
    }

    private String normalize(String moduleKey) {
        return moduleKey == null ? "" : moduleKey.trim().toLowerCase(Locale.ROOT);
    }
}
