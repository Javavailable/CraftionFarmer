package com.craftion.farmer.message;

import com.craftion.farmer.farmer.FarmerRole;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public final class GuiTextService {

    private static final String GUI_ROOT = "gui";

    private final MessageService messageService;

    public GuiTextService(MessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    public String text(String path, String fallback, Map<String, String> placeholders) {
        return this.messageService.applyPlaceholders(this.messageService.messageString(path, fallback), placeholders);
    }

    public List<String> list(String path, List<String> fallbackList, Map<String, String> placeholders) {
        return this.messageService.applyPlaceholders(this.messageService.messageList(path, fallbackList), placeholders);
    }

    public String menuTitle(String menuId, ConfigurationSection menuSection, Map<String, String> placeholders, String fallback) {
        String baseMenuId = baseMenuId(menuId);
        String path = titlePath(menuSection, GUI_ROOT + ".menus." + baseMenuId + ".title");
        String directFallback = menuSection == null ? null : menuSection.getString("title");
        return text(path, valueOrFallback(directFallback, fallback), placeholders);
    }

    public String itemName(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return null;
        }
        String path = itemFieldPath(section, "name", "name-key");
        return text(path, section.getString("name"), placeholders);
    }

    public List<String> itemLore(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return List.of();
        }
        String path = itemFieldPath(section, "lore", "lore-key");
        return list(path, section.getStringList("lore"), placeholders);
    }

    public String state(String key, String fallback) {
        return text(GUI_ROOT + ".shared.states." + normalizeKey(key), fallback, Map.of());
    }

    public String action(String key, String fallback) {
        return text(GUI_ROOT + ".shared.actions." + normalizeKey(key), fallback, Map.of());
    }

    public String label(String key, String fallback) {
        return text(GUI_ROOT + ".shared.labels." + normalizeKey(key), fallback, Map.of());
    }

    public String permission(String key, String fallback) {
        return text(GUI_ROOT + ".shared.permissions." + normalizeKey(key), fallback, Map.of());
    }

    public String unit(String key, String fallback) {
        return text(GUI_ROOT + ".shared.units." + normalizeKey(key), fallback, Map.of());
    }

    public String roleName(FarmerRole role) {
        FarmerRole safeRole = role == null ? FarmerRole.VIEWER : role;
        String key = safeRole.name().toLowerCase(Locale.ROOT);
        return text(GUI_ROOT + ".shared.roles." + key, fallbackLabel(key), Map.of());
    }

    public String materialName(String materialKey) {
        String normalizedKey = normalizeKey(materialKey);
        return text(GUI_ROOT + ".shared.material-names." + normalizedKey, fallbackLabel(normalizedKey), Map.of());
    }

    public String moduleName(String moduleKey) {
        String normalizedKey = normalizeKey(moduleKey);
        return text(GUI_ROOT + ".shared.module-names." + normalizedKey, fallbackLabel(normalizedKey), Map.of());
    }

    public String moduleDescription(String moduleKey) {
        String normalizedKey = normalizeKey(moduleKey);
        return text(GUI_ROOT + ".shared.module-descriptions." + normalizedKey, "module", Map.of());
    }

    public String moduleMetricLabel(String moduleKey) {
        String normalizedKey = normalizeKey(moduleKey);
        return text(GUI_ROOT + ".shared.module-metric-labels." + normalizedKey, label("state", "state"), Map.of());
    }

    public String format(String key, String fallback, Map<String, String> placeholders) {
        return text(GUI_ROOT + ".shared.formats." + normalizeKey(key), fallback, placeholders);
    }

    private String titlePath(ConfigurationSection section, String fallback) {
        if (section == null) {
            return fallback;
        }

        String titleKey = section.getString("title-key");
        if (titleKey != null && !titleKey.isBlank()) {
            return titleKey.trim();
        }

        String textKey = section.getString("text-key");
        if (textKey != null && !textKey.isBlank()) {
            return textKey.trim() + ".title";
        }
        return fallback;
    }

    private String itemFieldPath(ConfigurationSection section, String field, String fieldKey) {
        String configured = section.getString(fieldKey);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }

        String textKey = section.getString("text-key");
        if (textKey != null && !textKey.isBlank()) {
            return textKey.trim() + "." + field;
        }
        return textBasePath(section) + "." + field;
    }

    private String textBasePath(ConfigurationSection section) {
        String currentPath = section.getCurrentPath();
        if (currentPath == null || currentPath.isBlank()) {
            return GUI_ROOT + ".menus.unknown.items.unknown";
        }

        String[] parts = currentPath.split("\\.");
        if (parts.length == 5 && GUI_ROOT.equals(parts[0]) && "menus".equals(parts[1]) && "items".equals(parts[3])) {
            String id = section.getString("id");
            if (id != null && !id.isBlank()) {
                return GUI_ROOT + ".menus." + parts[2] + ".items." + id.trim();
            }
        }
        return currentPath;
    }

    private String baseMenuId(String menuId) {
        if (menuId == null || menuId.isBlank()) {
            return "main";
        }
        int separator = menuId.indexOf(':');
        return separator < 0 ? menuId : menuId.substring(0, separator);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }
        return key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String fallbackLabel(String key) {
        return key == null || key.isBlank() ? "-" : key.replace('_', ' ').replace('-', ' ');
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
