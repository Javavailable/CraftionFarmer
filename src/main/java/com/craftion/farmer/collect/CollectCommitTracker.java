package com.craftion.farmer.collect;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records the deterministic result immediately after the Farmer deposit backend returns.
 *
 * <p>This lets fail-open integrations distinguish a pre-commit failure from an unexpected failure
 * after storage was already mutated, without replaying the deposit.</p>
 */
public final class CollectCommitTracker {

    private final AtomicReference<CollectResult> committedResult = new AtomicReference<>();

    void record(CollectResult result) {
        this.committedResult.compareAndSet(null, Objects.requireNonNull(result, "result"));
    }

    public Optional<CollectResult> committedResult() {
        return Optional.ofNullable(this.committedResult.get());
    }
}
