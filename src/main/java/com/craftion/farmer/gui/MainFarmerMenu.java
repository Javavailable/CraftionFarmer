package com.craftion.farmer.gui;

import com.craftion.farmer.farmer.MaterialKey;
import java.util.HashMap;
import java.util.List;
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

        List<Integer> slots = productSection.getIntegerList("slots");
        ConfigurationSection template = productSection.getConfigurationSection("item");
        if (slots.isEmpty() || template == null) {
            return;
        }

        List<MaterialKey> materialKeys = context.configManager().collectMaterialKeys();
        int totalPages = totalPages(materialKeys.size(), slots.size());
        int page = clamp(page(context.menuId()), 1, totalPages);
        int startIndex = (page - 1) * slots.size();
        int endIndex = Math.min(materialKeys.size(), startIndex + slots.size());
        Map<String, String> pagePlaceholders = pagePlaceholders(page, totalPages, materialKeys.size(), startIndex, endIndex);

        int slotIndex = 0;
        for (int index = startIndex; index < endIndex; index++) {
            MaterialKey materialKey = materialKeys.get(index);

            long amount = context.farmer().storageAmount(materialKey);
            OptionalDouble price = context.configManager().price(materialKey);
            long capacity = context.configManager().maxStoragePerItem();
            builder.putConfiguredItem(slots.get(slotIndex), template, context.withPlaceholders(merged(pagePlaceholders, Map.of(
                "material", materialKey.toString(),
                "material_name", context.materialName(materialKey.toString()),
                "amount", formatAmount(amount),
                "worth", price.isPresent() ? formatMoney(price.getAsDouble() * amount) : "-",
                "capacity", capacity < 0L ? "sɪɴɪʀsɪᴢ" : formatAmount(capacity),
                "collection_status", collectionStatus(context, materialKey)
            ))), materialKey.toString());
            slotIndex++;
        }

        if (materialKeys.isEmpty()) {
            ConfigurationSection emptyTemplate = productSection.getConfigurationSection("empty");
            if (emptyTemplate != null) {
                builder.putConfiguredItem(slots.get(0), emptyTemplate, context.withPlaceholders(pagePlaceholders), "BARREL");
            }
        }

        putPageItem(builder, productSection.getConfigurationSection("page-indicator"), context, pagePlaceholders);
        if (page > 1) {
            putPageItem(builder, productSection.getConfigurationSection("previous-page"), context, pagePlaceholders);
        }
        if (page < totalPages) {
            putPageItem(builder, productSection.getConfigurationSection("next-page"), context, pagePlaceholders);
        }
    }

    private void putPageItem(
        MenuLayoutBuilder builder,
        ConfigurationSection section,
        MenuRenderContext context,
        Map<String, String> pagePlaceholders
    ) {
        if (section == null) {
            return;
        }
        builder.putConfiguredItem(section.getInt("slot", -1), section, context.withPlaceholders(pagePlaceholders));
    }

    private String collectionStatus(MenuRenderContext context, MaterialKey materialKey) {
        if (!context.farmer().collectingEnabled()) {
            return "<#FBBF24>ɢᴇɴᴇʟ ᴋᴀᴘᴀʟɪ";
        }
        if (!context.farmer().productCollectingEnabled(materialKey)) {
            return "<#FBBF24>ᴜʀᴜɴ ᴋᴀᴘᴀʟɪ";
        }
        return "<#22C55E>ᴀᴋᴛɪғ";
    }

    private String formatAmount(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private String formatMoney(double amount) {
        return Double.isFinite(amount) ? String.format(Locale.US, "%,.2f", amount) : "-";
    }

    private Map<String, String> pagePlaceholders(int page, int totalPages, int productCount, int startIndex, int endIndex) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page));
        placeholders.put("pages", String.valueOf(totalPages));
        placeholders.put("total_pages", String.valueOf(totalPages));
        placeholders.put("previous_page", String.valueOf(Math.max(1, page - 1)));
        placeholders.put("next_page", String.valueOf(Math.min(totalPages, page + 1)));
        placeholders.put("products", formatAmount(productCount));
        placeholders.put("first_product", productCount == 0 ? "0" : formatAmount(startIndex + 1L));
        placeholders.put("last_product", formatAmount(endIndex));
        return Map.copyOf(placeholders);
    }

    private Map<String, String> merged(Map<String, String> first, Map<String, String> second) {
        Map<String, String> merged = new HashMap<>();
        if (first != null) {
            merged.putAll(first);
        }
        if (second != null) {
            merged.putAll(second);
        }
        return Map.copyOf(merged);
    }

    private int page(String menuId) {
        if (menuId == null || !menuId.startsWith("main:")) {
            return 1;
        }
        try {
            return Integer.parseInt(menuId.substring("main:".length()));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private int totalPages(int productCount, int pageSize) {
        if (productCount <= 0 || pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (productCount + pageSize - 1) / pageSize);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
