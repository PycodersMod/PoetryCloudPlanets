package com.pycoder.poetrycloudplanets.planet;

import com.pycoder.poetrycloudplanets.config.PoetryCloudPlanetsConfig;
import com.pycoder.poetrycloudplanets.material.BlockMaterialArchive;
import com.pycoder.poetrycloudplanets.material.BlockMaterialProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

final class PlanetObservationCalculator {
    private static final String[] FALLBACK_ATMOSPHERES = {
            "真空",
            "极稀薄氮氧层",
            "稀薄氮氧层",
            "标准氮氧层",
            "高压二氧化碳层",
            "甲烷雾层",
            "尘埃悬浮层",
            "离子薄层"
    };

    private static final String[] FALLBACK_SECTORS = {
            "内环",
            "中环",
            "外环",
            "边缘带",
            "断裂层",
            "核心带"
    };

    private PlanetObservationCalculator() {
    }

    static PlanetObservationRecord generate(long seed) {
        return generateFromSnapshot(BlockMaterialArchive.getInstance().sampleForPlanet(seed), seed);
    }

    static PlanetObservationRecord generateFromSnapshot(List<BlockMaterialProfile> materials, long seed) {
        Random random = new Random(mixSeed(seed));
        if (materials == null || materials.isEmpty()) {
            return generateFallback(random);
        }

        Aggregate aggregate = Aggregate.of(materials, random);
        return new PlanetObservationRecord(
                formatMagnitude(aggregate, random),
                formatSpectrum(aggregate),
                formatComposition(aggregate),
                formatGravity(aggregate, random),
                formatAtmosphere(aggregate, random),
                formatRadiation(aggregate, random),
                formatOrbit(aggregate, random, seed),
                formatSector(aggregate, random, seed)
        );
    }

    private static PlanetObservationRecord generateFallback(Random random) {
        int minColor = parseHexColor(PoetryCloudPlanetsConfig.COMMON.spectrumColorMin(), 0x000000);
        int maxColor = parseHexColor(PoetryCloudPlanetsConfig.COMMON.spectrumColorMax(), 0xFFFFFF);
        return new PlanetObservationRecord(
                String.format(Locale.ROOT, "%.2f mag", -2.40D + random.nextDouble() * 12.80D),
                String.format(Locale.ROOT, "#%06x", lerpColor(minColor, maxColor, random.nextDouble())),
                "硅(Si) 50.0% / 氧(O) 50.0%",
                String.format(Locale.ROOT, "%.2f g", 0.08D + random.nextDouble() * 4.25D),
                FALLBACK_ATMOSPHERES[random.nextInt(FALLBACK_ATMOSPHERES.length)] + " / " + String.format(Locale.ROOT, "%.2f bar", 0.05D + random.nextDouble()),
                String.format(Locale.ROOT, "%.2f μSv/h", random.nextDouble() * 180.0D),
                "e=" + String.format(Locale.ROOT, "%.2f", random.nextDouble() * 0.72D)
                        + " / i=" + String.format(Locale.ROOT, "%.1f", random.nextDouble() * 32.0D)
                        + "° / 漂移 " + String.format(Locale.ROOT, "%.1f", random.nextDouble() * 12.0D) + "%",
                FALLBACK_SECTORS[random.nextInt(FALLBACK_SECTORS.length)] + " / 星域 " + String.format(Locale.ROOT, "%02d-%02d", random.nextInt(18) + 1, random.nextInt(18) + 1)
        );
    }

