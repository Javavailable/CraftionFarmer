package com.craftion.farmer.gui;

import com.craftion.farmer.farmer.MaterialKey;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class StorageMenu implements FarmerMenu {

    @Override
    public String id() {
        return "storage";
    }

    @Override
    public FarmerMenuAccess requiredAccess() {
        return FarmerMenuAccess.MEMBER;
    }

    @Override
    public void render(MenuRenderContext context, MenuLayoutBuilder builder) {
        ConfigurationSection storageSection = context.menuSection().getConfigurationSection("storage-items");
        if (storageSection == null) {
            return;
        }

        java.util.List<Integer> slots = storageSection.getIntegerList("slots");
        ConfigurationSection filledTemplate = storageSection.getConfigurationSection("filled");
        if (slots.isEmpty() || filledTemplate == null) {
            return;
        }

        int index = 0;
        for (Map.Entry<MaterialKey, Long> entry : context.farmer().storage().snapshot().entrySet().stream()
            .filter(value -> value.getValue() > 0L)
            .sorted(Comparator.comparing(value -> value.getKey().toString()))
            .toList()) {
            if (index >= slots.size()) {
                break;
            }

            String materialKey = entry.getKey().toString();
            builder.putConfiguredItem(slots.get(index), filledTemplate, context.withPlaceholders(Map.of(
                "material", materialKey,
                "material_name", context.materialName(materialKey),
                "amount", formatAmount(entry.getValue())
            )), "BARREL");
            index++;
        }

        if (index == 0) {
            ConfigurationSection emptyTemplate = storageSection.getConfigurationSection("empty");
            if (emptyTemplate != null) {
                builder.putConfiguredItem(slots.get(0), emptyTemplate, context.placeholders(), "BARREL");
            }
        }
    }

    private String formatAmount(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }
}
