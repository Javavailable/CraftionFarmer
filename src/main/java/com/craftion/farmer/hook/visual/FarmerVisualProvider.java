package com.craftion.farmer.hook.visual;

import com.craftion.farmer.farmer.Farmer;
import java.util.Collection;

public interface FarmerVisualProvider {

    VisualProviderType type();

    boolean isAvailable();

    void spawn(Farmer farmer);

    void remove(Farmer farmer);

    void remove(String farmerId);

    void reconcile(Collection<Farmer> farmers);

    void shutdown();
}
