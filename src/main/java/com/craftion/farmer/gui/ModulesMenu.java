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
        placeholders.put("module_description", context.guiTextService().moduleDescription(card.key()));
        placeholders.put("module_interval", moduleIntervalLabel(context, card.key()));
        placeholders.put("module_metric", moduleMetric(context, card));
        placeholders.put("module_metric_label", moduleMetricLabel(context, card));
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
            return context.guiTextService().state("locked", "locked");
        }
        if (access.status() == ModuleAccessResult.Status.CONFIG_DISABLED) {
            return context.guiTextService().state("closed", "closed");
        }
        if (access.status() == ModuleAccessResult.Status.UNAVAILABLE) {
            return context.guiTextService().state("coming-soon", "coming soon");
        }
        return context.guiTextService().state(enabled ? "active" : "closed", enabled ? "active" : "closed");
    }

    private String moduleAccess(MenuRenderContext context, ModuleAccessResult access) {
        if (!access.available()) {
            return context.guiTextService().state("coming-soon", "coming soon");
        }
        if (!access.configEnabled()) {
            return context.guiTextService().state("closed", "closed");
        }
        return access.roleAllowed()
            ? context.guiTextService().state("active", "active")
            : context.guiTextService().permission("denied", "denied");
    }

    private String modulePermission(MenuRenderContext context, ModuleAccessResult access) {
        if (!access.permissionRequired()) {
            return context.guiTextService().permission("none", "none");
        }
        return access.permissionAllowed()
            ? context.guiTextService().permission("ok", "ok")
            : context.guiTextService().permission("required", "required");
    }

    private String moduleAvailability(MenuRenderContext context, ModuleAccessResult access) {
        if (!access.available()) {
            return context.guiTextService().state("coming-soon", "coming soon");
        }
        return access.configEnabled()
            ? context.guiTextService().state("active", "active")
            : context.guiTextService().state("closed", "closed");
    }

    private String moduleAction(MenuRenderContext context, ModuleAccessResult access, boolean enabled) {
        return switch (access.status()) {
            case ALLOWED -> enabled
                ? context.guiTextService().action("close", "close")
                : context.guiTextService().action("open", "open");
            case ROLE_DENIED -> context.guiTextService().permission("denied", "denied");
            case PERMISSION_DENIED -> context.guiTextService().permission("required", "required");
            case CONFIG_DISABLED -> context.guiTextService().state("closed", "closed");
            case UNAVAILABLE -> context.guiTextService().state("coming-soon", "coming soon");
            case UNKNOWN_MODULE -> context.guiTextService().state("closed", "closed");
        };
    }

    private String moduleMetricLabel(MenuRenderContext context, ModuleCardDescriptor card) {
        return context.guiTextService().moduleMetricLabel(card.key());
    }

    private String moduleMetric(MenuRenderContext context, ModuleCardDescriptor card) {
        return switch (card.key()) {
            case "auto-sell" -> moduleIntervalLabel(context, card.key());
            case "production-calc" -> context.guiTextService().format("per-hour", "%amount%/hour", Map.of("amount", context.placeholders().getOrDefault("production_hour", "0")));
            case "auto-harvest" -> String.valueOf(context.configManager().autoHarvestCrops().size());
            default -> context.guiTextService().state("coming-soon", "coming soon");
        };
    }

    private String moduleIntervalLabel(MenuRenderContext context, String moduleKey) {
        if (!"auto-sell".equals(moduleKey)) {
            return "-";
        }
        return context.guiTextService().format("seconds", "%seconds% sec", Map.of("seconds", String.valueOf(context.configManager().autoSellIntervalSeconds())));
    }
}
