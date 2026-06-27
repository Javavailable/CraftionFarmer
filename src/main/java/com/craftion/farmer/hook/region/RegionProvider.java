package com.craftion.farmer.hook.region;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public interface RegionProvider {

    RegionProviderType type();

    boolean isAvailable();

    Optional<String> regionIdAt(Location location);

    Optional<String> regionIdForPlayer(UUID playerUuid);

    Optional<RegionMemberInfo> owner(String regionId);

    Optional<RegionMemberInfo> member(String regionId, UUID playerUuid);

    List<RegionMemberInfo> members(String regionId);

    RegionAccessResult access(Location location, UUID playerUuid);

    RegionAccessResult access(String regionId, UUID playerUuid);

    boolean isSkyblockWorld(World world);

    default boolean syncMembersOnOpen() {
        return false;
    }
}
