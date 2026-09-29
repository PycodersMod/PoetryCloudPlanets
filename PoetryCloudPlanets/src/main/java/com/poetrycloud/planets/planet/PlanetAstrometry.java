package com.poetrycloud.planets.planet;

import java.util.Locale;

public final class PlanetAstrometry {
    private PlanetAstrometry() {
    }

    public static double parseMagnitude(String brightness) {
        if (brightness == null || brightness.isBlank()) {
            return 0.0D;
        }

        String text = brightness.trim().toLowerCase(Locale.ROOT);
        if (text.endsWith("mag")) {
            text = text.substring(0, text.length() - 3).trim();
        }

        int separator = text.indexOf(' ');
        if (separator >= 0) {
            text = text.substring(0, separator).trim();
        }

        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    public static double apparentMagnitude(double absoluteMagnitude, double distance, GalaxyDistanceUnit unit) {
        if (unit == null || distance <= 0.0D) {
            return absoluteMagnitude;
        }

        double parsecDistance = unit.toParsecs(distance);
        if (!(parsecDistance > 0.0D)) {
            return absoluteMagnitude;
        }

        double observed = absoluteMagnitude + 5.0D * (Math.log10(parsecDistance) - 1.0D);
        return Double.isFinite(observed) ? observed : absoluteMagnitude;
    }

    public static String formatDistance(double distance, GalaxyDistanceUnit unit) {
        GalaxyDistanceUnit displayUnit = unit == null ? GalaxyDistanceUnit.AU : unit;
        return String.format(Locale.ROOT, "%.2f %s", distance, displayUnit.symbol());
    }

    public static String formatMagnitude(double magnitude) {
        return String.format(Locale.ROOT, "%.2f mag", magnitude);
    }
}
