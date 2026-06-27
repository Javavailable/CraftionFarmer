package com.craftion.farmer.hook.skyllia;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import fr.euphyllia.skyllia.api.event.SkyblockDeleteEvent;
import fr.euphyllia.skyllia.api.event.SkyblockRemoveMemberEvent;
import fr.euphyllia.skyllia.api.skyblock.Island;
import fr.euphyllia.skyllia.api.skyblock.Players;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkylliaSyncListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FarmerReconcileService reconcileService;
    private final DebugLogger debugLogger;

    public SkylliaSyncListener(
        JavaPlugin plugin,
        ConfigManager configManager,
        FarmerReconcileService reconcileService,
        DebugLogger debugLogger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.reconcileService = reconcileService;
        this.debugLogger = debugLogger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkyblockDelete(SkyblockDeleteEvent event) {
        if (!this.configManager.isSkylliaSyncEnabled()) {
            return;
        }

        Optional<String> regionId = regionId(event.getIsland());
        if (regionId.isEmpty()) {
            return;
        }

        CompletableFuture<Boolean> task = this.configManager.removeFarmerOnIslandDelete()
            ? this.reconcileService.deleteFarmerByRegionId(regionId.get())
            : this.reconcileService.disableFarmerByRegionId(regionId.get());
        logCompletion(task, "island delete", regionId.get());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSkyblockRemoveMember(SkyblockRemoveMemberEvent event) {
        if (!this.configManager.isSkylliaSyncEnabled()) {
            return;
        }

        Optional<String> regionId = regionId(event.getIsland());
        Optional<UUID> playerUuid = playerUuid(event.getRemovedPlayer());
        if (regionId.isEmpty() || playerUuid.isEmpty()) {
            return;
        }

        CompletableFuture<Boolean> task = this.reconcileService.removeMemberByRegionId(regionId.get(), playerUuid.get());
        logCompletion(task, "member remove", regionId.get());
    }

    private void logCompletion(CompletableFuture<Boolean> task, String action, String regionId) {
        task.whenComplete((changed, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Skyllia sync failed for " + action + " on " + regionId + ": " + readableMessage(throwable));
                return;
            }
            this.debugLogger.debug("Skyllia sync " + action + " completed for " + regionId + ", changed=" + changed);
        });
    }

    private Optional<String> regionId(Island island) {
        if (island == null || island.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(island.getId().toString());
    }

    private Optional<UUID> playerUuid(Players player) {
        if (player == null || player.getMojangId() == null) {
            return Optional.empty();
        }
        return Optional.of(player.getMojangId());
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
