package com.craftion.farmer.farmer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FarmerCacheTest {

    private static final LocationSnapshot LOCATION = LocationSnapshot.of("world", 0.0, 64.0, 0.0, 0.0F, 0.0F);

    @Test
    void knownRegionReturnsIndexedFarmer() {
        FarmerCache cache = new FarmerCache();
        Farmer farmer = farmer("farmer-one", "region-one", uuid(1));

        cache.put(farmer);

        assertSame(farmer, cache.getByRegionId("region-one").orElseThrow());
    }

    @Test
    void unknownRegionReturnsEmpty() {
        FarmerCache cache = new FarmerCache();

        assertTrue(cache.getByRegionId("missing-region").isEmpty());
    }

    @Test
    void removeByFarmerIdClearsFarmerAndRelatedIndexes() {
        FarmerCache cache = new FarmerCache();
        UUID owner = uuid(2);
        UUID member = uuid(3);
        Farmer farmer = farmer("farmer-two", "region-two", owner, member);
        cache.put(farmer);

        assertSame(farmer, cache.remove("farmer-two").orElseThrow());

        assertFalse(cache.contains("farmer-two"));
        assertTrue(cache.getByRegionId("region-two").isEmpty());
        assertTrue(cache.getByPlayerUuid(owner).isEmpty());
        assertTrue(cache.getByPlayerUuid(member).isEmpty());
    }

    @Test
    void removeByRegionIdRemovesDirectlyIndexedFarmer() {
        FarmerCache cache = new FarmerCache();
        UUID owner = uuid(4);
        Farmer farmer = farmer("farmer-three", "region-three", owner);
        cache.put(farmer);

        assertSame(farmer, cache.removeByRegionId("region-three").orElseThrow());

        assertFalse(cache.contains("farmer-three"));
        assertTrue(cache.getByRegionId("region-three").isEmpty());
        assertTrue(cache.getByPlayerUuid(owner).isEmpty());
    }

    @Test
    void clearRemovesPrimaryPlayerAndRegionIndexes() {
        FarmerCache cache = new FarmerCache();
        UUID owner = uuid(5);
        UUID member = uuid(6);
        cache.put(farmer("farmer-four", "region-four", owner, member));

        cache.clear();

        assertTrue(cache.snapshot().isEmpty());
        assertTrue(cache.getByRegionId("region-four").isEmpty());
        assertTrue(cache.getByPlayerUuid(owner).isEmpty());
        assertTrue(cache.getByPlayerUuid(member).isEmpty());
    }

    @Test
    void replacingSameFarmerIdMovesRegionIndex() {
        FarmerCache cache = new FarmerCache();
        Farmer original = farmer("farmer-five", "region-old", uuid(7));
        Farmer replacement = farmer("farmer-five", "region-new", uuid(7));
        cache.put(original);

        cache.put(replacement);

        assertTrue(cache.getByRegionId("region-old").isEmpty());
        assertSame(replacement, cache.getByRegionId("region-new").orElseThrow());
        assertSame(replacement, cache.get("farmer-five").orElseThrow());
    }

    @Test
    void lastPutWinsWhenDifferentFarmersShareRegion() {
        FarmerCache cache = new FarmerCache();
        Farmer first = farmer("farmer-six", "shared-region", uuid(8));
        Farmer last = farmer("farmer-seven", "shared-region", uuid(9));

        cache.put(first);
        cache.put(last);

        assertSame(last, cache.getByRegionId("shared-region").orElseThrow());
        assertSame(first, cache.get("farmer-six").orElseThrow());
        assertSame(last, cache.get("farmer-seven").orElseThrow());
    }

    @Test
    void removingDisplacedFarmerPreservesWinningRegionMapping() {
        FarmerCache cache = new FarmerCache();
        Farmer displaced = farmer("farmer-eight", "duplicate-region", uuid(10));
        Farmer winner = farmer("farmer-nine", "duplicate-region", uuid(11));
        cache.put(displaced);
        cache.put(winner);

        cache.remove("farmer-eight");

        assertSame(winner, cache.getByRegionId("duplicate-region").orElseThrow());
    }

    @Test
    void removingRegionWinnerDoesNotRestoreDisplacedDuplicate() {
        FarmerCache cache = new FarmerCache();
        Farmer displaced = farmer("farmer-ten", "no-fallback-region", uuid(12));
        Farmer winner = farmer("farmer-eleven", "no-fallback-region", uuid(13));
        cache.put(displaced);
        cache.put(winner);

        cache.remove("farmer-eleven");

        assertTrue(cache.getByRegionId("no-fallback-region").isEmpty());
        assertSame(displaced, cache.get("farmer-ten").orElseThrow());
    }

    @Test
    void playerIndexesFollowPutReplacementAndRemove() {
        FarmerCache cache = new FarmerCache();
        UUID oldOwner = uuid(14);
        UUID oldMember = uuid(15);
        UUID newOwner = uuid(16);
        UUID newMember = uuid(17);
        Farmer original = farmer("farmer-twelve", "player-region-old", oldOwner, oldMember);
        Farmer replacement = farmer("farmer-twelve", "player-region-new", newOwner, newMember);

        cache.put(original);
        assertSame(original, cache.getByPlayerUuid(oldOwner).orElseThrow());
        assertSame(original, cache.getByPlayerUuid(oldMember).orElseThrow());

        cache.put(replacement);
        assertTrue(cache.getByPlayerUuid(oldOwner).isEmpty());
        assertTrue(cache.getByPlayerUuid(oldMember).isEmpty());
        assertSame(replacement, cache.getByPlayerUuid(newOwner).orElseThrow());
        assertSame(replacement, cache.getByPlayerUuid(newMember).orElseThrow());

        cache.remove("farmer-twelve");
        assertTrue(cache.getByPlayerUuid(newOwner).isEmpty());
        assertTrue(cache.getByPlayerUuid(newMember).isEmpty());
    }

    @Test
    void removingOlderFarmerDoesNotDeleteNewerPlayerMapping() {
        FarmerCache cache = new FarmerCache();
        UUID sharedPlayer = uuid(18);
        Farmer older = farmer("farmer-thirteen", "older-region", sharedPlayer);
        Farmer newer = farmer("farmer-fourteen", "newer-region", uuid(19), sharedPlayer);
        cache.put(older);
        cache.put(newer);

        cache.remove("farmer-thirteen");

        assertSame(newer, cache.getByPlayerUuid(sharedPlayer).orElseThrow());
        assertEquals("farmer-fourteen", cache.getByPlayerUuid(sharedPlayer).orElseThrow().farmerId());
    }

    private static Farmer farmer(String farmerId, String regionId, UUID ownerUuid, UUID... memberUuids) {
        Farmer farmer = Farmer.create(farmerId, regionId, ownerUuid, LOCATION);
        for (UUID memberUuid : memberUuids) {
            farmer.putMember(new FarmerMember(farmerId, memberUuid, FarmerRole.MEMBER, Instant.EPOCH));
        }
        return farmer;
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }
}
