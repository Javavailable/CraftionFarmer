package com.craftion.farmer.collect;

import com.craftion.farmer.farmer.Farmer;
import java.util.Objects;
import java.util.Optional;

public record FarmerDepositOutcome(CollectResult result, Farmer farmer) {

    public FarmerDepositOutcome {
        Objects.requireNonNull(result, "result");
    }

    public static FarmerDepositOutcome skipped(CollectResult result) {
        return new FarmerDepositOutcome(result, null);
    }

    public Optional<Farmer> resolvedFarmer() {
        return Optional.ofNullable(this.farmer);
    }
}