    private static String formatMagnitude(Aggregate aggregate, Random random) {
        double min = PoetryCloudPlanetsConfig.COMMON.brightnessMinMag();
        double max = PoetryCloudPlanetsConfig.COMMON.brightnessMaxMag();
        double low = Math.min(min, max);
        double high = Math.max(min, max);

        double lightFactor = clamp(aggregate.averageLight / 15.0D, 0.0D, 1.0D);
        double reflectFactor = aggregate.averageReflectance;
        double densityFactor = clamp(aggregate.averageDensity / 5.0D, 0.0D, 1.0D);
        double radioFactor = clamp(aggregate.averageRadioactivity / 2.0D, 0.0D, 1.0D);
        double materialFactor = clamp(aggregate.materialCount / 12.0D, 0.0D, 1.0D);
        double brightnessScore = lightFactor * 0.42D
                + reflectFactor * 0.24D
                + radioFactor * 0.12D
                + (1.0D - densityFactor) * 0.12D
                + materialFactor * 0.10D;
        brightnessScore = clamp(brightnessScore + (random.nextDouble() - 0.5D) * 0.10D, 0.0D, 1.0D);

        double magnitude = high - brightnessScore * (high - low);
        magnitude = clamp(magnitude + (random.nextDouble() - 0.5D) * 0.25D, low, high);
        return String.format(Locale.ROOT, "%.2f mag", magnitude);
    }

    private static String formatSpectrum(Aggregate aggregate) {
        int baseColor = clampRgb(
                (int) Math.round(aggregate.red / aggregate.totalWeight),
                (int) Math.round(aggregate.green / aggregate.totalWeight),
                (int) Math.round(aggregate.blue / aggregate.totalWeight)
        );

        int minColor = parseHexColor(PoetryCloudPlanetsConfig.COMMON.spectrumColorMin(), 0x000000);
        int maxColor = parseHexColor(PoetryCloudPlanetsConfig.COMMON.spectrumColorMax(), 0xFFFFFF);
        double paletteBias = clamp(
                aggregate.averageReflectance * 0.45D
                        + aggregate.averageLight / 15.0D * 0.30D
                        + aggregate.averageRadioactivity * 0.10D
                        + aggregate.materialCount / 12.0D * 0.15D,
                0.0D,
                1.0D
        );
        int paletteColor = lerpColor(minColor, maxColor, paletteBias);
        int finalColor = lerpColor(baseColor, paletteColor, 0.45D);
        return String.format(Locale.ROOT, "#%06x", finalColor);
    }

    private static String formatComposition(Aggregate aggregate) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(aggregate.elements.entrySet());
        entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        if (entries.isEmpty()) {
            return "硅(Si) 50.0% / 氧(O) 50.0%";
        }

        int limit = Math.min(4, entries.size());
        double total = 0.0D;
        for (int i = 0; i < limit; i++) {
            total += entries.get(i).getValue();
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> entry = entries.get(i);
            if (i > 0) {
                builder.append(" / ");
            }
            double share = total <= 0.0D ? 0.0D : entry.getValue() / total;
            builder.append(entry.getKey())
                    .append(' ')
                    .append(String.format(Locale.ROOT, "%.1f", share * 100.0D))
                    .append('%');
        }

