package com.craftion.farmer.command;

import com.craftion.farmer.CraftionFarmerPlugin;
import com.craftion.farmer.economy.EconomyProvider;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCreateResult;
import com.craftion.farmer.farmer.FarmerRemoveResult;
import com.craftion.farmer.hook.placeholder.PlaceholderProvider;
import com.craftion.farmer.hook.region.RegionAccessResult;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.skyllia.FarmerReconcileResult;
import com.craftion.farmer.hook.visual.FarmerVisualProvider;
import com.craftion.farmer.message.MessageService;
import com.craftion.farmer.module.ProductionEstimate;
import com.craftion.farmer.storage.repository.FarmerLogEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FarmerCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERMISSION = "craftionfarmer.use";
    private static final String CREATE_PERMISSION = "craftionfarmer.create";
    private static final String REMOVE_PERMISSION = "craftionfarmer.remove";
    private static final String ADMIN_PERMISSION = "craftionfarmer.admin";
    private static final String ADMIN_BYPASS_PERMISSION = "craftionfarmer.admin.bypass";
    private static final String RELOAD_PERMISSION = "craftionfarmer.admin.reload";
    private static final String RECONCILE_PERMISSION = "craftionfarmer.admin.reconcile";
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final CraftionFarmerPlugin plugin;
    private final MessageService messageService;

    public FarmerCommand(CraftionFarmerPlugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            this.messageService.sendList(sender, "commands.farmer.help");
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            handleCreate(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            handleRemove(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("open")) {
            handleOpen(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            handleInfo(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            handleAdmin(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reconcile")) {
            handleReconcile(sender, args);
            return true;
        }

        this.messageService.sendList(sender, "commands.farmer.help");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            addIfMatches(completions, "help", args[0]);
            if (sender.hasPermission(USE_PERMISSION)) {
                addIfMatches(completions, "open", args[0]);
                addIfMatches(completions, "info", args[0]);
            }
            if (sender.hasPermission(CREATE_PERMISSION)) {
                addIfMatches(completions, "create", args[0]);
            }
            if (sender.hasPermission(REMOVE_PERMISSION)) {
                addIfMatches(completions, "remove", args[0]);
            }
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                addIfMatches(completions, "admin", args[0]);
            }
            if (sender.hasPermission(RELOAD_PERMISSION)) {
                addIfMatches(completions, "reload", args[0]);
            }
            if (sender.hasPermission(RECONCILE_PERMISSION)) {
                addIfMatches(completions, "reconcile", args[0]);
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender.hasPermission(REMOVE_PERMISSION)) {
            List<String> completions = new ArrayList<>();
            addIfMatches(completions, "confirm", args[1]);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission(ADMIN_PERMISSION)) {
            List<String> completions = new ArrayList<>();
            addIfMatches(completions, "info", args[1]);
            addIfMatches(completions, "logs", args[1]);
            return completions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("logs") && sender.hasPermission(ADMIN_PERMISSION)) {
            List<String> completions = new ArrayList<>();
            addIfMatches(completions, "10", args[3]);
            addIfMatches(completions, "25", args[3]);
            addIfMatches(completions, "50", args[3]);
            return completions;
        }

        return Collections.emptyList();
    }

    private void handleCreate(CommandSender sender) {
        if (!sender.hasPermission(CREATE_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        boolean bypassAccess = sender.hasPermission(ADMIN_BYPASS_PERMISSION);
        this.plugin.farmerCreateService().create(player, bypassAccess).whenComplete((result, throwable) -> this.plugin.scheduler().runGlobal(() -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Farmer create failed: " + readableMessage(throwable));
                this.messageService.send(sender, "commands.farmer.create-failed");
                return;
            }
            sendCreateResult(sender, result);
        }));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(REMOVE_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        boolean bypassAccess = sender.hasPermission(ADMIN_BYPASS_PERMISSION);
        if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
            this.plugin.farmerRemoveService().confirm(player, bypassAccess).whenComplete((result, throwable) -> this.plugin.scheduler().runGlobal(() -> {
                if (throwable != null) {
                    this.plugin.getLogger().warning("Farmer remove failed: " + readableMessage(throwable));
                    this.messageService.send(sender, "commands.farmer.remove-failed");
                    return;
                }
                sendRemoveResult(sender, result);
            }));
            return;
        }

        this.plugin.farmerRemoveService().prepare(player, bypassAccess).whenComplete((result, throwable) -> this.plugin.scheduler().runGlobal(() -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Farmer remove prepare failed: " + readableMessage(throwable));
                this.messageService.send(sender, "commands.farmer.remove-failed");
                return;
            }
            sendRemoveResult(sender, result);
        }));
    }

    private void handleOpen(CommandSender sender) {
        if (!sender.hasPermission(USE_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        if (this.plugin.menuService() == null || !this.plugin.menuService().openMain(player)) {
            this.messageService.send(sender, "commands.farmer.open-failed");
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!sender.hasPermission(USE_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        RegionProvider provider = this.plugin.regionProviderManager().provider();
        if (provider == null || !provider.isAvailable()) {
            this.messageService.send(sender, "commands.farmer.provider-unavailable");
            return;
        }

        RegionAccessResult access = provider.access(player.getLocation(), player.getUniqueId());
        if (!access.allowed()) {
            sendRegionAccessFailure(sender, access);
            return;
        }

        this.plugin.farmerPersistenceService().findByRegionId(access.regionId()).whenComplete((farmer, throwable) -> this.plugin.scheduler().runGlobal(() -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Farmer info failed: " + readableMessage(throwable));
                this.messageService.send(sender, "commands.farmer.info-failed");
                return;
            }
            farmer.ifPresentOrElse(value -> sendFarmerInfo(sender, value), () -> this.messageService.send(sender, "commands.farmer.info-no-farmer"));
        }));
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        if (args.length < 2) {
            this.messageService.send(sender, "commands.farmer.admin-usage");
            return;
        }

        if (args[1].equalsIgnoreCase("logs")) {
            handleAdminLogs(sender, args);
            return;
        }

        if (!args[1].equalsIgnoreCase("info")) {
            this.messageService.send(sender, "commands.farmer.admin-usage");
            return;
        }

        if (args.length < 3 || args[2].isBlank()) {
            this.messageService.send(sender, "commands.farmer.admin-info-usage");
            return;
        }

        this.plugin.farmerPersistenceService().findByRegionId(args[2]).whenComplete((farmer, throwable) -> this.plugin.scheduler().runGlobal(() -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Farmer admin info failed: " + readableMessage(throwable));
                this.messageService.send(sender, "commands.farmer.info-failed");
                return;
            }
            farmer.ifPresentOrElse(value -> sendAdminFarmerInfo(sender, value), () -> this.messageService.send(sender, "commands.farmer.info-no-farmer"));
        }));
    }

    private void handleAdminLogs(CommandSender sender, String[] args) {
        AdminLogsRequest request = adminLogsRequest(sender, args);
        if (request == null) {
            return;
        }
        if (!this.plugin.database().isAvailable()) {
            this.messageService.send(sender, "commands.farmer.admin-logs-database-unavailable");
            return;
        }

        this.plugin.farmerPersistenceService().findByRegionId(request.regionId()).thenCompose(farmer -> {
            if (farmer.isEmpty()) {
                return java.util.concurrent.CompletableFuture.completedFuture(new AdminLogsResult(null, List.of()));
            }
            return this.plugin.logRepository().recent(farmer.get().farmerId(), request.limit())
                .thenApply(entries -> new AdminLogsResult(farmer.get(), entries));
        }).whenComplete((result, throwable) -> scheduleResponse(sender, () -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Farmer admin logs failed: " + readableMessage(throwable));
                this.messageService.send(sender, "commands.farmer.admin-logs-failed");
                return;
            }
            if (result == null || result.farmer() == null) {
                this.messageService.send(sender, "commands.farmer.info-no-farmer", regionPlaceholders(request.regionId()));
                return;
            }
            sendAdminLogs(sender, result.farmer(), result.entries());
        }));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        this.plugin.reloadPluginFiles();
        this.messageService.send(sender, "commands.farmer.reload-success");
    }

    private void handleReconcile(CommandSender sender, String[] args) {
        if (!sender.hasPermission(RECONCILE_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        if (!this.plugin.configManager().reconcileOnCommand()) {
            this.messageService.send(sender, "commands.farmer.reconcile-disabled");
            return;
        }

        if (args.length < 2 || args[1].isBlank()) {
            this.messageService.send(sender, "commands.farmer.reconcile-usage");
            return;
        }

        this.messageService.send(sender, "commands.farmer.reconcile-started");
        this.plugin.farmerReconcileService().reconcileRegion(args[1]).whenComplete((result, throwable) -> this.plugin.scheduler().runGlobal(() -> {
            if (throwable != null) {
                this.messageService.send(sender, "commands.farmer.reconcile-failed");
                return;
            }
            sendReconcileResult(sender, result);
        }));
    }

    private void sendCreateResult(CommandSender sender, FarmerCreateResult result) {
        if (result == null) {
            this.messageService.send(sender, "commands.farmer.create-failed");
            return;
        }

        Map<String, String> placeholders = regionPlaceholders(result.regionId());
        switch (result.status()) {
            case CREATED -> this.messageService.send(sender, "commands.farmer.create-success", farmerPlaceholders(result.farmer()));
            case PROVIDER_UNAVAILABLE -> this.messageService.send(sender, "commands.farmer.provider-unavailable");
            case NO_REGION -> this.messageService.send(sender, "commands.farmer.create-no-region");
            case NOT_ALLOWED -> this.messageService.send(sender, "commands.farmer.create-denied", placeholders);
            case DUPLICATE -> this.messageService.send(sender, "commands.farmer.create-duplicate", placeholders);
        }
    }

    private void sendRemoveResult(CommandSender sender, FarmerRemoveResult result) {
        if (result == null) {
            this.messageService.send(sender, "commands.farmer.remove-failed");
            return;
        }

        Map<String, String> placeholders = regionPlaceholders(result.regionId());
        switch (result.status()) {
            case CONFIRM_REQUIRED -> this.messageService.send(sender, "commands.farmer.remove-confirm", placeholders);
            case REMOVED -> this.messageService.send(sender, "commands.farmer.remove-success", placeholders);
            case PROVIDER_UNAVAILABLE -> this.messageService.send(sender, "commands.farmer.provider-unavailable");
            case NO_REGION -> this.messageService.send(sender, "commands.farmer.remove-no-region");
            case NOT_ALLOWED -> this.messageService.send(sender, "commands.farmer.remove-denied", placeholders);
            case NO_FARMER -> this.messageService.send(sender, "commands.farmer.remove-no-farmer", placeholders);
            case NO_PENDING -> this.messageService.send(sender, "commands.farmer.remove-no-pending");
            case EXPIRED -> this.messageService.send(sender, "commands.farmer.remove-expired");
        }
    }

    private void sendRegionAccessFailure(CommandSender sender, RegionAccessResult access) {
        if (access.denyReason() == RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER || access.denyReason() == RegionAccessResult.DenyReason.BANNED) {
            this.messageService.send(sender, "commands.farmer.info-denied", regionPlaceholders(access.regionId()));
            return;
        }
        this.messageService.send(sender, "commands.farmer.info-no-region");
    }

    private void sendFarmerInfo(CommandSender sender, Farmer farmer) {
        this.messageService.sendList(sender, "commands.farmer.info", farmerPlaceholders(farmer));
    }

    private void sendAdminFarmerInfo(CommandSender sender, Farmer farmer) {
        this.messageService.sendList(sender, "commands.farmer.admin-info", adminFarmerPlaceholders(farmer));
    }

    private void sendAdminLogs(CommandSender sender, Farmer farmer, List<FarmerLogEntry> entries) {
        this.messageService.send(sender, "commands.farmer.admin-logs-header", farmerPlaceholders(farmer));
        if (entries == null || entries.isEmpty()) {
            this.messageService.send(sender, "commands.farmer.admin-logs-empty", farmerPlaceholders(farmer));
            return;
        }
        for (FarmerLogEntry entry : entries) {
            this.messageService.send(sender, "commands.farmer.admin-logs-entry", logPlaceholders(entry));
        }
    }

    private void sendReconcileResult(CommandSender sender, FarmerReconcileResult result) {
        if (result == null) {
            this.messageService.send(sender, "commands.farmer.reconcile-failed");
            return;
        }

        switch (result.status()) {
            case UPDATED -> this.messageService.send(sender, "commands.farmer.reconcile-success");
            case UNCHANGED -> this.messageService.send(sender, "commands.farmer.reconcile-unchanged");
            case NO_REGION -> this.messageService.send(sender, "commands.farmer.reconcile-no-region");
            case NO_FARMER -> this.messageService.send(sender, "commands.farmer.reconcile-no-farmer");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        this.messageService.send(sender, "commands.farmer.only-player");
        return null;
    }

    private AdminLogsRequest adminLogsRequest(CommandSender sender, String[] args) {
        if (args.length > 4) {
            this.messageService.send(sender, "commands.farmer.admin-logs-usage");
            return null;
        }

        String regionId = null;
        int limit = 10;
        if (args.length >= 3 && !args[2].isBlank()) {
            if (isInteger(args[2])) {
                limit = limit(args[2]);
            } else {
                regionId = args[2].trim();
            }
        }
        if (args.length >= 4 && !args[3].isBlank()) {
            if (!isInteger(args[3])) {
                this.messageService.send(sender, "commands.farmer.admin-logs-usage");
                return null;
            }
            limit = limit(args[3]);
        }
        if (regionId == null || regionId.isBlank()) {
            regionId = currentRegion(sender);
        }
        if (regionId == null || regionId.isBlank()) {
            return null;
        }
        return new AdminLogsRequest(regionId, limit);
    }

    private String currentRegion(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.messageService.send(sender, "commands.farmer.admin-logs-usage");
            return null;
        }

        RegionProvider provider = this.plugin.regionProviderManager().provider();
        if (provider == null || !provider.isAvailable()) {
            this.messageService.send(sender, "commands.farmer.provider-unavailable");
            return null;
        }
        return provider.regionIdAt(player.getLocation()).orElseGet(() -> {
            this.messageService.send(sender, "commands.farmer.admin-logs-no-region");
            return null;
        });
    }

    private boolean isInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private int limit(String value) {
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            return 10;
        }
    }

    private Map<String, String> farmerPlaceholders(Farmer farmer) {
        if (farmer == null) {
            return Map.of();
        }
        return Map.of(
            "farmer", farmer.farmerId(),
            "region", farmer.regionId(),
            "owner", ownerName(farmer.ownerUuid()),
            "owner_uuid", farmer.ownerUuid().toString(),
            "level", String.valueOf(farmer.level()),
            "members", String.valueOf(farmer.members().size())
        );
    }

    private Map<String, String> adminFarmerPlaceholders(Farmer farmer) {
        Map<String, String> base = new java.util.HashMap<>(farmerPlaceholders(farmer));
        EconomyProvider economyProvider = this.plugin.economyProviderManager() == null ? null : this.plugin.economyProviderManager().provider();
        RegionProvider regionProvider = this.plugin.regionProviderManager() == null ? null : this.plugin.regionProviderManager().provider();
        FarmerVisualProvider visualProvider = this.plugin.visualProviderManager() == null ? null : this.plugin.visualProviderManager().provider();
        PlaceholderProvider placeholderProvider = this.plugin.placeholderProviderManager() == null ? null : this.plugin.placeholderProviderManager().provider();
        base.put("collecting", stateLabel(farmer.collectingEnabled()));
        base.put("storage_total", formatAmount(storageTotal(farmer)));
        base.put("module_states", moduleStates(farmer));
        base.put("economy_provider", economyProvider == null ? "-" : economyProvider.name());
        base.put("economy_state", stateLabel(economyProvider != null && economyProvider.isAvailable()));
        base.put("region_provider", regionProvider == null ? "-" : regionProvider.type().name());
        base.put("region_state", stateLabel(regionProvider != null && regionProvider.isAvailable()));
        base.put("visual_provider", visualProvider == null ? "-" : visualProvider.type().name());
        base.put("visual_state", stateLabel(visualProvider != null && visualProvider.isAvailable()));
        base.put("placeholder_provider", placeholderProvider == null ? "-" : placeholderProvider.name());
        base.put("placeholder_state", stateLabel(placeholderProvider != null && placeholderProvider.isAvailable()));
        ProductionEstimate estimate = this.plugin.moduleManager().productionEstimate(farmer);
        base.put("production_minute", formatAmount(estimate.perMinute()));
        base.put("production_hour", formatAmount(estimate.perHour()));
        return Map.copyOf(base);
    }

    private Map<String, String> logPlaceholders(FarmerLogEntry entry) {
        return Map.of(
            "time", LOG_TIME_FORMATTER.format(Instant.ofEpochMilli(entry.createdAt()).atZone(ZoneId.systemDefault())),
            "action", entry.action(),
            "detail", entry.detail().isBlank() ? "-" : entry.detail(),
            "actor", entry.actorUuid() == null ? "-" : entry.actorUuid().toString(),
            "farmer", entry.farmerId()
        );
    }

    private Map<String, String> regionPlaceholders(String regionId) {
        return Map.of("region", regionId == null ? "-" : regionId);
    }

    private long storageTotal(Farmer farmer) {
        return farmer.storage().snapshot().values().stream().mapToLong(Long::longValue).sum();
    }

    private String moduleStates(Farmer farmer) {
        if (farmer.moduleStates().isEmpty()) {
            return "-";
        }
        return farmer.moduleStates().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + stateLabel(entry.getValue()))
            .collect(Collectors.joining(", "));
    }

    private String ownerName(UUID ownerUuid) {
        String name = Bukkit.getOfflinePlayer(ownerUuid).getName();
        return name == null || name.isBlank() ? ownerUuid.toString() : name;
    }

    private String stateLabel(boolean enabled) {
        return enabled ? "ᴀᴋᴛɪғ" : "ᴘᴀsɪғ";
    }

    private String formatAmount(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private void scheduleResponse(CommandSender sender, Runnable task) {
        if (sender instanceof Player player && player.isOnline()) {
            this.plugin.scheduler().runAtEntity(player, task);
            return;
        }
        this.plugin.scheduler().runGlobal(task);
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    private void addIfMatches(List<String> completions, String value, String input) {
        if (value.startsWith(input.toLowerCase(Locale.ROOT))) {
            completions.add(value);
        }
    }

    private record AdminLogsRequest(String regionId, int limit) {
    }

    private record AdminLogsResult(Farmer farmer, List<FarmerLogEntry> entries) {
    }
}
