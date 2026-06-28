package com.craftion.farmer.hook.placeholder;

public interface PlaceholderProvider {

    String name();

    boolean isAvailable();

    void initialize();

    void shutdown();
}
