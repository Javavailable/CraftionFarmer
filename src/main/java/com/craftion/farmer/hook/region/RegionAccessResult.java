package com.craftion.farmer.hook.region;

import java.util.Optional;

public record RegionAccessResult(
    boolean allowed,
    String regionId,
    RegionMemberInfo member,
    DenyReason denyReason
) {

    public static RegionAccessResult allowed(RegionMemberInfo member) {
        return new RegionAccessResult(true, member.regionId(), member, DenyReason.NONE);
    }

    public static RegionAccessResult denied(DenyReason denyReason) {
        return denied(null, denyReason);
    }

    public static RegionAccessResult denied(String regionId, DenyReason denyReason) {
        return new RegionAccessResult(false, regionId, null, denyReason);
    }

    public Optional<String> regionIdOptional() {
        return Optional.ofNullable(this.regionId);
    }

    public Optional<RegionMemberInfo> memberOptional() {
        return Optional.ofNullable(this.member);
    }

    public enum DenyReason {
        NONE,
        PROVIDER_DISABLED,
        SKYBLOCK_WORLD_REQUIRED,
        NO_REGION,
        ISLAND_DISABLED,
        PLAYER_NOT_MEMBER,
        BANNED,
        INVALID_REGION_ID
    }
}
