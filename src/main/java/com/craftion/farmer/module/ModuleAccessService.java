package com.craftion.farmer.module;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.gui.FarmerMenuAccess;
import com.craftion.farmer.gui.FarmerMenuSession;
import java.util.Objects;
import org.bukkit.entity.Player;

public final class ModuleAccessService {

    private final ConfigManager configManager;

    public ModuleAccessService(ConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
    }

    public ModuleAccessResult evaluate(Player player, FarmerMenuSession session, ModuleCardDescriptor card) {
        if (card == null) {
            return new ModuleAccessResult(ModuleAccessResult.Status.UNKNOWN_MODULE, "", "", false, false, false, false, false);
        }

        String permission = this.configManager.modulePermission(card.key());
        boolean permissionRequired = this.configManager.modulePermissionRequired(card.key());
        boolean roleAllowed = session != null && FarmerMenuAccess.MANAGER.allows(session.role());
        boolean permissionAllowed = !permissionRequired || (player != null && player.hasPermission(permission));
        boolean configEnabled = this.configManager.moduleEnabled(card.key());
        if (card.unavailable()) {
            return new ModuleAccessResult(
                ModuleAccessResult.Status.UNAVAILABLE,
                card.key(),
                permission,
                permissionRequired,
                roleAllowed,
                permissionAllowed,
                configEnabled,
                false
            );
        }
        if (!configEnabled) {
            return new ModuleAccessResult(
                ModuleAccessResult.Status.CONFIG_DISABLED,
                card.key(),
                permission,
                permissionRequired,
                roleAllowed,
                permissionAllowed,
                false,
                true
            );
        }
        if (!roleAllowed) {
            return new ModuleAccessResult(
                ModuleAccessResult.Status.ROLE_DENIED,
                card.key(),
                permission,
                permissionRequired,
                false,
                permissionAllowed,
                true,
                true
            );
        }
        if (!permissionAllowed) {
            return new ModuleAccessResult(
                ModuleAccessResult.Status.PERMISSION_DENIED,
                card.key(),
                permission,
                true,
                true,
                false,
                true,
                true
            );
        }
        return new ModuleAccessResult(ModuleAccessResult.Status.ALLOWED, card.key(), permission, permissionRequired, true, true, true, true);
    }
}