        return builder.toString();
    }

    private static String formatGravity(Aggregate aggregate, Random random) {
        double min = PoetryCloudPlanetsConfig.COMMON.gravityMin();
        double max = PoetryCloudPlanetsConfig.COMMON.gravityMax();
        double low = Math.min(min, max);
        double high = Math.max(min, max);

        double gravityScore = clamp(
                aggregate.averageDensity / 5.0D * 0.58D
                        + aggregate.materialCount / 16.0D * 0.16D
                        + aggregate.averageReflectance * 0.14D
                        + aggregate.averageRadioactivity * 0.06D
                        + (random.nextDouble() - 0.5D) * 0.08D,
                0.0D,
                1.0D
        );
        double gravity = low + gravityScore * (high - low);
        return String.format(Locale.ROOT, "%.2f g", gravity);
    }

    private static String formatAtmosphere(Aggregate aggregate, Random random) {
        List<? extends String> options = PoetryCloudPlanetsConfig.COMMON.atmosphereOptions.get();
        if (options.isEmpty()) {
            options = List.of(FALLBACK_ATMOSPHERES);
        }

        String descriptor;
        if (aggregate.averageRadioactivity > 0.65D) {
            descriptor = pickContaining(options, "离子", random);
        } else if (aggregate.averageDensity < 1.0D) {
            descriptor = pickContaining(options, "稀薄", random);
        } else if (aggregate.averageReflectance > 0.55D) {
            descriptor = pickContaining(options, "尘埃", random);
        } else if (aggregate.averageDensity > 3.0D) {
            descriptor = pickContaining(options, "高压", random);
        } else {
            descriptor = options.get(Math.floorMod(random.nextInt(), options.size()));
        }

        double pressure = pressureForAtmosphere(descriptor, aggregate, random);
        return descriptor + " / " + String.format(Locale.ROOT, "%.2f bar", pressure);
    }

    private static String formatRadiation(Aggregate aggregate, Random random) {
        double min = PoetryCloudPlanetsConfig.COMMON.radiationMin();
        double max = PoetryCloudPlanetsConfig.COMMON.radiationMax();
        double low = Math.min(min, max);
        double high = Math.max(min, max);

        double radiationScore = clamp(
                aggregate.averageRadioactivity * 0.72D
                        + aggregate.averageLight / 15.0D * 0.12D
                        + aggregate.materialCount / 16.0D * 0.08D
                        + (random.nextDouble() - 0.5D) * 0.12D,
                0.0D,
                1.0D
        );
        double radiation = low + radiationScore * (high - low);
        return String.format(Locale.ROOT, "%.2f μSv/h", radiation);
    }

    private static String formatOrbit(Aggregate aggregate, Random random, long seed) {
        double eccentricity = clamp(
                aggregate.averageDensity / 5.0D * 0.22D
                        + aggregate.averageRadioactivity * 0.08D
                        + random.nextDouble() * 0.42D,
                0.0D,
                0.95D
        );
        double inclination = clamp(
                aggregate.averageReflectance * 18.0D
                        + aggregate.materialCount * 0.95D
                        + random.nextDouble() * 10.0D,
                0.0D,
                90.0D
        );
        double drift = clamp(
                aggregate.averageRadioactivity * 16.0D
                        + (random.nextDouble() - 0.5D) * 5.0D
                        + aggregate.materialCount * 0.55D,
                0.0D,
                100.0D
        );
        return "e=" + String.format(Locale.ROOT, "%.2f", eccentricity)
                + " / i=" + String.format(Locale.ROOT, "%.1f", inclination) + "°"
                + " / 漂移 " + String.format(Locale.ROOT, "%.1f", drift) + "%";
    }

    private static String formatSector(Aggregate aggregate, Random random, long seed) {
        List<? extends String> options = PoetryCloudPlanetsConfig.COMMON.sectorOptions.get();
        if (options.isEmpty()) {
            options = List.of(FALLBACK_SECTORS);
        }

        long mixed = mixSeed(seed) ^ Double.doubleToLongBits(aggregate.averageDensity) ^ Double.doubleToLongBits(aggregate.averageReflectance);
        int ringIndex = Math.floorMod((int) mixed, options.size());
        String ring = options.get(ringIndex);
        int x = random.nextInt(18) + 1;
        int y = random.nextInt(18) + 1;
        return ring + " / 星域 " + String.format(Locale.ROOT, "%02d-%02d", x, y);
    }

    private static String pickContaining(List<? extends String> options, String keyword, Random random) {
        for (String option : options) {
            if (option.contains(keyword)) {
                return option;
            }
        }
        return options.get(Math.floorMod(random.nextInt(), options.size()));
    }

    private static double pressureForAtmosphere(String descriptor, Aggregate aggregate, Random random) {
        if (descriptor.contains("真空")) {
            return 0.00D;
        }
        if (descriptor.contains("离子")) {
            return 0.05D + random.nextDouble() * 0.25D + aggregate.averageRadioactivity * 0.05D;
        }
        if (descriptor.contains("稀薄")) {
            return 0.10D + random.nextDouble() * 0.35D + aggregate.averageDensity * 0.02D;
        }
        if (descriptor.contains("标准")) {
            return 0.75D + random.nextDouble() * 0.55D + aggregate.averageDensity * 0.05D;
        }
        if (descriptor.contains("高压")) {
            return 1.40D + random.nextDouble() * 2.20D + aggregate.averageDensity * 0.10D;
        }
        if (descriptor.contains("甲烷")) {
            return 0.20D + random.nextDouble() * 0.95D + aggregate.averageReflectance * 0.10D;
        }
        if (descriptor.contains("尘埃")) {
            return 0.05D + random.nextDouble() * 0.25D + aggregate.averageReflectance * 0.08D;
        }
        return 0.05D + random.nextDouble() * 0.50D;
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

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static int clampRgb(int red, int green, int blue) {
        return (clamp(red, 0, 255) << 16)
                | (clamp(green, 0, 255) << 8)
                | clamp(blue, 0, 255);
    }

    private static int lerpColor(int from, int to, double t) {
        double ratio = clamp(t, 0.0D, 1.0D);
        int fromR = (from >>> 16) & 0xFF;
        int fromG = (from >>> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >>> 16) & 0xFF;
        int toG = (to >>> 8) & 0xFF;
        int toB = to & 0xFF;
        int red = clamp((int) Math.round(fromR + (toR - fromR) * ratio), 0, 255);
        int green = clamp((int) Math.round(fromG + (toG - fromG) * ratio), 0, 255);
        int blue = clamp((int) Math.round(fromB + (toB - fromB) * ratio), 0, 255);
        return clampRgb(red, green, blue);
    }

    private static int parseHexColor(String rawHex, int fallback) {
        if (rawHex == null || rawHex.isBlank()) {
            return fallback;
        }

        String value = rawHex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }

        if (value.length() != 6) {
            return fallback;
        }

        try {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private record Aggregate(
            int materialCount,
            double totalWeight,
            double averageDensity,
            double averageLight,
            double averageReflectance,
            double averageRadioactivity,
            double red,
            double green,
            double blue,
            Map<String, Double> elements
    ) {
        private static Aggregate of(List<BlockMaterialProfile> materials, Random random) {
            double totalWeight = 0.0D;
            double weightedDensity = 0.0D;
            double weightedLight = 0.0D;
            double weightedReflectance = 0.0D;
            double weightedRadioactivity = 0.0D;
            double weightedRed = 0.0D;
            double weightedGreen = 0.0D;
            double weightedBlue = 0.0D;
            LinkedHashMap<String, Double> elements = new LinkedHashMap<>();

            for (BlockMaterialProfile profile : materials) {
                double weight = 0.55D + random.nextDouble() * 0.85D + profile.reflectance() * 0.30D + profile.lightLevel() * 0.02D;
                totalWeight += weight;
                weightedDensity += profile.density() * weight;
                weightedLight += profile.lightLevel() * weight;
                weightedReflectance += profile.reflectance() * weight;
                weightedRadioactivity += profile.radioactivity() * weight;
                weightedRed += ((profile.color() >>> 16) & 0xFF) * weight;
                weightedGreen += ((profile.color() >>> 8) & 0xFF) * weight;
                weightedBlue += (profile.color() & 0xFF) * weight;

                for (Map.Entry<String, Double> entry : profile.elements().entrySet()) {
                    elements.merge(entry.getKey(), entry.getValue() * weight, Double::sum);
                }
            }

            if (totalWeight <= 0.0D) {
                totalWeight = 1.0D;
            }

            return new Aggregate(
                    materials.size(),
                    totalWeight,
                    weightedDensity / totalWeight,
                    weightedLight / totalWeight,
                    weightedReflectance / totalWeight,
                    weightedRadioactivity / totalWeight,
                    weightedRed,
                    weightedGreen,
                    weightedBlue,
                    elements
            );
        }
    }
}
