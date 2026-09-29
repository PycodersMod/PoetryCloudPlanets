package com.pycoder.poetrycloudplanets.planet;

import java.util.Locale;

public enum PlanetType {
    SPECIAL,
    RANDOM,
    EXTERNAL;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlanetType fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return RANDOM;
        }

        try {
            return PlanetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RANDOM;
        }
    }
}
