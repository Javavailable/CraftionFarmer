package com.craftion.farmer.hook.placeholder;

import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.module.ModuleManager;
import com.craftion.farmer.module.ProductionEstimate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class PapiPlaceholderProvider extends PlaceholderExpansion implements PlaceholderProvider {

    private final JavaPlugin plugin;
    private final FarmerCache farmerCache;
    private final ModuleManager moduleManager;
    private boolean registered;

    public PapiPlaceholderProvider(JavaPlugin plugin, FarmerCache farmerCache, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.farmerCache = farmerCache;
        this.moduleManager = moduleManager;
    }

    @Override
    public String name() {
        return "PlaceholderAPI";
    }

    @Override
    public boolean isAvailable() {
        return this.registered;
    }

    @Override
    public void initialize() {
        this.registered = register();
    }

    @Override
    public void shutdown() {
        if (this.registered) {
            unregister();
            this.registered = false;
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "craftionfarmer";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Craftion";
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) {
            return "-";
        }

        Optional<Farmer> farmer = farmer(player.getUniqueId());
        if (farmer.isEmpty()) {
            return "-";
        }

        return value(farmer.get(), params == null ? "" : params.trim().toLowerCase(Locale.ROOT));
    }

    private String value(Farmer farmer, String params) {
        return switch (params) {
            case "farmer_id" -> text(farmer.farmerId());
            case "region_id" -> text(farmer.regionId());
            case "owner" -> farmer.ownerUuid().toString();
            case "level" -> String.valueOf(farmer.level());
            case "storage_total" -> formatAmount(storageTotal(farmer));
            case "collecting" -> plainState(farmer.collectingEnabled());
            case "module_auto_sell" -> plainState(this.moduleManager.state(farmer, "auto-sell"));
            case "module_production_calc" -> plainState(this.moduleManager.state(farmer, "production-calc"));
            case "production_minute" -> formatAmount(production(farmer).perMinute());
            case "production_hour" -> formatAmount(production(farmer).perHour());
            case "production_day" -> formatAmount(production(farmer).perDay());
            default -> "-";
        };
    }

    private Optional<Farmer> farmer(UUID playerUuid) {
        return this.farmerCache.snapshot().values().stream()
            .filter(farmer -> farmer.ownerUuid().equals(playerUuid) || farmer.members().containsKey(playerUuid))
            .findFirst();
    }

    private long storageTotal(Farmer farmer) {
        return farmer.storage().snapshot().values().stream().mapToLong(Long::longValue).sum();
    }

    private ProductionEstimate production(Farmer farmer) {
        return this.moduleManager.productionEstimate(farmer);
    }

    private String plainState(boolean enabled) {
        return enabled ? "aktif" : "pasif";
    }

    private String formatAmount(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
