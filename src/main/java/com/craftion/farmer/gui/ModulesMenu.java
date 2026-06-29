package com.craftion.farmer.gui;

import com.craftion.farmer.module.ModuleAccessResult;
import com.craftion.farmer.module.ModuleCardDescriptor;
import java.util.HashMap;
import java.util.List;
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

        List<Integer> slots = moduleSection.getIntegerList("slots");
        if (slots.isEmpty()) {
            return;
        }

        context.moduleManager().ensureDefaultStates(context.farmer());

        int index = 0;
        for (ModuleCardDescriptor card : context.moduleManager().moduleCards()) {
            if (index >= slots.size()) {
                break;
            }

            ModuleAccessResult access = context.moduleManager().access(context.player(), context.session(), card);
            boolean enabled = card.lifecycleModule() && context.moduleManager().state(context.farmer(), card.key());
            ConfigurationSection template = moduleSection.getConfigurationSection(templateKey(access, enabled));
            if (template != null) {
                builder.putConfiguredItem(slots.get(index), template, context.withPlaceholders(modulePlaceholders(context, card, access, enabled)), "AMETHYST_SHARD");
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

    private String templateKey(ModuleAccessResult access, boolean enabled) {
        if (access.status() == ModuleAccessResult.Status.UNAVAILABLE || access.status() == ModuleAccessResult.Status.CONFIG_DISABLED) {
            return "unavailable";
        }
        if (access.status() == ModuleAccessResult.Status.ROLE_DENIED || access.status() == ModuleAccessResult.Status.PERMISSION_DENIED) {
            return "locked";
        }
        return enabled ? "enabled" : "disabled";
    }

    private Map<String, String> modulePlaceholders(
        MenuRenderContext context,
        ModuleCardDescriptor card,
        ModuleAccessResult access,
        boolean enabled
    ) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("module_key", card.key());
        placeholders.put("module", context.moduleName(card.key()));
        placeholders.put("module_state", moduleState(context, access, enabled));
        placeholders.put("module_description", context.configManager().guiModuleDescription(card.key()));
        placeholders.put("module_interval", context.moduleManager().intervalLabel(card.key()));
        placeholders.put("module_metric", moduleMetric(context, card));
        placeholders.put("module_metric_label", moduleMetricLabel(card));
        placeholders.put("module_material", card.iconMaterial());
        placeholders.put("module_access", moduleAccess(context, access));
        placeholders.put("module_permission", modulePermission(context, access));
        placeholders.put("module_availability", moduleAvailability(context, access));
        placeholders.put("module_action", moduleAction(context, access, enabled));
        placeholders.put("permission", access.permission().isBlank() ? "-" : access.permission());
        return Map.copyOf(placeholders);
    }

    private String moduleState(MenuRenderContext context, ModuleAccessResult access, boolean enabled) {
        if (access.status() == ModuleAccessResult.Status.ROLE_DENIED || access.status() == ModuleAccessResult.Status.PERMISSION_DENIED) {
            return context.configManager().guiLabel("modules.locked", "ᴋɪʟɪᴛʟɪ");
        }
        if (access.status() == ModuleAccessResult.Status.UNAVAILABLE || access.status() == ModuleAccessResult.Status.CONFIG_DISABLED) {
            return context.configManager().guiLabel("modules.coming-soon", "ʏᴀᴋɪɴᴅᴀ");
        }
        return context.configManager().guiModuleState(enabled);
    }

    private String moduleAccess(MenuRenderContext context, ModuleAccessResult access) {
        if (!access.available()) {
            return context.configManager().guiLabel("modules.coming-soon", "ʏᴀᴋɪɴᴅᴀ");
        }
        if (!access.configEnabled()) {
            return context.configManager().guiLabel("modules.unavailable", "ᴋᴀᴘᴀʟɪ");
        }
        return access.roleAllowed()
            ? context.configManager().guiLabel("modules.access-ok", "ᴀᴋᴛɪғ")
            : context.configManager().guiLabel("modules.access-denied", "ʏᴇᴛᴋɪ ʏᴏᴋ");
    }

    private String modulePermission(MenuRenderContext context, ModuleAccessResult access) {
        if (!access.permissionRequired()) {
            return context.configManager().guiLabel("modules.permission-none", "ɢᴇʀᴇᴋᴍᴇᴢ");
        }
        return access.permissionAllowed()
            ? context.configManager().guiLabel("modules.permission-ok", "ᴠᴀʀ")
            : context.configManager().guiLabel("modules.permission-required", "ɢᴇʀᴇᴋɪʀ");
    }

    private String moduleAvailability(MenuRenderContext context, ModuleAccessResult access) {
        if (!access.available()) {
            return context.configManager().guiLabel("modules.coming-soon", "ʏᴀᴋɪɴᴅᴀ");
        }
        return access.configEnabled()
            ? context.configManager().guiLabel("modules.config-enabled", "ᴀᴋᴛɪғ")
            : context.configManager().guiLabel("modules.unavailable", "ᴋᴀᴘᴀʟɪ");
    }

    private String moduleAction(MenuRenderContext context, ModuleAccessResult access, boolean enabled) {
        return switch (access.status()) {
            case ALLOWED -> enabled
                ? context.configManager().guiLabel("modules.action-disable", "ᴋᴀᴘᴀᴛ")
                : context.configManager().guiLabel("modules.action-enable", "ᴀᴄ");
            case ROLE_DENIED -> context.configManager().guiLabel("modules.access-denied", "ʏᴇᴛᴋɪ ʏᴏᴋ");
            case PERMISSION_DENIED -> context.configManager().guiLabel("modules.permission-required", "ɢᴇʀᴇᴋɪʀ");
            case CONFIG_DISABLED -> context.configManager().guiLabel("modules.unavailable", "ᴋᴀᴘᴀʟɪ");
            case UNAVAILABLE -> context.configManager().guiLabel("modules.coming-soon", "ʏᴀᴋɪɴᴅᴀ");
            case UNKNOWN_MODULE -> context.configManager().guiLabel("modules.unavailable", "ᴋᴀᴘᴀʟɪ");
        };
    }

    private String moduleMetricLabel(ModuleCardDescriptor card) {
        return switch (card.key()) {
            case "auto-sell" -> "ᴀʀᴀʟɪᴋ";
            case "production-calc" -> "ᴜʀᴇᴛɪᴍ";
            case "auto-harvest" -> "ᴜʀᴜɴ";
            default -> "ᴅᴜʀᴜᴍ";
        };
    }

    private String moduleMetric(MenuRenderContext context, ModuleCardDescriptor card) {
        return switch (card.key()) {
            case "auto-sell" -> context.moduleManager().intervalLabel(card.key());
            case "production-calc" -> context.placeholders().getOrDefault("production_hour", "0") + "/sᴀᴀᴛ";
            case "auto-harvest" -> String.valueOf(context.configManager().autoHarvestCrops().size());
            default -> context.configManager().guiLabel("modules.coming-soon", "ʏᴀᴋɪɴᴅᴀ");
        };
    }
}
