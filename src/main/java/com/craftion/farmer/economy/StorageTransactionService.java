package com.craftion.farmer.economy;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.farmer.StorageRemoveResult;
import com.craftion.farmer.gui.FarmerMenuAccess;
import com.craftion.farmer.gui.FarmerMenuSession;
import com.craftion.farmer.storage.repository.LogRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class StorageTransactionService {

    private static final String WITHDRAW_ACTION = "WITHDRAW";
    private static final String SELL_ACTION = "SELL";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DebugLogger debugLogger;
    private final FarmerPersistenceService farmerPersistenceService;
    private final LogRepository logRepository;
    private final EconomyProviderManager economyProviderManager;
    private final PriceProvider priceProvider;

    public StorageTransactionService(
        JavaPlugin plugin,
        ConfigManager configManager,
        DebugLogger debugLogger,
        FarmerPersistenceService farmerPersistenceService,
        LogRepository logRepository,
        EconomyProviderManager economyProviderManager,
        PriceProvider priceProvider
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debugLogger = debugLogger;
        this.farmerPersistenceService = farmerPersistenceService;
        this.logRepository = logRepository;
        this.economyProviderManager = economyProviderManager;
        this.priceProvider = priceProvider;
    }

    public StorageTransactionResult withdraw(Player player, FarmerMenuSession session, String target) {
        WithdrawTarget withdrawTarget = parseWithdrawTarget(target);
        if (player == null || session == null || withdrawTarget == null) {
            return result(StorageTransactionResult.Status.INVALID_ACTION, null);
        }
        if (!canWithdraw(session)) {
            return result(StorageTransactionResult.Status.DENIED, withdrawTarget.materialKey());
        }

        Material material = material(withdrawTarget.materialKey());
        if (material == null) {
            return result(StorageTransactionResult.Status.INVALID_ACTION, withdrawTarget.materialKey());
        }

        Farmer farmer = session.farmer();
        long storageAmount = farmer.storageAmount(withdrawTarget.materialKey());
        if (storageAmount <= 0L) {
            return result(StorageTransactionResult.Status.EMPTY_STORAGE, withdrawTarget.materialKey());
        }

        long maxAmount = withdrawTarget.all() ? storageAmount : Math.min(storageAmount, maxStack(material));
        long capacity = inventoryCapacity(player, material);
        if (capacity <= 0L) {
            return result(StorageTransactionResult.Status.INVENTORY_FULL, withdrawTarget.materialKey());
        }

        long requestedAmount = Math.min(maxAmount, capacity);
        if (requestedAmount <= 0L) {
            return result(StorageTransactionResult.Status.INVENTORY_FULL, withdrawTarget.materialKey());
        }

        StorageRemoveResult removeResult = farmer.removeStorageAmount(withdrawTarget.materialKey(), requestedAmount);
        if (!removeResult.changedStorage()) {
            return result(StorageTransactionResult.Status.EMPTY_STORAGE, withdrawTarget.materialKey());
        }

        long givenAmount = giveItems(player, material, removeResult.removedAmount());
        long leftoverAmount = removeResult.removedAmount() - givenAmount;
        if (leftoverAmount > 0L) {
            restore(farmer, withdrawTarget.materialKey(), leftoverAmount);
        }
        if (givenAmount <= 0L) {
            return result(StorageTransactionResult.Status.INVENTORY_FULL, withdrawTarget.materialKey());
        }

        persistAndLog(farmer, player.getUniqueId(), WITHDRAW_ACTION, withdrawDetail(withdrawTarget.materialKey(), givenAmount));
        return new StorageTransactionResult(
            StorageTransactionResult.Status.SUCCESS,
            withdrawTarget.materialKey(),
            givenAmount,
            1L,
            0.0D,
            0.0D,
            0.0D,
            "",
            ""
        );
    }

    public StorageTransactionResult sell(Player player, FarmerMenuSession session, String target) {
        SellTarget sellTarget = parseSellTarget(target);
        if (player == null || session == null || sellTarget == null) {
            return result(StorageTransactionResult.Status.INVALID_ACTION, null);
        }
        if (!canSell(session)) {
            return result(StorageTransactionResult.Status.DENIED, sellTarget.materialKey());
        }

        SalePlanResult planResult = salePlans(session.farmer(), sellTarget);
        if (planResult.status() != StorageTransactionResult.Status.SUCCESS) {
            return result(planResult.status(), sellTarget.materialKey());
        }

        Farmer farmer = session.farmer();
        List<SaleLine> removedLines = removeSaleLines(farmer, planResult.plans());
        if (removedLines.isEmpty()) {
            return result(StorageTransactionResult.Status.EMPTY_STORAGE, sellTarget.materialKey());
        }

        double gross = gross(removedLines);
        if (!Double.isFinite(gross) || gross <= 0.0D) {
            restore(farmer, removedLines);
            return result(StorageTransactionResult.Status.FAILED, sellTarget.materialKey());
        }
        double tax = tax(gross);
        double net = gross - tax;
        if (!Double.isFinite(net) || net <= 0.0D) {
            restore(farmer, removedLines);
            return result(StorageTransactionResult.Status.DEPOSIT_FAILED, sellTarget.materialKey());
        }

        EconomyProvider economyProvider = this.economyProviderManager.provider();
        String providerName = economyProvider == null ? "" : economyProvider.name();
        if (economyProvider == null || !economyProvider.isAvailable()) {
            restore(farmer, removedLines);
            return new StorageTransactionResult(
                StorageTransactionResult.Status.ECONOMY_UNAVAILABLE,
                sellTarget.materialKey(),
                totalAmount(removedLines),
                removedLines.size(),
                gross,
                tax,
                net,
                providerName,
                ""
            );
        }

        EconomyDepositResult depositResult = economyProvider.deposit(player, net);
        if (!depositResult.success()) {
            restore(farmer, removedLines);
            return new StorageTransactionResult(
                StorageTransactionResult.Status.DEPOSIT_FAILED,
                sellTarget.materialKey(),
                totalAmount(removedLines),
                removedLines.size(),
                gross,
                tax,
                net,
                depositResult.providerName(),
                depositResult.errorMessage()
            );
        }

        persistAndLog(farmer, player.getUniqueId(), SELL_ACTION, sellDetail(removedLines, gross, tax, net, depositResult.providerName()));
        return new StorageTransactionResult(
            StorageTransactionResult.Status.SUCCESS,
            sellTarget.materialKey(),
            totalAmount(removedLines),
            removedLines.size(),
            gross,
            tax,
            net,
            depositResult.providerName(),
            ""
        );
    }

    private boolean canWithdraw(FarmerMenuSession session) {
        FarmerMenuAccess requiredAccess = this.configManager.allowMemberWithdraw() ? FarmerMenuAccess.MEMBER : FarmerMenuAccess.MANAGER;
        return requiredAccess.allows(session.role());
    }

    private boolean canSell(FarmerMenuSession session) {
        return FarmerMenuAccess.MANAGER.allows(session.role());
    }

    private SalePlanResult salePlans(Farmer farmer, SellTarget target) {
        if (target.allMaterials()) {
            Map<MaterialKey, Long> snapshot = farmer.storage().snapshot();
            boolean hasStorage = snapshot.values().stream().anyMatch(amount -> amount > 0L);
            if (!hasStorage) {
                return new SalePlanResult(StorageTransactionResult.Status.EMPTY_STORAGE, List.of());
            }

            List<SalePlan> plans = snapshot.entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .flatMap(entry -> price(entry.getKey()).stream().mapToObj(price -> new SalePlan(entry.getKey(), entry.getValue(), price)))
                .toList();
            if (plans.isEmpty()) {
                return new SalePlanResult(StorageTransactionResult.Status.NO_PRICE, List.of());
            }
            return new SalePlanResult(StorageTransactionResult.Status.SUCCESS, plans);
        }

        long amount = farmer.storageAmount(target.materialKey());
        if (amount <= 0L) {
            return new SalePlanResult(StorageTransactionResult.Status.EMPTY_STORAGE, List.of());
        }
        OptionalDouble price = price(target.materialKey());
        if (price.isEmpty()) {
            return new SalePlanResult(StorageTransactionResult.Status.NO_PRICE, List.of());
        }
        return new SalePlanResult(StorageTransactionResult.Status.SUCCESS, List.of(new SalePlan(target.materialKey(), amount, price.getAsDouble())));
    }

    private List<SaleLine> removeSaleLines(Farmer farmer, List<SalePlan> plans) {
        List<SaleLine> removedLines = new ArrayList<>();
        for (SalePlan plan : plans) {
            StorageRemoveResult removeResult = farmer.removeStorageAmount(plan.materialKey(), plan.amount());
            if (removeResult.removedAmount() > 0L) {
                removedLines.add(new SaleLine(plan.materialKey(), removeResult.removedAmount(), plan.price()));
            }
        }
        return removedLines;
    }

    private OptionalDouble price(MaterialKey materialKey) {
        return this.priceProvider.price(materialKey);
    }

    private Material material(MaterialKey materialKey) {
        Material material = Material.matchMaterial(materialKey.toString().toUpperCase(Locale.ROOT));
        return material == null || material.isAir() ? null : material;
    }

    private long inventoryCapacity(Player player, Material material) {
        PlayerInventory inventory = player.getInventory();
        ItemStack probe = new ItemStack(material, 1);
        int maxStack = maxStack(material);
        long capacity = 0L;
        for (ItemStack itemStack : inventory.getStorageContents()) {
            if (itemStack == null || itemStack.getType().isAir()) {
                capacity += maxStack;
                continue;
            }
            if (itemStack.isSimilar(probe) && itemStack.getAmount() < maxStack) {
                capacity += maxStack - itemStack.getAmount();
            }
        }
        return capacity;
    }

    private long giveItems(Player player, Material material, long amount) {
        long remainingAmount = amount;
        int maxStack = maxStack(material);
        while (remainingAmount > 0L) {
            int stackAmount = (int) Math.min(maxStack, remainingAmount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, stackAmount));
            if (!leftovers.isEmpty()) {
                long leftoverAmount = leftovers.values().stream().mapToLong(ItemStack::getAmount).sum();
                remainingAmount = remainingAmount - stackAmount + leftoverAmount;
                break;
            }
            remainingAmount -= stackAmount;
        }
        return amount - remainingAmount;
    }

    private int maxStack(Material material) {
        return Math.max(1, new ItemStack(material, 1).getMaxStackSize());
    }

    private double gross(List<SaleLine> lines) {
        double total = 0.0D;
        for (SaleLine line : lines) {
            double value = line.amount() * line.price();
            if (!Double.isFinite(value)) {
                return Double.NaN;
            }
            total += value;
            if (!Double.isFinite(total)) {
                return Double.NaN;
            }
        }
        return total;
    }

    private double tax(double gross) {
        if (!this.configManager.economyTaxEnabled()) {
            return 0.0D;
        }
        double tax = gross * this.configManager.economyTaxRate();
        if (!Double.isFinite(tax)) {
            return gross;
        }
        return Math.max(0.0D, Math.min(gross, tax));
    }

    private long totalAmount(List<SaleLine> lines) {
        long total = 0L;
        for (SaleLine line : lines) {
            if (Long.MAX_VALUE - total < line.amount()) {
                return Long.MAX_VALUE;
            }
            total += line.amount();
        }
        return total;
    }

    private void restore(Farmer farmer, List<SaleLine> lines) {
        for (SaleLine line : lines) {
            restore(farmer, line.materialKey(), line.amount());
        }
    }

    private void restore(Farmer farmer, MaterialKey materialKey, long amount) {
        if (amount > 0L) {
            farmer.addStorageAmount(materialKey, amount, -1L);
        }
    }

    private void persistAndLog(Farmer farmer, UUID actorUuid, String action, String detail) {
        this.farmerPersistenceService.save(farmer).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Farmer transaction storage kaydedilemedi: " + readableMessage(throwable));
            }
        });

        try {
            this.logRepository.append(farmer.farmerId(), actorUuid, action, detail).whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    this.plugin.getLogger().warning("Farmer transaction log yazilamadi: " + readableMessage(throwable));
                }
            });
        } catch (RuntimeException exception) {
            this.debugLogger.debug("Farmer transaction log skipped: " + readableMessage(exception));
        }
    }

    private String withdrawDetail(MaterialKey materialKey, long amount) {
        return "material=" + materialKey + " amount=" + amount;
    }

    private String sellDetail(List<SaleLine> lines, double gross, double tax, double net, String providerName) {
        String items = lines.stream()
            .map(line -> line.materialKey() + ":" + line.amount() + "@" + formatMoney(line.price()))
            .collect(Collectors.joining(","));
        return "items=" + items
            + " gross=" + formatMoney(gross)
            + " tax=" + formatMoney(tax)
            + " net=" + formatMoney(net)
            + " provider=" + providerName;
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private WithdrawTarget parseWithdrawTarget(String target) {
        String[] parts = parts(target);
        if (parts.length == 1) {
            MaterialKey materialKey = materialKey(parts[0]);
            return materialKey == null ? null : new WithdrawTarget(materialKey, false);
        }
        if (parts.length == 2) {
            MaterialKey materialKey = materialKey(parts[0]);
            if (materialKey == null) {
                return null;
            }
            return switch (parts[1]) {
                case "stack" -> new WithdrawTarget(materialKey, false);
                case "all" -> new WithdrawTarget(materialKey, true);
                default -> null;
            };
        }
        return null;
    }

    private SellTarget parseSellTarget(String target) {
        String[] parts = parts(target);
        if (parts.length == 1 && parts[0].equals("all")) {
            return new SellTarget(null, true);
        }
        if (parts.length == 1) {
            MaterialKey materialKey = materialKey(parts[0]);
            return materialKey == null ? null : new SellTarget(materialKey, false);
        }
        if (parts.length == 2 && parts[1].equals("all")) {
            MaterialKey materialKey = materialKey(parts[0]);
            return materialKey == null ? null : new SellTarget(materialKey, false);
        }
        return null;
    }

    private String[] parts(String target) {
        return target == null ? new String[0] : target.trim().toLowerCase(Locale.ROOT).split(":", -1);
    }

    private MaterialKey materialKey(String value) {
        try {
            return MaterialKey.of(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private StorageTransactionResult result(StorageTransactionResult.Status status, MaterialKey materialKey) {
        return new StorageTransactionResult(status, materialKey, 0L, 0L, 0.0D, 0.0D, 0.0D, "", "");
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    private record WithdrawTarget(MaterialKey materialKey, boolean all) {
    }

    private record SellTarget(MaterialKey materialKey, boolean allMaterials) {
    }

    private record SalePlanResult(StorageTransactionResult.Status status, List<SalePlan> plans) {
    }

    private record SalePlan(MaterialKey materialKey, long amount, double price) {
    }

    private record SaleLine(MaterialKey materialKey, long amount, double price) {
    }
}
