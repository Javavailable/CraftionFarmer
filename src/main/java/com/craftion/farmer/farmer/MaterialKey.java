package com.craftion.farmer.farmer;

import java.util.Locale;

public record MaterialKey(String value) {

    public MaterialKey {
        value = FarmerValidation.requireNonBlank(value, "materialKey").toLowerCase(Locale.ROOT);
    }

    public static MaterialKey of(String value) {
        return new MaterialKey(value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
