package com.craftion.farmer.storage.repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MemberRepository {

    CompletableFuture<Boolean> isMember(String farmerId, UUID playerUuid);
}
