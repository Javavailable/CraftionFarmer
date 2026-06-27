package com.craftion.farmer.farmer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FarmerStorage {

    private final ConcurrentMap<MaterialKey, Long> amounts = new ConcurrentHashMap<>();

    public FarmerStorage() {
    }

    public FarmerStorage(Map<MaterialKey, Long> amounts) {
        FarmerValidation.requireNonNull(amounts, "amounts").forEach(this::setAmount);
    }

    public long amount(MaterialKey materialKey) {
        FarmerValidation.requireNonNull(materialKey, "materialKey");
        return this.amounts.getOrDefault(materialKey, 0L);
    }

    public void setAmount(MaterialKey materialKey, long amount) {
        FarmerValidation.requireNonNull(materialKey, "materialKey");
        FarmerValidation.requireNonNegative(amount, "amount");
        this.amounts.put(materialKey, amount);
    }

    public void remove(MaterialKey materialKey) {
        FarmerValidation.requireNonNull(materialKey, "materialKey");
        this.amounts.remove(materialKey);
    }

    public Map<MaterialKey, Long> snapshot() {
        return Map.copyOf(this.amounts);
    }

    public FarmerStorage copy() {
        return new FarmerStorage(snapshot());
    }
}
