package com.craftion.farmer.collect;

import static com.craftion.farmer.test.MockItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.LocationSnapshot;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.hook.region.RegionProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FarmerDepositServiceTest {

    private static final MaterialKey WHEAT = MaterialKey.of("WHEAT");

    private MutablePolicy policy;
    private RegionProvider provider;
    private FarmerCache cache;
    private Farmer farmer;
    private World world;
    private Location location;
    private FarmerDepositService service;

    @BeforeEach
    void setUp() {
        this.policy = new MutablePolicy();
        this.provider = mock(RegionProvider.class);
        this.cache = new FarmerCache();
        this.farmer = farmer(true);
        this.cache.put(this.farmer);
        this.world = mock(World.class);
        this.location = new Location(this.world, 1.0D, 64.0D, 1.0D);
        when(this.provider.isAvailable()).thenReturn(true);
        when(this.provider.isSkyblockWorld(any(World.class))).thenReturn(true);
        when(this.provider.regionIdAt(any(Location.class))).thenReturn(Optional.of("region-one"));
        this.service = service(this.provider);
    }

    @Test
    void nullContextReturnsInvalidItem() {
        CollectResult result = this.service.deposit(null).result();

        assertEquals(CollectResult.Status.INVALID_ITEM, result.status());
        assertEquals(0L, result.requestedAmount());
    }

    @Test
    void contextMissingStackOrLocationReturnsInvalidItem() {
        CollectResult missingStack = this.service.deposit(new CollectContext(null, this.location, null, false)).result();
        CollectResult missingLocation = this.service.deposit(new CollectContext(item(Material.WHEAT, 1), null, null, false)).result();

        assertEquals(CollectResult.Status.INVALID_ITEM, missingStack.status());
        assertEquals(CollectResult.Status.INVALID_ITEM, missingLocation.status());
    }

    @Test
    void airItemReturnsEmptyItem() {
        assertEquals(CollectResult.Status.EMPTY_ITEM, deposit(item(Material.AIR, 1)).status());
    }

    @Test
    void nonPositiveAmountReturnsEmptyItem() {
        assertEquals(CollectResult.Status.EMPTY_ITEM, deposit(item(Material.WHEAT, 0)).status());
    }

    @Test
    void disabledCollectionReturnsDisabled() {
        this.policy.enabled = false;

        assertEquals(CollectResult.Status.DISABLED, deposit(item(Material.WHEAT, 1)).status());
    }

    @Test
    void ignoredPlayerDropReturnsPlayerDrop() {
        this.policy.ignorePlayerDrops = true;

        CollectResult result = this.service.deposit(context(item(Material.WHEAT, 1), true)).result();

        assertEquals(CollectResult.Status.PLAYER_DROP, result.status());
    }

    @Test
    void ignoredMetadataReturnsItemHasMeta() {
        this.policy.ignoreMeta = true;

        assertEquals(CollectResult.Status.ITEM_HAS_META, deposit(item(Material.WHEAT, 1, true)).status());
    }

    @Test
    void disallowedMaterialReturnsMaterialNotAllowed() {
        this.policy.allowed = Set.of(Material.CARROT);

        assertEquals(CollectResult.Status.MATERIAL_NOT_ALLOWED, deposit(item(Material.WHEAT, 1)).status());
    }

    @Test
    void unavailableOrMissingProviderReturnsNoRegion() {
        when(this.provider.isAvailable()).thenReturn(false);
        assertEquals(CollectResult.Status.NO_REGION, deposit(item(Material.WHEAT, 1)).status());

        this.service = service(null);
        assertEquals(CollectResult.Status.NO_REGION, deposit(item(Material.WHEAT, 1)).status());
    }

    @Test
    void nullWorldReturnsNotSkyblockWorld() {
        CollectContext context = new CollectContext(item(Material.WHEAT, 1), new Location(null, 0, 64, 0), null, false);

        assertEquals(CollectResult.Status.NOT_SKYBLOCK_WORLD, this.service.deposit(context).result().status());
    }

    @Test
    void nonSkyblockWorldReturnsNotSkyblockWorld() {
        when(this.provider.isSkyblockWorld(this.world)).thenReturn(false);

        assertEquals(CollectResult.Status.NOT_SKYBLOCK_WORLD, deposit(item(Material.WHEAT, 1)).status());
    }

    @Test
    void missingOrBlankRegionReturnsNoRegion() {
        when(this.provider.regionIdAt(any(Location.class))).thenReturn(Optional.empty());
        assertEquals(CollectResult.Status.NO_REGION, deposit(item(Material.WHEAT, 1)).status());

        when(this.provider.regionIdAt(any(Location.class))).thenReturn(Optional.of("  "));
        assertEquals(CollectResult.Status.NO_REGION, deposit(item(Material.WHEAT, 1)).status());
    }

    @Test
    void missingFarmerReturnsNoFarmerWithRegionId() {
        this.cache.clear();

        CollectResult result = deposit(item(Material.WHEAT, 1));

        assertEquals(CollectResult.Status.NO_FARMER, result.status());
        assertEquals("region-one", result.regionId());
        assertNull(result.farmerId());
    }

    @Test
    void disabledFarmerReturnsFarmerDisabled() {
        this.cache.clear();
        this.farmer = farmer(false);
        this.cache.put(this.farmer);

        assertEquals(CollectResult.Status.FARMER_DISABLED, deposit(item(Material.WHEAT, 1)).status());
    }

    @Test
    void disabledProductReturnsProductDisabled() {
        this.farmer.setProductCollectingEnabled(WHEAT, false);

        assertEquals(CollectResult.Status.PRODUCT_DISABLED, deposit(item(Material.WHEAT, 1)).status());
    }

    @Test
    void fullAcceptanceStoresExactAmountAndReturnsCollected() {
        FarmerDepositOutcome outcome = this.service.deposit(context(item(Material.WHEAT, 10), false));
        CollectResult result = outcome.result();

        assertEquals(CollectResult.Status.COLLECTED, result.status());
        assertEquals(10L, result.requestedAmount());
        assertEquals(10L, result.collectedAmount());
        assertEquals(0L, result.remainingAmount());
        assertEquals(10L, result.storageAmount());
        assertEquals(10L, this.farmer.storageAmount(WHEAT));
        assertEquals("farmer-one", result.farmerId());
        assertEquals("region-one", result.regionId());
        assertEquals(WHEAT, result.materialKey());
        assertSame(this.farmer, outcome.farmer());
    }

    @Test
    void partialCapacityAcceptsExactAmountAndRemainder() {
        this.policy.capacity = 6L;

        CollectResult result = deposit(item(Material.WHEAT, 10));

        assertEquals(CollectResult.Status.PARTIAL, result.status());
        assertEquals(6L, result.collectedAmount());
        assertEquals(4L, result.remainingAmount());
        assertEquals(6L, result.storageAmount());
        assertEquals(6L, this.farmer.storageAmount(WHEAT));
    }

    @Test
    void fullStorageAcceptsZeroAndLeavesStorageUnchanged() {
        this.policy.capacity = 5L;
        this.farmer.addStorageAmount(WHEAT, 5L, -1L);

        CollectResult result = deposit(item(Material.WHEAT, 3));

        assertEquals(CollectResult.Status.STORAGE_FULL, result.status());
        assertEquals(0L, result.collectedAmount());
        assertEquals(3L, result.remainingAmount());
        assertEquals(5L, result.storageAmount());
        assertEquals(5L, this.farmer.storageAmount(WHEAT));
    }

    @Test
    void existingStorageIsIncludedInCapacityCalculation() {
        this.policy.capacity = 10L;
        this.farmer.addStorageAmount(WHEAT, 7L, -1L);

        CollectResult result = deposit(item(Material.WHEAT, 8));

        assertEquals(CollectResult.Status.PARTIAL, result.status());
        assertEquals(3L, result.collectedAmount());
        assertEquals(5L, result.remainingAmount());
        assertEquals(10L, result.storageAmount());
    }

    @Test
    void unlimitedCapacityUsesExistingFarmerSemantics() {
        this.policy.capacity = -1L;
        this.farmer.addStorageAmount(WHEAT, 9L, -1L);

        CollectResult result = deposit(item(Material.WHEAT, 4));

        assertEquals(CollectResult.Status.COLLECTED, result.status());
        assertEquals(13L, result.storageAmount());
        assertEquals(-1L, result.capacity());
    }

    @Test
    void depositDoesNotMutateOriginalInputStack() {
        ItemStack input = item(Material.WHEAT, 11, true);
        this.policy.ignoreMeta = false;

        CollectResult result = deposit(input);

        assertEquals(11, input.getAmount());
        assertEquals(Material.WHEAT, input.getType());
        assertTrue(input.hasItemMeta());
        assertEquals(11L, result.requestedAmount());
    }

    private CollectResult deposit(ItemStack itemStack) {
        return this.service.deposit(context(itemStack, false)).result();
    }

    private CollectContext context(ItemStack itemStack, boolean playerDrop) {
        return new CollectContext(itemStack, this.location, CollectReason.ITEM_SPAWN, playerDrop);
    }

    private FarmerDepositService service(RegionProvider value) {
        return new FarmerDepositService(this.policy, () -> value, this.cache);
    }

    private static Farmer farmer(boolean collectingEnabled) {
        return Farmer.create(
            "farmer-one",
            "region-one",
            new UUID(0L, 1L),
            LocationSnapshot.of("world", 0.0D, 64.0D, 0.0D, 0.0F, 0.0F),
            collectingEnabled
        );
    }

    private static final class MutablePolicy implements FarmerDepositService.CollectPolicy {

        private boolean enabled = true;
        private boolean ignorePlayerDrops;
        private boolean ignoreMeta;
        private Set<Material> allowed = Set.of(Material.WHEAT);
        private long capacity = 100L;

        @Override
        public boolean collectEnabled() {
            return this.enabled;
        }

        @Override
        public boolean ignorePlayerDrops() {
            return this.ignorePlayerDrops;
        }

        @Override
        public boolean ignoreItemsWithMeta() {
            return this.ignoreMeta;
        }

        @Override
        public Set<Material> allowedMaterials() {
            return this.allowed;
        }

        @Override
        public long maxStoragePerItem() {
            return this.capacity;
        }
    }
}
