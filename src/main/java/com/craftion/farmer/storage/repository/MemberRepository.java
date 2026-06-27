package com.craftion.farmer.storage.repository;

import com.craftion.farmer.farmer.FarmerMember;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MemberRepository {

    CompletableFuture<Boolean> isMember(String farmerId, UUID playerUuid);

    CompletableFuture<List<FarmerMember>> findByFarmerId(String farmerId);

    CompletableFuture<Void> save(FarmerMember member);

    CompletableFuture<Void> delete(String farmerId, UUID playerUuid);
}
