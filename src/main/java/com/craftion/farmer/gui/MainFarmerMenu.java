package com.craftion.farmer.gui;

import com.craftion.farmer.farmer.MaterialKey;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import org.bukkit.configuration.ConfigurationSection;

public final class MainFarmerMenu implements FarmerMenu {

    @Override
    public String id() {
        return "main";
    }

    @Override
    public FarmerMenuAccess requiredAccess() {
        return FarmerMenuAccess.VIEWER;
    }

    @Override
    public void render(MenuRenderContext context, MenuLayoutBuilder builder) {
        ConfigurationSection productSection = context.menuSection().getConfigurationSection("product-items");
        if (productSection == null) {
            return;
        }

        java.util.List<Integer> slots = productSection.getIntegerList("slots");
        ConfigurationSection template = productSection.getConfigurationSection("item");
        if (slots.isEmpty() || template == null) {
            return;
        }

        int index = 0;
        for (MaterialKey materialKey : context.configManager().collectMaterialKeys()) {
            if (index >= slots.size()) {
                break;
            }

            long amount = context.farmer().storageAmount(materialKey);
            OptionalDouble price = context.configManager().price(materialKey);
            long capacity = context.configManager().maxStoragePerItem();
            builder.putConfiguredItem(slots.get(index), template, context.withPlaceholders(Map.of(
                "material", materialKey.toString(),
                "material_name", context.materialName(materialKey.toString()),
                "amount", formatAmount(amount),
                "price", price.isPresent() ? formatMoney(price.getAsDouble()) : "-",
                "worth", price.isPresent() ? formatMoney(price.getAsDouble() * amount) : "-",
                "capacity", capacity < 0L ? "sɪɴɪʀsɪᴢ" : formatAmount(capacity),
                "fill_percent", fillPercent(amount, capacity),
                "product_state", context.configManager().guiCollectingState(context.farmer().productCollectingEnabled(materialKey)),
                "effective_product_state", context.configManager().guiCollectingState(context.farmer().collectingEnabled() && context.farmer().productCollectingEnabled(materialKey))
            )), materialKey.toString());
            index++;
        }

        if (index == 0) {
            ConfigurationSection emptyTemplate = productSection.getConfigurationSection("empty");
            if (emptyTemplate != null) {
                builder.putConfiguredItem(slots.get(0), emptyTemplate, context.placeholders(), "BARREL");
            }
        }
    }

    private String formatAmount(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private String formatMoney(double amount) {
        return Double.isFinite(amount) ? String.format(Locale.US, "%,.2f", amount) : "-";
    }

    private String fillPercent(long amount, long capacity) {
        if (capacity < 0L) {
            return "-";
        }
        if (capacity <= 0L) {
            return "0%";
        }
        double percent = Math.max(0.0D, Math.min(100.0D, (amount * 100.0D) / capacity));
        return String.format(Locale.US, "%.1f%%", percent);
    }
}
