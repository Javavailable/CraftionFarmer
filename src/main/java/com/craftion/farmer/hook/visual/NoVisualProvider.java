package com.craftion.farmer.hook.visual;

import com.craftion.farmer.farmer.Farmer;
import java.util.Collection;

public final class NoVisualProvider implements FarmerVisualProvider {

    @Override
    public VisualProviderType type() {
        return VisualProviderType.NONE;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void spawn(Farmer farmer) {
    }

    @Override
    public void remove(Farmer farmer) {
    }

    @Override
    public void remove(String farmerId) {
    }

    @Override
    public void reconcile(Collection<Farmer> farmers) {
    }

    @Override
    public void shutdown() {
    }
}
