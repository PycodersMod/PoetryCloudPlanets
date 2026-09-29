package com.poetrycloud.planets.planet;

import com.poetrycloud.planets.config.PoetryCloudPlanetsConfig;

import java.util.List;
import java.util.Random;

final class GalaxyCoordinateGenerator {
    private static final double DEFAULT_MIN_LIGHT_YEARS = 5_000.0D;
    private static final double DEFAULT_MAX_LIGHT_YEARS = 50_000.0D;

    private GalaxyCoordinateGenerator() {
    }

    static GalaxyCoordinates generate(long seed, List<SpecialPlanetTemplate> specialTemplates, GalaxyDistanceUnit unit) {
        Random random = new Random(mixSeed(seed));
        GalaxyDistanceUnit displayUnit = unit == null ? GalaxyDistanceUnit.AU : unit;
        double minScale = Math.min(PoetryCloudPlanetsConfig.COMMON.randomPlanetDistanceScaleMin(), PoetryCloudPlanetsConfig.COMMON.randomPlanetDistanceScaleMax());
        double maxScale = Math.max(PoetryCloudPlanetsConfig.COMMON.randomPlanetDistanceScaleMin(), PoetryCloudPlanetsConfig.COMMON.randomPlanetDistanceScaleMax());

        double innerRadius;
        double outerRadius;
        if (specialTemplates == null || specialTemplates.isEmpty()) {
            innerRadius = displayUnit.fromParsecs(GalaxyDistanceUnit.LY.toParsecs(DEFAULT_MIN_LIGHT_YEARS));
            outerRadius = displayUnit.fromParsecs(GalaxyDistanceUnit.LY.toParsecs(DEFAULT_MAX_LIGHT_YEARS));
        } else {
            double minDistance = Double.POSITIVE_INFINITY;
            double maxDistance = 0.0D;
            for (SpecialPlanetTemplate template : specialTemplates) {
                if (template == null) {
                    continue;
                }

                double distance = template.coordinates().distanceToOrigin();
                if (distance < minDistance) {
                    minDistance = distance;
                }
                if (distance > maxDistance) {
                    maxDistance = distance;
                }
            }

            if (!Double.isFinite(minDistance) || maxDistance <= 0.0D) {
                innerRadius = displayUnit.fromParsecs(GalaxyDistanceUnit.LY.toParsecs(DEFAULT_MIN_LIGHT_YEARS));
                outerRadius = displayUnit.fromParsecs(GalaxyDistanceUnit.LY.toParsecs(DEFAULT_MAX_LIGHT_YEARS));
            } else {
                innerRadius = minDistance * minScale;
                outerRadius = maxDistance * maxScale;
                if (!(innerRadius > 0.0D) || !(outerRadius > innerRadius)) {
                    innerRadius = displayUnit.fromParsecs(GalaxyDistanceUnit.LY.toParsecs(DEFAULT_MIN_LIGHT_YEARS));
                    outerRadius = displayUnit.fromParsecs(GalaxyDistanceUnit.LY.toParsecs(DEFAULT_MAX_LIGHT_YEARS));
                }
            }
        }

        double radius = sampleRadius(random, innerRadius, outerRadius);
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double z = random.nextDouble() * 2.0D - 1.0D;
        double radialFactor = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
        double x = radius * radialFactor * Math.cos(theta);
        double y = radius * radialFactor * Math.sin(theta);
        double zValue = radius * z;
        return new GalaxyCoordinates(x, y, zValue);
    }

    private static double sampleRadius(Random random, double innerRadius, double outerRadius) {
        double low = Math.max(0.0D, Math.min(innerRadius, outerRadius));
        double high = Math.max(low, Math.max(innerRadius, outerRadius));
        double lowVolume = low * low * low;
        double highVolume = high * high * high;
        double sampledVolume = lowVolume + (highVolume - lowVolume) * random.nextDouble();
        return Math.cbrt(sampledVolume);
    }

    private static long mixSeed(long seed) {
        long value = seed ^ 0x9E3779B97F4A7C15L;
        value ^= (value >>> 33);
        value *= 0xFF51AFD7ED558CCDL;
        value ^= (value >>> 33);
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= (value >>> 33);
        return value;
    }
}
