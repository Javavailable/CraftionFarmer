package com.craftion.farmer.hook.region;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public final class NoRegionProvider implements RegionProvider {

    @Override
    public RegionProviderType type() {
        return RegionProviderType.NONE;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<String> regionIdAt(Location location) {
        return Optional.empty();
    }

    @Override
    public Optional<String> regionIdForPlayer(UUID playerUuid) {
        return Optional.empty();
    }

    @Override
    public Optional<RegionMemberInfo> owner(String regionId) {
        return Optional.empty();
    }

    @Override
    public Optional<RegionMemberInfo> member(String regionId, UUID playerUuid) {
        return Optional.empty();
    }

    @Override
    public List<RegionMemberInfo> members(String regionId) {
        return List.of();
    }

    @Override
    public RegionAccessResult access(Location location, UUID playerUuid) {
        return RegionAccessResult.denied(RegionAccessResult.DenyReason.PROVIDER_DISABLED);
    }

    @Override
    public RegionAccessResult access(String regionId, UUID playerUuid) {
        return RegionAccessResult.denied(regionId, RegionAccessResult.DenyReason.PROVIDER_DISABLED);
    }

    @Override
    public boolean isSkyblockWorld(World world) {
        return false;
    }
}
