package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ModuleStateService {

    private final ConfigManager configManager;
    private final FarmerPersistenceService farmerPersistenceService;
    private final ConcurrentMap<String, Long> stateVersions = new ConcurrentHashMap<>();

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
        String versionKey = farmer.farmerId() + ":" + moduleKey;
        boolean previousState;
        boolean nextState;
        long operationVersion;
        synchronized (farmer) {
            previousState = state(farmer, module);
            nextState = !previousState;
            farmer.setModuleState(moduleKey, nextState);
            operationVersion = this.stateVersions.merge(versionKey, 1L, Long::sum);
        }
        return this.farmerPersistenceService.save(farmer)
            .thenApply(ignored -> new ModuleStateResult(ModuleStateResult.Status.SUCCESS, module.key(), nextState))
            .exceptionally(throwable -> {
                boolean safeState;
                synchronized (farmer) {
                    boolean currentState = state(farmer, module);
                    long currentVersion = this.stateVersions.getOrDefault(versionKey, 0L);
                    if (currentState == nextState && currentVersion == operationVersion) {
                        farmer.setModuleState(moduleKey, previousState);
                        this.stateVersions.merge(versionKey, 1L, Long::sum);
                    }
                    safeState = state(farmer, module);
                }
                return new ModuleStateResult(ModuleStateResult.Status.FAILED, module.key(), safeState);
            });
    }

    private String normalize(String moduleKey) {
        return moduleKey == null ? "" : moduleKey.trim().toLowerCase(Locale.ROOT);
    }
}
