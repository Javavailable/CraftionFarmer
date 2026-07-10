package com.craftion.farmer.hook.spawner;

import java.util.concurrent.CompletableFuture;

interface CraftionSpawnerBridgeHandle {

    boolean register();

    CompletableFuture<Void> shutdown();
}
