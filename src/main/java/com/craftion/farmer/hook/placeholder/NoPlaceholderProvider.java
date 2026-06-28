package com.craftion.farmer.hook.placeholder;

public final class NoPlaceholderProvider implements PlaceholderProvider {

    @Override
    public String name() {
        return "NONE";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void shutdown() {
    }
}
