package com.craftion.farmer.farmer;

import com.craftion.farmer.hook.region.RegionAccessResult;
import com.craftion.farmer.hook.region.RegionMemberInfo;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class FarmerCreateService {

    private final FarmerPersistenceService persistenceService;
    private final RegionProviderManager regionProviderManager;

    public FarmerCreateService(FarmerPersistenceService persistenceService, RegionProviderManager regionProviderManager) {
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.regionProviderManager = Objects.requireNonNull(regionProviderManager, "regionProviderManager");
    }

    public CompletableFuture<FarmerCreateResult> create(Player player, boolean bypassAccess) {
        Objects.requireNonNull(player, "player");
        RegionProvider provider = this.regionProviderManager.provider();
        Optional<RegionContext> optionalContext = resolveRegionContext(provider, player, bypassAccess);
        if (optionalContext.isEmpty()) {
            return CompletableFuture.completedFuture(failureFor(provider, player, bypassAccess));
        }

        RegionContext context = optionalContext.get();
        Farmer farmer = Farmer.create(context.regionId(), context.regionId(), context.ownerUuid(), locationSnapshot(player.getLocation()));
        Instant now = Instant.now();
        for (Map.Entry<UUID, FarmerRole> entry : context.members().entrySet()) {
            farmer.putMember(new FarmerMember(farmer.farmerId(), entry.getKey(), entry.getValue(), now));
        }

        return this.persistenceService.findByRegionId(context.regionId()).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.completedFuture(FarmerCreateResult.failed(FarmerCreateResult.Status.DUPLICATE, context.regionId()));
            }

            return this.persistenceService.save(farmer).thenApply(ignored -> FarmerCreateResult.created(farmer));
        });
    }

    private Optional<RegionContext> resolveRegionContext(RegionProvider provider, Player player, boolean bypassAccess) {
        if (provider == null || !provider.isAvailable()) {
            return Optional.empty();
        }

        Location location = player.getLocation();
        String regionId;
        if (bypassAccess) {
            Optional<String> optionalRegionId = provider.regionIdAt(location);
            if (optionalRegionId.isEmpty()) {
                return Optional.empty();
            }
            regionId = optionalRegionId.get();
        } else {
            RegionAccessResult access = provider.access(location, player.getUniqueId());
            if (!canManage(access)) {
                return Optional.empty();
            }
            regionId = access.regionId();
        }

        Optional<RegionMemberInfo> owner = provider.owner(regionId);
        if (owner.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RegionContext(regionId, owner.get().playerUuid(), memberRoles(provider, regionId, owner.get())));
    }

    private FarmerCreateResult failureFor(RegionProvider provider, Player player, boolean bypassAccess) {
        if (provider == null || !provider.isAvailable()) {
            return FarmerCreateResult.failed(FarmerCreateResult.Status.PROVIDER_UNAVAILABLE);
        }

        if (bypassAccess) {
            return FarmerCreateResult.failed(FarmerCreateResult.Status.NO_REGION);
        }

        RegionAccessResult access = provider.access(player.getLocation(), player.getUniqueId());
        if (access.allowed() && !canManage(access)) {
            return FarmerCreateResult.failed(FarmerCreateResult.Status.NOT_ALLOWED, access.regionId());
        }
        if (access.denyReason() == RegionAccessResult.DenyReason.PROVIDER_DISABLED) {
            return FarmerCreateResult.failed(FarmerCreateResult.Status.PROVIDER_UNAVAILABLE);
        }
        if (access.denyReason() == RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER || access.denyReason() == RegionAccessResult.DenyReason.BANNED) {
            return FarmerCreateResult.failed(FarmerCreateResult.Status.NOT_ALLOWED, access.regionId());
        }
        return FarmerCreateResult.failed(FarmerCreateResult.Status.NO_REGION, access.regionId());
    }

    private Map<UUID, FarmerRole> memberRoles(RegionProvider provider, String regionId, RegionMemberInfo owner) {
        Map<UUID, FarmerRole> members = new LinkedHashMap<>();
        members.put(owner.playerUuid(), FarmerRole.OWNER);
        for (RegionMemberInfo member : provider.members(regionId)) {
            if (isPersistableMember(member)) {
                members.put(member.playerUuid(), member.owner() ? FarmerRole.OWNER : member.role());
            }
        }
        members.put(owner.playerUuid(), FarmerRole.OWNER);
        return members;
    }

    private boolean canManage(RegionAccessResult access) {
        if (access == null || !access.allowed() || access.member() == null) {
            return false;
        }
        return access.member().role() == FarmerRole.OWNER || access.member().role() == FarmerRole.MANAGER;
    }

    private boolean isPersistableMember(RegionMemberInfo member) {
        if (member == null || member.trusted()) {
            return false;
        }
        return member.role() == FarmerRole.OWNER || member.role() == FarmerRole.MANAGER || member.role() == FarmerRole.MEMBER;
    }

    private LocationSnapshot locationSnapshot(Location location) {
        return LocationSnapshot.of(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    private record RegionContext(String regionId, UUID ownerUuid, Map<UUID, FarmerRole> members) {
    }
}
