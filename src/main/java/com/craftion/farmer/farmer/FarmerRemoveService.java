package com.craftion.farmer.farmer;

import com.craftion.farmer.hook.region.RegionAccessResult;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.hook.visual.VisualProviderManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class FarmerRemoveService {

    private final FarmerPersistenceService persistenceService;
    private final RegionProviderManager regionProviderManager;
    private final VisualProviderManager visualProviderManager;
    private final Duration confirmTimeout;
    private final Map<UUID, PendingRemoval> pendingRemovals = new ConcurrentHashMap<>();

    public FarmerRemoveService(
        FarmerPersistenceService persistenceService,
        RegionProviderManager regionProviderManager,
        VisualProviderManager visualProviderManager,
        Duration confirmTimeout
    ) {
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.regionProviderManager = Objects.requireNonNull(regionProviderManager, "regionProviderManager");
        this.visualProviderManager = Objects.requireNonNull(visualProviderManager, "visualProviderManager");
        this.confirmTimeout = Objects.requireNonNull(confirmTimeout, "confirmTimeout");
    }

    public CompletableFuture<FarmerRemoveResult> prepare(Player player, boolean bypassAccess) {
        Objects.requireNonNull(player, "player");
        Optional<String> optionalRegionId = resolveManageableRegion(player, bypassAccess);
        if (optionalRegionId.isEmpty()) {
            return CompletableFuture.completedFuture(failureFor(player, bypassAccess));
        }

        String regionId = optionalRegionId.get();
        return this.persistenceService.findByRegionId(regionId).thenApply(existing -> {
            if (existing.isEmpty()) {
                return FarmerRemoveResult.of(FarmerRemoveResult.Status.NO_FARMER, regionId);
            }

            this.pendingRemovals.put(player.getUniqueId(), new PendingRemoval(regionId, Instant.now().plus(this.confirmTimeout)));
            return FarmerRemoveResult.of(FarmerRemoveResult.Status.CONFIRM_REQUIRED, regionId);
        });
    }

    public CompletableFuture<FarmerRemoveResult> confirm(Player player, boolean bypassAccess) {
        Objects.requireNonNull(player, "player");
        PendingRemoval pendingRemoval = this.pendingRemovals.remove(player.getUniqueId());
        if (pendingRemoval == null) {
            return CompletableFuture.completedFuture(FarmerRemoveResult.of(FarmerRemoveResult.Status.NO_PENDING));
        }

        if (pendingRemoval.expiresAt().isBefore(Instant.now())) {
            return CompletableFuture.completedFuture(FarmerRemoveResult.of(FarmerRemoveResult.Status.EXPIRED, pendingRemoval.regionId()));
        }

        if (!bypassAccess && !canManageRegion(pendingRemoval.regionId(), player.getUniqueId())) {
            return CompletableFuture.completedFuture(FarmerRemoveResult.of(FarmerRemoveResult.Status.NOT_ALLOWED, pendingRemoval.regionId()));
        }

        return this.persistenceService.findByRegionId(pendingRemoval.regionId()).thenCompose(farmer -> {
            if (farmer.isEmpty()) {
                return CompletableFuture.completedFuture(FarmerRemoveResult.of(FarmerRemoveResult.Status.NO_FARMER, pendingRemoval.regionId()));
            }

            return this.persistenceService.deleteByRegionId(pendingRemoval.regionId()).thenApply(deleted -> {
                if (!deleted) {
                    return FarmerRemoveResult.of(FarmerRemoveResult.Status.NO_FARMER, pendingRemoval.regionId());
                }
                this.visualProviderManager.remove(farmer.get());
                return FarmerRemoveResult.of(FarmerRemoveResult.Status.REMOVED, pendingRemoval.regionId());
            });
        });
    }

    private Optional<String> resolveManageableRegion(Player player, boolean bypassAccess) {
        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            return Optional.empty();
        }

        if (bypassAccess) {
            return provider.regionIdAt(player.getLocation());
        }

        RegionAccessResult access = provider.access(player.getLocation(), player.getUniqueId());
        if (!canManage(access)) {
            return Optional.empty();
        }
        return access.regionIdOptional();
    }

    private boolean canManageRegion(String regionId, UUID playerUuid) {
        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            return false;
        }
        return canManage(provider.access(regionId, playerUuid));
    }

    private FarmerRemoveResult failureFor(Player player, boolean bypassAccess) {
        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            return FarmerRemoveResult.of(FarmerRemoveResult.Status.PROVIDER_UNAVAILABLE);
        }

        if (bypassAccess) {
            return FarmerRemoveResult.of(FarmerRemoveResult.Status.NO_REGION);
        }

        RegionAccessResult access = provider.access(player.getLocation(), player.getUniqueId());
        if (access.allowed() && !canManage(access)) {
            return FarmerRemoveResult.of(FarmerRemoveResult.Status.NOT_ALLOWED, access.regionId());
        }
        if (access.denyReason() == RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER || access.denyReason() == RegionAccessResult.DenyReason.BANNED) {
            return FarmerRemoveResult.of(FarmerRemoveResult.Status.NOT_ALLOWED, access.regionId());
        }
        return FarmerRemoveResult.of(FarmerRemoveResult.Status.NO_REGION, access.regionId());
    }

    private boolean canManage(RegionAccessResult access) {
        if (access == null || !access.allowed() || access.member() == null) {
            return false;
        }
        return access.member().role() == FarmerRole.OWNER || access.member().role() == FarmerRole.MANAGER;
    }

    private record PendingRemoval(String regionId, Instant expiresAt) {
    }
}
