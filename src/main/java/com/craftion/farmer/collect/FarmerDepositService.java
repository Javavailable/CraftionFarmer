package com.craftion.farmer.collect;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.farmer.StorageAddResult;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

public final class FarmerDepositService {

    private final CollectPolicy policy;
    private final Supplier<RegionProvider> regionProviderSupplier;
    private final FarmerCache farmerCache;

    public FarmerDepositService(
        ConfigManager configManager,
        RegionProviderManager regionProviderManager,
        FarmerCache farmerCache
    ) {
        this(
            new ConfigCollectPolicy(Objects.requireNonNull(configManager, "configManager")),
            Objects.requireNonNull(regionProviderManager, "regionProviderManager")::provider,
            farmerCache
        );
    }

    FarmerDepositService(
        CollectPolicy policy,
        Supplier<RegionProvider> regionProviderSupplier,
        FarmerCache farmerCache
    ) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.regionProviderSupplier = Objects.requireNonNull(regionProviderSupplier, "regionProviderSupplier");
        this.farmerCache = Objects.requireNonNull(farmerCache, "farmerCache");
    }

    public FarmerDepositOutcome deposit(CollectContext context) {
        if (context == null) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.INVALID_ITEM, null));
        }

        ItemStack itemStack = context.itemStack();
        Location location = context.location();
        CollectReason reason = context.reason();
        boolean playerDrop = context.playerDrop();
        if (itemStack == null || location == null) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.INVALID_ITEM, context));
        }

        int requestedAmount = itemStack.getAmount();
        Material material = itemStack.getType();
        if (material == null) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.INVALID_ITEM, context));
        }
        if (requestedAmount <= 0 || isAir(material)) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.EMPTY_ITEM, context));
        }
        if (!this.policy.collectEnabled()) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.DISABLED, context));
        }
        if (this.policy.ignorePlayerDrops() && playerDrop) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.PLAYER_DROP, context));
        }
        if (this.policy.ignoreItemsWithMeta() && itemStack.hasItemMeta()) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.ITEM_HAS_META, context));
        }
        if (!this.policy.allowedMaterials().contains(material)) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.MATERIAL_NOT_ALLOWED, context));
        }

        RegionProvider provider = this.regionProviderSupplier.get();
        if (provider == null || !provider.isAvailable()) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.NO_REGION, context));
        }

        World world = location.getWorld();
        if (world == null || !provider.isSkyblockWorld(world)) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.NOT_SKYBLOCK_WORLD, context));
        }

        Optional<String> resolvedRegionId = provider.regionIdAt(location)
            .map(String::trim)
            .filter(regionId -> !regionId.isEmpty());
        if (resolvedRegionId.isEmpty()) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.NO_REGION, context));
        }
        String regionId = resolvedRegionId.get();

        Optional<Farmer> resolvedFarmer = this.farmerCache.getByRegionId(regionId);
        if (resolvedFarmer.isEmpty()) {
            return FarmerDepositOutcome.skipped(CollectResult.skipped(CollectResult.Status.NO_FARMER, context, regionId));
        }
        Farmer farmer = resolvedFarmer.get();
        if (!farmer.collectingEnabled()) {
            return new FarmerDepositOutcome(CollectResult.skipped(CollectResult.Status.FARMER_DISABLED, context, regionId), farmer);
        }

        MaterialKey materialKey = MaterialKey.of(material.name());
        if (!farmer.productCollectingEnabled(materialKey)) {
            return new FarmerDepositOutcome(CollectResult.skipped(CollectResult.Status.PRODUCT_DISABLED, context, regionId), farmer);
        }

        long capacity = this.policy.maxStoragePerItem();
        StorageAddResult addResult = farmer.addStorageAmount(materialKey, requestedAmount, capacity);

        CollectResult.Status status;
        if (!addResult.changedStorage()) {
            status = CollectResult.Status.STORAGE_FULL;
        } else if (addResult.remainingAmount() > 0L) {
            status = CollectResult.Status.PARTIAL;
        } else {
            status = CollectResult.Status.COLLECTED;
        }

        CollectResult result = new CollectResult(
            status,
            reason,
            farmer.farmerId(),
            farmer.regionId(),
            materialKey,
            requestedAmount,
            addResult.collectedAmount(),
            addResult.remainingAmount(),
            addResult.storageAmount(),
            capacity
        );
        return new FarmerDepositOutcome(result, farmer);
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    interface CollectPolicy {

        boolean collectEnabled();

        boolean ignorePlayerDrops();

        boolean ignoreItemsWithMeta();

        Set<Material> allowedMaterials();

        long maxStoragePerItem();
    }

    private record ConfigCollectPolicy(ConfigManager configManager) implements CollectPolicy {

        @Override
        public boolean collectEnabled() {
            return this.configManager.isCollectEnabled();
        }

        @Override
        public boolean ignorePlayerDrops() {
            return this.configManager.ignorePlayerDrops();
        }

        @Override
        public boolean ignoreItemsWithMeta() {
            return this.configManager.ignoreItemsWithMeta();
        }

        @Override
        public Set<Material> allowedMaterials() {
            return this.configManager.allowedCollectMaterials();
        }

        @Override
        public long maxStoragePerItem() {
            return this.configManager.maxStoragePerItem();
        }
    }
}
