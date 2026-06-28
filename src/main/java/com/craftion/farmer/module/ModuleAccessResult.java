package com.craftion.farmer.module;

public record ModuleAccessResult(
    Status status,
    String moduleKey,
    String permission,
    boolean permissionRequired,
    boolean roleAllowed,
    boolean permissionAllowed,
    boolean configEnabled,
    boolean available
) {

    public ModuleAccessResult {
        status = status == null ? Status.UNKNOWN_MODULE : status;
        moduleKey = moduleKey == null ? "" : moduleKey.trim().toLowerCase(java.util.Locale.ROOT);
        permission = permission == null ? "" : permission.trim();
    }

    public boolean toggleAllowed() {
        return this.status == Status.ALLOWED;
    }

    public enum Status {
        ALLOWED,
        ROLE_DENIED,
        PERMISSION_DENIED,
        CONFIG_DISABLED,
        UNAVAILABLE,
        UNKNOWN_MODULE
    }
}
