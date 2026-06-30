package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoHarvestModule implements FarmerModule, Listener {

    public static final String KEY = "auto-harvest";
    private static final BlockFace[] PISTON_FACES = {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DebugLogger debugLogger;
    private final FarmerCache farmerCache;
    private final ModuleStateService moduleStateService;
    private final RegionProviderManager regionProviderManager;
    private boolean registered;

    public AutoHarvestModule(
        JavaPlugin plugin,
        ConfigManager configManager,
        DebugLogger debugLogger,
        FarmerCache farmerCache,
        ModuleStateService moduleStateService,
        RegionProviderManager regionProviderManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debugLogger = debugLogger;
        this.farmerCache = farmerCache;
        this.moduleStateService = moduleStateService;
        this.regionProviderManager = regionProviderManager;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String iconMaterial() {
        return "DIAMOND_HOE";
    }

    @Override
    public void initialize() {
        registerIfEnabled();
    }

    @Override
    public void reload() {
        shutdown();
        registerIfEnabled();
    }

    @Override
    public void shutdown() {
        if (!this.registered) {
            return;
        }

        HandlerList.unregisterAll(this);
        this.registered = false;
        this.debugLogger.debug("AutoHarvest listener unregistered.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!this.configManager.moduleEnabled(KEY)) {
            debugSkip("module disabled", event.getBlock().getLocation(), null);
            return;
        }

        BlockState newState = event.getNewState();
        Material harvestMaterial = normalizedHarvestMaterial(newState.getType());
        if (harvestMaterial == null) {
            return;
        }

        Location location = event.getBlock().getLocation();
        Set<Material> allowedCrops = this.configManager.autoHarvestCrops();
        if (!allowedCrops.contains(harvestMaterial)) {
            debugSkip("material not allowed", location, harvestMaterial.name());
            return;
        }

        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            debugSkip("no region", location, harvestMaterial.name());
            return;
        }

        World world = location.getWorld();
        if (world == null || !provider.isSkyblockWorld(world)) {
            debugSkip("not skyblock world", location, harvestMaterial.name());
            return;
        }

        Optional<String> regionId = provider.regionIdAt(location);
        if (regionId.isEmpty()) {
            debugSkip("no region", location, harvestMaterial.name());
            return;
        }

        Optional<Farmer> farmer = this.farmerCache.getByRegionId(regionId.get());
        if (farmer.isEmpty()) {
            debugSkip("no farmer", location, harvestMaterial.name());
            return;
        }

        Farmer value = farmer.get();
        if (!value.collectingEnabled()) {
            debugSkip("farmer collection disabled", location, harvestMaterial.name());
            return;
        }

        if (!this.moduleStateService.state(value, this)) {
            debugSkip("module state disabled", location, harvestMaterial.name());
            return;
        }

        MaterialKey materialKey = MaterialKey.of(harvestMaterial.name());
        if (!value.productCollectingEnabled(materialKey)) {
            debugSkip("product collection disabled", location, harvestMaterial.name());
            return;
        }

        if (this.configManager.autoHarvestRequirePiston() && !hasPiston(event.getBlock())) {
            debugSkip("piston required", location, harvestMaterial.name());
            return;
        }

        if (this.configManager.autoHarvestCheckStock() && isStorageFullForHarvest(value, harvestMaterial)) {
            debugSkip("storage full", location, harvestMaterial.name());
            if (this.configManager.autoHarvestPreventGrowthWhenFull()) {
                event.setCancelled(true);
            }
            return;
        }

        if (!harvest(event, harvestMaterial)) {
            debugSkip("not mature crop", location, harvestMaterial.name());
        }
    }

    private void registerIfEnabled() {
        if (this.registered) {
            return;
        }
        if (!this.configManager.moduleEnabled(KEY)) {
            debugSkip("module disabled", null, null);
            return;
        }

        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        this.registered = true;
        this.debugLogger.debug("AutoHarvest listener registered.");
    }

    private boolean harvest(BlockGrowEvent event, Material harvestMaterial) {
        if (isBlockCrop(harvestMaterial)) {
            event.setCancelled(true);
            BlockState matureState = event.getNewState();
            List<ItemStack> drops = harvestDrops(harvestMaterial);
            event.getBlock().setType(Material.AIR, true);
            dropHarvest(matureState.getLocation(), drops);
            return true;
        }

        BlockData blockData = event.getNewState().getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return false;
        }
        if (ageable.getAge() < ageable.getMaximumAge()) {
            return false;
        }

        event.setCancelled(true);
        BlockState matureState = event.getNewState();
        List<ItemStack> drops = harvestDrops(harvestMaterial);
        ageable.setAge(0);
        event.getBlock().setBlockData(ageable, true);
        dropHarvest(matureState.getLocation(), drops);
        return true;
    }

    private void dropHarvest(Location location, List<ItemStack> drops) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        for (ItemStack drop : drops) {
            if (drop != null && drop.getAmount() > 0 && !drop.getType().isAir()) {
                world.dropItemNaturally(location, drop);
            }
        }
    }

    private List<ItemStack> harvestDrops(Material harvestMaterial) {
        List<ItemStack> drops = new ArrayList<>();
        int amount = 1;

        switch (harvestMaterial) {
            case WHEAT -> {
                drops.add(new ItemStack(Material.WHEAT, 1));
                drops.add(new ItemStack(Material.WHEAT_SEEDS, ThreadLocalRandom.current().nextInt(1, 4)));
                return drops;
            }
            case BEETROOT -> {
                drops.add(new ItemStack(Material.BEETROOT, 1));
                drops.add(new ItemStack(Material.BEETROOT_SEEDS, ThreadLocalRandom.current().nextInt(1, 4)));
                return drops;
            }
            case CARROT -> amount = ThreadLocalRandom.current().nextInt(2, 6);
            case POTATO -> {
                amount = ThreadLocalRandom.current().nextInt(2, 6);
                if (ThreadLocalRandom.current().nextInt(100) < 2) {
                    drops.add(new ItemStack(Material.POISONOUS_POTATO, 1));
                }
            }
            case NETHER_WART -> amount = ThreadLocalRandom.current().nextInt(2, 5);
            case MELON_SLICE -> amount = ThreadLocalRandom.current().nextInt(3, 8);
            case COCOA_BEANS, SWEET_BERRIES -> amount = 3;
            default -> amount = 1;
        }

        drops.add(new ItemStack(harvestMaterial, amount));
        return drops;
    }

    private boolean isBlockCrop(Material harvestMaterial) {
        return harvestMaterial == Material.SUGAR_CANE
            || harvestMaterial == Material.CACTUS
            || harvestMaterial == Material.BAMBOO
            || harvestMaterial == Material.PUMPKIN
            || harvestMaterial == Material.MELON_SLICE;
    }

    private List<Material> stockKeysForHarvest(Material harvestMaterial) {
        if (harvestMaterial == Material.WHEAT) {
            return List.of(Material.WHEAT, Material.WHEAT_SEEDS);
        }
        if (harvestMaterial == Material.BEETROOT) {
            return List.of(Material.BEETROOT, Material.BEETROOT_SEEDS);
        }
        return List.of(harvestMaterial);
    }

    private boolean isStorageFullForHarvest(Farmer farmer, Material harvestMaterial) {
        Set<Material> allowedMaterials = this.configManager.allowedCollectMaterials();
        for (Material material : stockKeysForHarvest(harvestMaterial)) {
            if (!allowedMaterials.contains(material)) {
                continue;
            }
            MaterialKey materialKey = MaterialKey.of(material.name());
            if (farmer.productCollectingEnabled(materialKey) && isStorageFull(farmer, materialKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStorageFull(Farmer farmer, MaterialKey materialKey) {
        long capacity = this.configManager.maxStoragePerItem();
        return capacity >= 0L && farmer.storageAmount(materialKey) >= capacity;
    }

    private boolean hasPiston(Block block) {
        if (!this.configManager.autoHarvestCheckAllDirections()) {
            return isPiston(block.getRelative(BlockFace.UP).getType());
        }

        for (BlockFace face : PISTON_FACES) {
            if (isPiston(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPiston(Material material) {
        return material == Material.PISTON || material == Material.STICKY_PISTON;
    }

    private Material normalizedHarvestMaterial(Material material) {
        if (material == null || material.isAir()) {
            return null;
        }
        return switch (material) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case MELON -> Material.MELON_SLICE;
            case PUMPKIN -> Material.PUMPKIN;
            case CACTUS -> Material.CACTUS;
            case SUGAR_CANE -> Material.SUGAR_CANE;
            case BAMBOO -> Material.BAMBOO;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    private void debugSkip(String reason, Location location, String detail) {
        StringBuilder message = new StringBuilder("AutoHarvest skipped: ").append(reason);
        if (detail != null && !detail.isBlank()) {
            message.append(" material=").append(detail);
        }
        if (location != null) {
            World world = location.getWorld();
            message.append(" location=")
                .append(world == null ? "unknown" : world.getName())
                .append(':')
                .append(location.getBlockX())
                .append(',')
                .append(location.getBlockY())
                .append(',')
                .append(location.getBlockZ());
        }
        this.debugLogger.debug(message.toString());
    }
}
