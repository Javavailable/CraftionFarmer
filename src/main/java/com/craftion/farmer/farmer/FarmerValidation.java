package com.craftion.farmer.farmer;

import java.util.UUID;

final class FarmerValidation {

    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private FarmerValidation() {
    }

    static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    static UUID requireUuid(UUID value, String fieldName) {
        if (value == null || EMPTY_UUID.equals(value)) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        return value;
    }

    static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null.");
        }
        return value;
    }

    static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive.");
        }
        return value;
    }

    static long requireNonNegative(long value, String fieldName) {
        if (value < 0L) {
            throw new IllegalArgumentException(fieldName + " cannot be negative.");
        }
        return value;
    }
}
