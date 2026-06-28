package com.craftion.farmer.economy;

import com.craftion.farmer.farmer.MaterialKey;
import java.util.OptionalDouble;

public interface PriceProvider {

    OptionalDouble price(MaterialKey materialKey);
}
