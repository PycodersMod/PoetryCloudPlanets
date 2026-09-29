package com.pycoder.poetrycloudplanets.planet;

import java.util.Locale;

public enum GalaxyDistanceUnit {
    AU("AU", 4.84813681109536E-6D),
    LY("ly", 0.3066013937855506D),
    PC("pc", 1.0D),
    KPC("kpc", 1_000.0D),
    MPC("mpc", 1_000_000.0D),
    GPC("gpc", 1_000_000_000.0D);

    private final String symbol;
    private final double parsecsPerUnit;

    GalaxyDistanceUnit(String symbol, double parsecsPerUnit) {
        this.symbol = symbol;
        this.parsecsPerUnit = parsecsPerUnit;
    }

    public String symbol() {
        return symbol;
    }

    public double toParsecs(double value) {
        return value * parsecsPerUnit;
    }

    public double fromParsecs(double parsecs) {
        return parsecs / parsecsPerUnit;
    }

    public double toUnits(double parsecs) {
        return fromParsecs(parsecs);
    }

    public double fromUnits(double units) {
        return toParsecs(units);
    }

    public static GalaxyDistanceUnit parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AU;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "au" -> AU;
            case "ly" -> LY;
            case "pc" -> PC;
            case "kpc" -> KPC;
            case "mpc" -> MPC;
            case "gpc" -> GPC;
            default -> AU;
        };
    }

    public String format(double value) {
        return String.format(Locale.ROOT, "%.2f %s", value, symbol);
    }
}
