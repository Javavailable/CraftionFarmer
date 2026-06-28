package com.craftion.farmer.gui;

import java.util.Comparator;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class ModulesMenu implements FarmerMenu {

    @Override
    public String id() {
        return "modules";
    }

    @Override
    public FarmerMenuAccess requiredAccess() {
        return FarmerMenuAccess.MEMBER;
    }

    @Override
    public void render(MenuRenderContext context, MenuLayoutBuilder builder) {
        ConfigurationSection moduleSection = context.menuSection().getConfigurationSection("module-items");
        if (moduleSection == null) {
            return;
        }

        java.util.List<Integer> slots = moduleSection.getIntegerList("slots");
        if (slots.isEmpty()) {
            return;
        }

        int index = 0;
        for (Map.Entry<String, Boolean> entry : context.farmer().moduleStates().entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .toList()) {
            if (index >= slots.size()) {
                break;
            }

            ConfigurationSection template = moduleSection.getConfigurationSection(entry.getValue() ? "enabled" : "disabled");
            if (template != null) {
                builder.putConfiguredItem(slots.get(index), template, context.withPlaceholders(Map.of(
                    "module", context.moduleName(entry.getKey()),
                    "module_state", context.configManager().guiModuleState(entry.getValue())
                )), "AMETHYST_SHARD");
                index++;
            }
        }

        if (index == 0) {
            ConfigurationSection emptyTemplate = moduleSection.getConfigurationSection("empty");
            if (emptyTemplate != null) {
                builder.putConfiguredItem(slots.get(0), emptyTemplate, context.placeholders(), "AMETHYST_SHARD");
            }
        }
    }
}
