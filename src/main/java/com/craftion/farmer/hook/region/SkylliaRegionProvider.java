package com.craftion.farmer.hook.region;

import com.craftion.farmer.farmer.FarmerRole;
import fr.euphyllia.skyllia.api.SkylliaAPI;
import fr.euphyllia.skyllia.api.service.TrustService;
import fr.euphyllia.skyllia.api.skyblock.Island;
import fr.euphyllia.skyllia.api.skyblock.Players;
import fr.euphyllia.skyllia.api.skyblock.model.RoleType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public final class SkylliaRegionProvider implements RegionProvider {

    private final boolean allowTrustedPlayers;
    private final boolean requireSkyblockWorld;
    private final boolean syncMembersOnOpen;

    public SkylliaRegionProvider(boolean allowTrustedPlayers, boolean requireSkyblockWorld, boolean syncMembersOnOpen) {
        this.allowTrustedPlayers = allowTrustedPlayers;
        this.requireSkyblockWorld = requireSkyblockWorld;
        this.syncMembersOnOpen = syncMembersOnOpen;
    }

    @Override
    public RegionProviderType type() {
        return RegionProviderType.SKYLLIA;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<String> regionIdAt(Location location) {
        return islandAt(location).map(island -> island.getId().toString());
    }

    @Override
    public Optional<String> regionIdForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }

        try {
            Island island = SkylliaAPI.getIslandByPlayerId(playerUuid);
            if (isDisabled(island)) {
                return Optional.empty();
            }
            return Optional.of(regionId(island));
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegionMemberInfo> owner(String regionId) {
        try {
            return islandByRegionId(regionId).flatMap(island -> toMemberInfo(island, island.getOwner(), true, false));
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegionMemberInfo> member(String regionId, UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }

        try {
            return islandByRegionId(regionId).flatMap(island -> memberInfo(island, playerUuid));
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<RegionMemberInfo> members(String regionId) {
        Optional<Island> optionalIsland = islandByRegionId(regionId);
        if (optionalIsland.isEmpty()) {
            return List.of();
        }

        try {
            Island island = optionalIsland.get();
            Map<UUID, RegionMemberInfo> members = new LinkedHashMap<>();
            toMemberInfo(island, island.getOwner(), true, false).ifPresent(member -> members.put(member.playerUuid(), member));

            List<Players> skylliaMembers = island.getMembers();
            if (skylliaMembers != null) {
                for (Players player : skylliaMembers) {
                    toMemberInfo(island, player, false, false).ifPresent(member -> members.putIfAbsent(member.playerUuid(), member));
                }
            }

            return new ArrayList<>(members.values());
        } catch (RuntimeException | LinkageError exception) {
            return List.of();
        }
    }

    @Override
    public RegionAccessResult access(Location location, UUID playerUuid) {
        if (playerUuid == null) {
            return RegionAccessResult.denied(RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER);
        }

        if (location == null || location.getWorld() == null) {
            return RegionAccessResult.denied(RegionAccessResult.DenyReason.NO_REGION);
        }

        if (this.requireSkyblockWorld && !isSkyblockWorld(location.getWorld())) {
            return RegionAccessResult.denied(RegionAccessResult.DenyReason.SKYBLOCK_WORLD_REQUIRED);
        }

        Optional<Island> optionalIsland = rawIslandAt(location);
        if (optionalIsland.isEmpty()) {
            return RegionAccessResult.denied(RegionAccessResult.DenyReason.NO_REGION);
        }

        return access(optionalIsland.get(), playerUuid);
    }

    @Override
    public RegionAccessResult access(String regionId, UUID playerUuid) {
        if (playerUuid == null) {
            return RegionAccessResult.denied(regionId, RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER);
        }

        Optional<UUID> islandId = parseIslandId(regionId);
        if (islandId.isEmpty()) {
            return RegionAccessResult.denied(regionId, RegionAccessResult.DenyReason.INVALID_REGION_ID);
        }

        Optional<Island> optionalIsland = rawIslandById(islandId.get());
        if (optionalIsland.isEmpty()) {
            return RegionAccessResult.denied(regionId, RegionAccessResult.DenyReason.NO_REGION);
        }

        return access(optionalIsland.get(), playerUuid);
    }

    @Override
    public boolean isSkyblockWorld(World world) {
        if (world == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(SkylliaAPI.isWorldSkyblock(world));
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public boolean syncMembersOnOpen() {
        return this.syncMembersOnOpen;
    }

    private RegionAccessResult access(Island island, UUID playerUuid) {
        try {
            if (isDisabled(island)) {
                return RegionAccessResult.denied(regionId(island), RegionAccessResult.DenyReason.ISLAND_DISABLED);
            }

            Players owner = island.getOwner();
            if (owner != null && playerUuid.equals(owner.getMojangId())) {
                return toMemberInfo(island, owner, true, false)
                    .map(RegionAccessResult::allowed)
                    .orElseGet(() -> RegionAccessResult.denied(regionId(island), RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER));
            }

            Players member = island.getMember(playerUuid);
            if (member != null && member.getRoleType() == RoleType.BAN) {
                return RegionAccessResult.denied(regionId(island), RegionAccessResult.DenyReason.BANNED);
            }

            Optional<RegionMemberInfo> memberInfo = toMemberInfo(island, member, false, false);
            if (memberInfo.isPresent()) {
                return RegionAccessResult.allowed(memberInfo.get());
            }

            if (isTrusted(island.getId(), playerUuid)) {
                return RegionAccessResult.allowed(new RegionMemberInfo(regionId(island), playerUuid, FarmerRole.MEMBER, false, true));
            }

            return RegionAccessResult.denied(regionId(island), RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER);
        } catch (RuntimeException | LinkageError exception) {
            return RegionAccessResult.denied(RegionAccessResult.DenyReason.NO_REGION);
        }
    }

    private Optional<RegionMemberInfo> memberInfo(Island island, UUID playerUuid) {
        Players owner = island.getOwner();
        if (owner != null && playerUuid.equals(owner.getMojangId())) {
            return toMemberInfo(island, owner, true, false);
        }

        Players member = island.getMember(playerUuid);
        Optional<RegionMemberInfo> memberInfo = toMemberInfo(island, member, false, false);
        if (memberInfo.isPresent()) {
            return memberInfo;
        }

        if (isTrusted(island.getId(), playerUuid)) {
            return Optional.of(new RegionMemberInfo(regionId(island), playerUuid, FarmerRole.MEMBER, false, true));
        }

        return Optional.empty();
    }

    private Optional<RegionMemberInfo> toMemberInfo(Island island, Players player, boolean owner, boolean trusted) {
        if (player == null || player.getMojangId() == null) {
            return Optional.empty();
        }

        RoleType roleType = owner ? RoleType.OWNER : player.getRoleType();
        Optional<FarmerRole> role = mapRole(roleType);
        if (role.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RegionMemberInfo(regionId(island), player.getMojangId(), role.get(), owner, trusted));
    }

    private Optional<Island> islandAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }

        if (this.requireSkyblockWorld && !isSkyblockWorld(location.getWorld())) {
            return Optional.empty();
        }

        Island island = rawIslandAt(location).orElse(null);
        if (isDisabled(island)) {
            return Optional.empty();
        }
        return Optional.ofNullable(island);
    }

    private Optional<Island> islandByRegionId(String regionId) {
        Optional<UUID> islandId = parseIslandId(regionId);
        if (islandId.isEmpty()) {
            return Optional.empty();
        }

        Optional<Island> island = rawIslandById(islandId.get());
        if (island.isEmpty() || isDisabled(island.get())) {
            return Optional.empty();
        }

        return island;
    }

    private Optional<Island> rawIslandAt(Location location) {
        try {
            return Optional.ofNullable(SkylliaAPI.getIslandByChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private Optional<Island> rawIslandById(UUID islandId) {
        try {
            return Optional.ofNullable(SkylliaAPI.getIslandByIslandId(islandId));
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private Optional<UUID> parseIslandId(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(regionId.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<FarmerRole> mapRole(RoleType roleType) {
        if (roleType == null) {
            return Optional.empty();
        }

        return switch (roleType) {
            case OWNER -> Optional.of(FarmerRole.OWNER);
            case CO_OWNER, MODERATOR -> Optional.of(FarmerRole.MANAGER);
            case MEMBER -> Optional.of(FarmerRole.MEMBER);
            case VISITOR -> Optional.of(FarmerRole.VIEWER);
            case BAN -> Optional.empty();
        };
    }

    private boolean isTrusted(UUID islandId, UUID playerUuid) {
        if (!this.allowTrustedPlayers || islandId == null || playerUuid == null) {
            return false;
        }

        try {
            TrustService trustService = SkylliaAPI.getTrustService();
            return trustService != null && trustService.isTrusted(islandId, playerUuid);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private boolean isDisabled(Island island) {
        if (island == null) {
            return true;
        }

        try {
            return island.isDisable();
        } catch (RuntimeException | LinkageError exception) {
            return true;
        }
    }

    private String regionId(Island island) {
        return island.getId().toString();
    }
}
