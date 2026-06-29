package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.economy.EconomyProviderManager;
import com.craftion.farmer.economy.StorageTransactionService;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.FarmerSaveRetryService;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.gui.FarmerMenuSession;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.storage.repository.LogRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModuleManager {

    private final ConfigManager configManager;
    private final DebugLogger debugLogger;
    private final FarmerPersistenceService farmerPersistenceService;
    private final FarmerSaveRetryService farmerSaveRetryService;
    private final LogRepository logRepository;
    private final ModuleStateService moduleStateService;
    private final ModuleAccessService moduleAccessService;
    private final Map<String, FarmerModule> modules = new LinkedHashMap<>();
    private final Map<String, ModuleCardDescriptor> unavailableCards = new LinkedHashMap<>();
    private final ProductionCalcModule productionCalcModule;

    public ModuleManager(
        JavaPlugin plugin,
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        FarmerCache farmerCache,
        FarmerPersistenceService farmerPersistenceService,
        FarmerSaveRetryService farmerSaveRetryService,
        LogRepository logRepository,
        RegionProviderManager regionProviderManager,
        EconomyProviderManager economyProviderManager,
        StorageTransactionService storageTransactionService
    ) {
        Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.debugLogger = Objects.requireNonNull(debugLogger, "debugLogger");
        this.farmerPersistenceService = Objects.requireNonNull(farmerPersistenceService, "farmerPersistenceService");
        this.farmerSaveRetryService = Objects.requireNonNull(farmerSaveRetryService, "farmerSaveRetryService");
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
        this.moduleStateService = new ModuleStateService(configManager, farmerPersistenceService);
        this.moduleAccessService = new ModuleAccessService(configManager);
        this.productionCalcModule = new ProductionCalcModule();
        register(this.productionCalcModule);
        register(new AutoSellModule(
            configManager,
            Objects.requireNonNull(schedulerAdapter, "schedulerAdapter"),
            this.debugLogger,
            Objects.requireNonNull(farmerCache, "farmerCache"),
            this.moduleStateService,
            Objects.requireNonNull(regionProviderManager, "regionProviderManager"),
            Objects.requireNonNull(economyProviderManager, "economyProviderManager"),
            Objects.requireNonNull(storageTransactionService, "storageTransactionService")
        ));
        register(new AutoHarvestModule(
            plugin,
            configManager,
            this.debugLogger,
            Objects.requireNonNull(farmerCache, "farmerCache"),
            this.moduleStateService,
            Objects.requireNonNull(regionProviderManager, "regionProviderManager")
        ));
        registerUnavailable(ModuleCardDescriptor.unavailable("auto-kill", "IRON_SWORD"));
    }

    public void initialize() {
        this.modules.values().forEach(FarmerModule::initialize);
    }

    public void reload() {
        this.modules.values().forEach(FarmerModule::reload);
    }

    public void shutdown() {
        this.modules.values().forEach(FarmerModule::shutdown);
    }

    public List<FarmerModule> modules() {
        return List.copyOf(this.modules.values());
    }

    public List<ModuleCardDescriptor> moduleCards() {
        List<ModuleCardDescriptor> cards = new ArrayList<>();
        this.modules.values().stream().map(ModuleCardDescriptor::available).forEach(cards::add);
        this.unavailableCards.values().stream()
            .filter(card -> !this.modules.containsKey(normalize(card.key())))
            .forEach(cards::add);
        return List.copyOf(cards);
    }

    public Optional<FarmerModule> module(String moduleKey) {
        return Optional.ofNullable(this.modules.get(normalize(moduleKey)));
    }

    public Optional<ModuleCardDescriptor> moduleCard(String moduleKey) {
        String normalizedKey = normalize(moduleKey);
        FarmerModule module = this.modules.get(normalizedKey);
        if (module != null) {
            return Optional.of(ModuleCardDescriptor.available(module));
        }
        return Optional.ofNullable(this.unavailableCards.get(normalizedKey));
    }

    public ModuleAccessResult access(Player player, FarmerMenuSession session, ModuleCardDescriptor card) {
        return this.moduleAccessService.evaluate(player, session, card);
    }

    public ModuleAccessResult access(Player player, FarmerMenuSession session, String moduleKey) {
        return moduleCard(moduleKey)
            .map(card -> access(player, session, card))
            .orElseGet(() -> new ModuleAccessResult(
                ModuleAccessResult.Status.UNKNOWN_MODULE,
                normalize(moduleKey),
                this.configManager.modulePermission(moduleKey),
                this.configManager.modulePermissionRequired(moduleKey),
                false,
                false,
                false,
                false
            ));
    }

    public boolean configEnabled(String moduleKey) {
        return moduleCard(moduleKey).isPresent() && this.configManager.moduleEnabled(moduleKey);
    }

    public boolean state(Farmer farmer, String moduleKey) {
        Optional<FarmerModule> module = module(moduleKey);
        return module.isPresent() && this.moduleStateService.state(farmer, module.get());
    }

    public boolean ensureDefaultStates(Farmer farmer) {
        boolean changed = this.moduleStateService.ensureDefaults(farmer, this.modules.values());
        if (changed) {
            this.farmerPersistenceService.save(farmer).whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    this.debugLogger.debug("Module default save failed for " + farmer.farmerId() + ": " + readableMessage(throwable));
                    this.farmerSaveRetryService.markDirty(farmer, "module defaults");
                }
            });
        }
        return changed;
    }

    public void ensureDefaultStates(Collection<Farmer> farmers) {
        if (farmers == null || farmers.isEmpty()) {
            return;
        }

        List<Farmer> changedFarmers = farmers.stream()
            .filter(farmer -> this.moduleStateService.ensureDefaults(farmer, this.modules.values()))
            .toList();
        if (!changedFarmers.isEmpty()) {
            this.farmerPersistenceService.saveAll(changedFarmers).whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    this.debugLogger.debug("Module default batch save failed: " + readableMessage(throwable));
                    changedFarmers.forEach(farmer -> this.farmerSaveRetryService.markDirty(farmer, "module defaults"));
                }
            });
        }
    }

    public CompletableFuture<ModuleStateResult> toggle(Player player, FarmerMenuSession session, String moduleKey) {
        Optional<ModuleCardDescriptor> card = moduleCard(moduleKey);
        if (card.isEmpty()) {
            return CompletableFuture.completedFuture(new ModuleStateResult(ModuleStateResult.Status.UNKNOWN_MODULE, moduleKey, false));
        }

        Farmer farmer = session == null ? null : session.farmer();
        boolean currentState = farmer != null && state(farmer, card.get().key());
        ModuleAccessResult access = access(player, session, card.get());
        if (!access.toggleAllowed()) {
            ModuleStateResult.Status status = switch (access.status()) {
                case ROLE_DENIED -> ModuleStateResult.Status.DENIED;
                case PERMISSION_DENIED -> ModuleStateResult.Status.PERMISSION_DENIED;
                case CONFIG_DISABLED -> ModuleStateResult.Status.MODULE_DISABLED;
                case UNAVAILABLE -> ModuleStateResult.Status.UNAVAILABLE;
                case UNKNOWN_MODULE -> ModuleStateResult.Status.UNKNOWN_MODULE;
                case ALLOWED -> ModuleStateResult.Status.FAILED;
            };
            return CompletableFuture.completedFuture(new ModuleStateResult(status, card.get().key(), currentState));
        }

        Optional<FarmerModule> module = module(card.get().key());
        if (module.isEmpty() || farmer == null) {
            return CompletableFuture.completedFuture(new ModuleStateResult(ModuleStateResult.Status.UNKNOWN_MODULE, card.get().key(), currentState));
        }

        return this.moduleStateService.toggle(farmer, module.get()).thenApply(result -> {
            if (result.success()) {
                module.get().onStateChanged(farmer, result.enabled());
                appendToggleLog(farmer, module.get(), result.enabled(), player == null ? null : player.getUniqueId());
            }
            return result;
        });
    }

    private void appendToggleLog(Farmer farmer, FarmerModule module, boolean enabled, java.util.UUID actorUuid) {
        this.logRepository.append(
            farmer.farmerId(),
            actorUuid,
            "MODULE_TOGGLE",
            "module=" + module.key() + " enabled=" + enabled
        ).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                this.debugLogger.debug("Module toggle log failed for " + farmer.farmerId() + ": " + readableMessage(throwable));
            }
        });
    }

    public void recordCollect(Farmer farmer, MaterialKey materialKey, long amount) {
        if (state(farmer, ProductionCalcModule.KEY)) {
            this.productionCalcModule.record(farmer, materialKey, amount);
        }
    }

    public ProductionEstimate productionEstimate(Farmer farmer) {
        if (!state(farmer, ProductionCalcModule.KEY)) {
            return ProductionEstimate.empty();
        }
        return this.productionCalcModule.estimate(farmer, new com.craftion.farmer.economy.ConfigPriceProvider(this.configManager));
    }

    public String intervalLabel(String moduleKey) {
        if (AutoSellModule.KEY.equals(normalize(moduleKey))) {
            return this.configManager.autoSellIntervalSeconds() + " sɴ";
        }
        return "-";
    }

    private void register(FarmerModule module) {
        this.modules.put(normalize(module.key()), module);
    }

    private void registerUnavailable(ModuleCardDescriptor card) {
        this.unavailableCards.put(normalize(card.key()), card);
    }

    private String normalize(String moduleKey) {
        return moduleKey == null ? "" : moduleKey.trim().toLowerCase(Locale.ROOT);
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }
}
