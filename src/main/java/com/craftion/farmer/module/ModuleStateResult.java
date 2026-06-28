package com.craftion.farmer.module;

public record ModuleStateResult(Status status, String moduleKey, boolean enabled) {

    public ModuleStateResult {
        status = status == null ? Status.FAILED : status;
        moduleKey = moduleKey == null ? "" : moduleKey.trim();
    }

    public boolean success() {
        return this.status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        DENIED,
        PERMISSION_DENIED,
        UNAVAILABLE,
        UNKNOWN_MODULE,
        MODULE_DISABLED,
        FAILED
    }
}
