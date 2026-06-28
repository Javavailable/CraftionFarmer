package com.craftion.farmer.gui;

import com.craftion.farmer.module.FarmerModule;
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

        context.moduleManager().ensureDefaultStates(context.farmer());

        int index = 0;
        for (FarmerModule module : context.moduleManager().modules()) {
            if (index >= slots.size()) {
                break;
            }

            boolean configEnabled = context.moduleManager().configEnabled(module.key());
            boolean enabled = context.moduleManager().state(context.farmer(), module.key());
            ConfigurationSection template = moduleSection.getConfigurationSection(templateKey(configEnabled, enabled));
            if (template != null) {
                builder.putConfiguredItem(slots.get(index), template, context.withPlaceholders(Map.of(
                    "module_key", module.key(),
                    "module", context.moduleName(module.key()),
                    "module_state", moduleState(context, configEnabled, enabled),
                    "module_description", context.configManager().guiModuleDescription(module.key()),
                    "module_interval", context.moduleManager().intervalLabel(module.key()),
                    "module_material", module.iconMaterial()
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

    private String templateKey(boolean configEnabled, boolean enabled) {
        if (!configEnabled) {
            return "unavailable";
        }
        return enabled ? "enabled" : "disabled";
    }

    private String moduleState(MenuRenderContext context, boolean configEnabled, boolean enabled) {
        if (!configEnabled) {
            return context.configManager().guiLabel("modules.unavailable", "ᴋᴀᴘᴀʟɪ");
        }
        return context.configManager().guiModuleState(enabled);
    }
}
