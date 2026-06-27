package com.craftion.farmer.scheduler;

import java.util.Locale;
import org.bukkit.plugin.java.JavaPlugin;

public final class SchedulerFactory {

    private static final String FOLIA_MARKER_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    private SchedulerFactory() {
    }

    public static SchedulerAdapter create(JavaPlugin plugin) {
        ServerRuntime runtime = detectRuntime(plugin);
        return switch (runtime) {
            case FOLIA, LUMINOL -> new FoliaSchedulerAdapter(plugin, runtime.displayName());
            case PAPER -> new PaperSchedulerAdapter(plugin);
        };
    }

    private static ServerRuntime detectRuntime(JavaPlugin plugin) {
        String serverName = plugin.getServer().getName().toLowerCase(Locale.ROOT);

        if (serverName.contains("luminol")) {
            return ServerRuntime.LUMINOL;
        }

        if (serverName.contains("folia") || isClassPresent(FOLIA_MARKER_CLASS)) {
            return ServerRuntime.FOLIA;
        }

        return ServerRuntime.PAPER;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, SchedulerFactory.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private enum ServerRuntime {
        PAPER("Paper"),
        FOLIA("Folia"),
        LUMINOL("Luminol");

        private final String displayName;

        ServerRuntime(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return this.displayName;
        }
    }
}
