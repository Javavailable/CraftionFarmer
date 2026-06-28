package com.craftion.farmer.economy;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.farmer.MaterialKey;
import java.util.Objects;
import java.util.OptionalDouble;

public final class ConfigPriceProvider implements PriceProvider {

    private final ConfigManager configManager;

    public ConfigPriceProvider(ConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
    }

    @Override
    public OptionalDouble price(MaterialKey materialKey) {
        return this.configManager.price(materialKey);
    }
}
