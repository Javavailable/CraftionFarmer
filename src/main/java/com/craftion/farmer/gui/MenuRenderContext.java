package com.craftion.farmer.gui;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerRole;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.module.ModuleManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public record MenuRenderContext(
    Player player,
    String menuId,
    FarmerMenuSession session,
    ConfigManager configManager,
    ModuleManager moduleManager,
    ConfigurationSection menuSection,
    MaterialKey productMaterialKey,
    Map<String, String> placeholders
) {

    public MenuRenderContext {
        Objects.requireNonNull(player, "player");
        menuId = Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(configManager, "configManager");
        Objects.requireNonNull(moduleManager, "moduleManager");
        Objects.requireNonNull(menuSection, "menuSection");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
    }

    public Farmer farmer() {
        return this.session.farmer();
    }

    public Optional<MaterialKey> productMaterialKeyOptional() {
        return Optional.ofNullable(this.productMaterialKey);
    }

    public Map<String, String> withPlaceholders(Map<String, String> extraPlaceholders) {
        Map<String, String> merged = new HashMap<>(this.placeholders);
        if (extraPlaceholders != null) {
            merged.putAll(extraPlaceholders);
        }
        return Map.copyOf(merged);
    }

    public String materialName(String materialKey) {
        return this.configManager.guiMaterialName(materialKey);
    }

    public String moduleName(String moduleKey) {
        return this.configManager.guiModuleName(moduleKey);
    }

    public String roleName(FarmerRole role) {
        return this.configManager.guiRoleName(role);
    }

    public String playerName(UUID playerUuid) {
        if (playerUuid == null) {
            return "-";
        }
        String name = Bukkit.getOfflinePlayer(playerUuid).getName();
        return name == null || name.isBlank() ? playerUuid.toString() : name;
    }
}
