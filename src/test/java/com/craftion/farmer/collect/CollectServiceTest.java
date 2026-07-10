package com.craftion.farmer.collect;

import static com.craftion.farmer.test.MockItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.LocationSnapshot;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.hook.region.RegionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectServiceTest {

    private static final MaterialKey WHEAT = MaterialKey.of("WHEAT");

    private MutablePolicy policy;
    private Farmer farmer;
    private FarmerDepositService depositService;
    private Location location;
    private AtomicInteger scheduleCalls;
    private AtomicInteger recordCalls;
    private AtomicLong recordedAmount;
    private AtomicInteger dispatchCalls;
    private List<String> warnings;

    @BeforeEach
    void setUp() {
        this.policy = new MutablePolicy();
        FarmerCache cache = new FarmerCache();
        this.farmer = Farmer.create(
            "farmer-one",
            "region-one",
            new UUID(0L, 1L),
            LocationSnapshot.of("world", 0.0D, 64.0D, 0.0D, 0.0F, 0.0F)
        );
        cache.put(this.farmer);
        World world = mock(World.class);
        this.location = new Location(world, 0.0D, 64.0D, 0.0D);
        RegionProvider provider = mock(RegionProvider.class);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.isSkyblockWorld(any(World.class))).thenReturn(true);
        when(provider.regionIdAt(any(Location.class))).thenReturn(Optional.of("region-one"));
        this.depositService = new FarmerDepositService(this.policy, () -> provider, cache);
        this.scheduleCalls = new AtomicInteger();
        this.recordCalls = new AtomicInteger();
        this.recordedAmount = new AtomicLong();
        this.dispatchCalls = new AtomicInteger();
        this.warnings = new ArrayList<>();
    }

    @Test
    void acceptedDepositMarksDirtyAndRecordsAcceptedAmountOnly() {
        CollectService service = service(
            () -> this.scheduleCalls.incrementAndGet(),
            (farmer, materialKey, amount) -> {
                this.recordCalls.incrementAndGet();
                this.recordedAmount.set(amount);
            },
            (farmer, materialKey, requested, storage, capacity) -> this.dispatchCalls.incrementAndGet()
        );

        CollectResult result = service.collect(context(10));

        assertEquals(CollectResult.Status.COLLECTED, result.status());
        assertTrue(service.isDirtyFarmer("farmer-one"));
        assertEquals(1, this.scheduleCalls.get());
        assertEquals(1, this.recordCalls.get());
        assertEquals(10L, this.recordedAmount.get());
        assertEquals(0, this.dispatchCalls.get());
        assertEquals(10L, this.farmer.storageAmount(WHEAT));
    }

    @Test
    void zeroAcceptedDoesNotMarkDirtyOrRecordAndDispatchesStorageFull() {
        this.policy.capacity = 5L;
        this.farmer.addStorageAmount(WHEAT, 5L, -1L);
        CollectService service = service(
            () -> this.scheduleCalls.incrementAndGet(),
            (farmer, materialKey, amount) -> this.recordCalls.incrementAndGet(),
            (farmer, materialKey, requested, storage, capacity) -> this.dispatchCalls.incrementAndGet()
        );

        CollectResult result = service.collect(context(4));

        assertEquals(CollectResult.Status.STORAGE_FULL, result.status());
        assertFalse(service.isDirtyFarmer("farmer-one"));
        assertEquals(0, this.scheduleCalls.get());
        assertEquals(0, this.recordCalls.get());
        assertEquals(1, this.dispatchCalls.get());
        assertEquals(5L, this.farmer.storageAmount(WHEAT));
    }

    @Test
    void partialDepositRecordsOnlyAcceptedAmountAndDispatchesStorageFull() {
        this.policy.capacity = 6L;
        CollectService service = service(
            () -> this.scheduleCalls.incrementAndGet(),
            (farmer, materialKey, amount) -> {
                this.recordCalls.incrementAndGet();
                this.recordedAmount.set(amount);
            },
            (farmer, materialKey, requested, storage, capacity) -> this.dispatchCalls.incrementAndGet()
        );

        CollectResult result = service.collect(context(10));

        assertEquals(CollectResult.Status.PARTIAL, result.status());
        assertEquals(6L, result.collectedAmount());
        assertEquals(4L, result.remainingAmount());
        assertEquals(6L, this.recordedAmount.get());
        assertEquals(1, this.recordCalls.get());
        assertEquals(1, this.dispatchCalls.get());
        assertEquals(6L, this.farmer.storageAmount(WHEAT));
    }

    @Test
    void productionCalcFailureDoesNotEscapeOrReplayStorageMutation() {
        CollectService service = service(
            () -> this.scheduleCalls.incrementAndGet(),
            (farmer, materialKey, amount) -> {
                this.recordCalls.incrementAndGet();
                throw new IllegalStateException("record failed");
            },
            (farmer, materialKey, requested, storage, capacity) -> this.dispatchCalls.incrementAndGet()
        );

        CollectResult result = assertDoesNotThrow(() -> service.collect(context(9)));

        assertEquals(CollectResult.Status.COLLECTED, result.status());
        assertEquals(9L, result.collectedAmount());
        assertEquals(0L, result.remainingAmount());
        assertEquals(9L, this.farmer.storageAmount(WHEAT));
        assertEquals(1, this.recordCalls.get());
        assertEquals(1, this.warnings.size());
    }

    @Test
    void eventDispatchFailureDoesNotEscapeOrReplayPartialDeposit() {
        this.policy.capacity = 4L;
        CollectService service = service(
            () -> this.scheduleCalls.incrementAndGet(),
            (farmer, materialKey, amount) -> this.recordedAmount.set(amount),
            (farmer, materialKey, requested, storage, capacity) -> {
                this.dispatchCalls.incrementAndGet();
                throw new LinkageError("event failed");
            }
        );

        CollectResult result = assertDoesNotThrow(() -> service.collect(context(10)));

        assertEquals(CollectResult.Status.PARTIAL, result.status());
        assertEquals(4L, result.collectedAmount());
        assertEquals(6L, result.remainingAmount());
        assertEquals(4L, this.farmer.storageAmount(WHEAT));
        assertEquals(1, this.dispatchCalls.get());
        assertEquals(1, this.warnings.size());
    }

    @Test
    void schedulerFailureRetainsDirtyFarmerAndDoesNotReplayDeposit() {
        CollectService service = service(
            () -> {
                this.scheduleCalls.incrementAndGet();
                throw new IllegalStateException("scheduler failed");
            },
            (farmer, materialKey, amount) -> {
                this.recordCalls.incrementAndGet();
                this.recordedAmount.set(amount);
            },
            (farmer, materialKey, requested, storage, capacity) -> this.dispatchCalls.incrementAndGet()
        );

        CollectResult result = assertDoesNotThrow(() -> service.collect(context(8)));

        assertEquals(CollectResult.Status.COLLECTED, result.status());
        assertEquals(8L, this.farmer.storageAmount(WHEAT));
        assertTrue(service.isDirtyFarmer("farmer-one"));
        assertEquals(1, this.scheduleCalls.get());
        assertEquals(1, this.recordCalls.get());
        assertEquals(8L, this.recordedAmount.get());
        assertEquals(1, this.warnings.size());
    }

    @Test
    void independentFailuresStillReturnOriginalPartialResult() {
        this.policy.capacity = 3L;
        CollectService service = new CollectService(
            this.depositService,
            () -> {
                throw new IllegalStateException("schedule");
            },
            (farmer, materialKey, amount) -> {
                throw new IllegalStateException("record");
            },
            (farmer, materialKey, requested, storage, capacity) -> {
                throw new IllegalStateException("event");
            },
            message -> {
                throw new IllegalStateException("logger");
            }
        );

        CollectResult result = assertDoesNotThrow(() -> service.collect(context(10)));

        assertEquals(CollectResult.Status.PARTIAL, result.status());
        assertEquals(3L, result.collectedAmount());
        assertEquals(7L, result.remainingAmount());
        assertEquals(3L, result.storageAmount());
        assertEquals(3L, this.farmer.storageAmount(WHEAT));
        assertTrue(service.isDirtyFarmer("farmer-one"));
    }

    private CollectService service(
        Runnable scheduler,
        CollectService.CollectRecorder recorder,
        CollectService.StorageFullDispatcher dispatcher
    ) {
        return new CollectService(this.depositService, scheduler, recorder, dispatcher, this.warnings::add);
    }

    private CollectContext context(int amount) {
        return new CollectContext(item(Material.WHEAT, amount), this.location, CollectReason.ITEM_SPAWN, false);
    }

    private static final class MutablePolicy implements FarmerDepositService.CollectPolicy {

        private long capacity = 100L;

        @Override
        public boolean collectEnabled() {
            return true;
        }

        @Override
        public boolean ignorePlayerDrops() {
            return false;
        }

        @Override
        public boolean ignoreItemsWithMeta() {
            return false;
        }

        @Override
        public Set<Material> allowedMaterials() {
            return Set.of(Material.WHEAT);
        }

        @Override
        public long maxStoragePerItem() {
            return this.capacity;
        }
    }
}
