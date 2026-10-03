package com.pycoder.poetrycloudplanets.planet;

import com.pycoder.poetrycloudplanets.material.BlockMaterialArchive;
import com.pycoder.poetrycloudplanets.material.BlockMaterialProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public record PlanetObservationRecord(
        String brightness,
        String spectrum,
        String composition,
        String gravity,
        String atmosphere,
        String radiation,
        String orbitalAnomaly,
        String sector
) {
    private static final String TAG_BRIGHTNESS = "Brightness";
    private static final String TAG_SPECTRUM = "Spectrum";
    private static final String TAG_COMPOSITION = "Composition";
    private static final String TAG_GRAVITY = "Gravity";
    private static final String TAG_ATMOSPHERE = "Atmosphere";
    private static final String TAG_RADIATION = "Radiation";
    private static final String TAG_ORBITAL_ANOMALY = "OrbitalAnomaly";
    private static final String TAG_SECTOR = "Sector";
    private static final String PLACEHOLDER = "待生成";
    private static final String[] ELEMENT_POOL = {
            "铁(Fe)",
            "金(Au)",
            "硅(Si)",
            "碳(C)",
            "氧(O)",
            "镁(Mg)",
            "镍(Ni)",
            "铝(Al)",
            "钠(Na)",
            "硫(S)",
            "钛(Ti)"
    };
    private static final String[] ATMOSPHERE_POOL = {
            "真空",
            "极稀薄氮氧层",
            "稀薄氮氧层",
            "标准氮氧层",
            "高压二氧化碳层",
            "甲烷雾层",
            "尘埃悬浮层",
            "离子薄层"
    };
    private static final String[] SECTOR_POOL = {
            "内环",
            "中环",
            "外环",
            "边缘带",
            "断裂层",
            "核心带"
    };

    public PlanetObservationRecord {
        brightness = normalize(brightness);
        spectrum = normalize(spectrum);
        composition = normalize(composition);
        gravity = normalize(gravity);
        atmosphere = normalize(atmosphere);
        radiation = normalize(radiation);
        orbitalAnomaly = normalize(orbitalAnomaly);
        sector = normalize(sector);
    }

    public static PlanetObservationRecord placeholder() {
        return new PlanetObservationRecord(PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER);
    }

    public boolean isPlaceholder() {
        return PLACEHOLDER.equals(brightness)
                && PLACEHOLDER.equals(spectrum)
                && PLACEHOLDER.equals(composition)
                && PLACEHOLDER.equals(gravity)
                && PLACEHOLDER.equals(atmosphere)
                && PLACEHOLDER.equals(radiation)
                && PLACEHOLDER.equals(orbitalAnomaly)
                && PLACEHOLDER.equals(sector);
    }

    public double brightnessMagnitude() {
        return PlanetAstrometry.parseMagnitude(brightness);
    }

    public static PlanetObservationRecord generated(long seed) {
        return PlanetObservationCalculator.generate(seed);
    }

    public static PlanetObservationRecord generatedFromMaterialSnapshot(List<String> materialIds, long seed) {
        if (materialIds == null || materialIds.isEmpty()) {
            return generated(seed);
        }

        ArrayList<BlockMaterialProfile> materials = new ArrayList<>(materialIds.size());
        for (String rawId : materialIds) {
            if (rawId == null) {
                continue;
            }

            String trimmed = rawId.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            ResourceLocation blockId = ResourceLocation.tryParse(trimmed);
            if (blockId == null) {
                continue;
            }

            materials.add(BlockMaterialArchive.getInstance().resolve(blockId));
        }

        if (materials.isEmpty()) {
            return generated(seed);
        }

        return PlanetObservationCalculator.generateFromSnapshot(materials, seed);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_BRIGHTNESS, brightness);
        tag.putString(TAG_SPECTRUM, spectrum);
        tag.putString(TAG_COMPOSITION, composition);
        tag.putString(TAG_GRAVITY, gravity);
        tag.putString(TAG_ATMOSPHERE, atmosphere);
        tag.putString(TAG_RADIATION, radiation);
        tag.putString(TAG_ORBITAL_ANOMALY, orbitalAnomaly);
        tag.putString(TAG_SECTOR, sector);
        return tag;
    }

    public static PlanetObservationRecord fromTag(CompoundTag tag) {
        return new PlanetObservationRecord(
                tag.getString(TAG_BRIGHTNESS),
                tag.getString(TAG_SPECTRUM),
                tag.getString(TAG_COMPOSITION),
                tag.getString(TAG_GRAVITY),
                tag.getString(TAG_ATMOSPHERE),
                tag.getString(TAG_RADIATION),
                tag.getString(TAG_ORBITAL_ANOMALY),
                tag.getString(TAG_SECTOR)
        );
    }

    private static String normalize(String value) {
        if (value == null) {
            return PLACEHOLDER;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? PLACEHOLDER : trimmed;
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

    private static String pick(Random random, String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static String formatMagnitude(Random random) {
        double magnitude = -2.40 + random.nextDouble() * 12.80;
        return String.format(Locale.ROOT, "%.2f mag", magnitude);
    }

    private static String formatRgb(Random random) {
        return "#" + String.format(Locale.ROOT, "%06x", random.nextInt(0x1000000));
    }

    private static String formatComposition(Random random) {
        List<String> elements = new ArrayList<>(List.of(ELEMENT_POOL));
        Collections.shuffle(elements, random);

        double weight1 = 2.5 + random.nextDouble() * 6.0;
        double weight2 = 1.5 + random.nextDouble() * 4.0;
        double weight3 = 0.8 + random.nextDouble() * 3.0;
        double weight4 = 0.3 + random.nextDouble() * 2.0;
        double total = weight1 + weight2 + weight3 + weight4;

        return formatElementShare(elements.get(0), weight1 / total)
                + " / " + formatElementShare(elements.get(1), weight2 / total)
                + " / " + formatElementShare(elements.get(2), weight3 / total)
                + " / " + formatElementShare(elements.get(3), weight4 / total);
    }

    private static String formatElementShare(String element, double share) {
        return element + " " + String.format(Locale.ROOT, "%.1f", share * 100.0) + "%";
    }

    private static String formatGravity(Random random) {
        double gravity = 0.08 + random.nextDouble() * 4.25;
        return String.format(Locale.ROOT, "%.2f g", gravity);
    }

    private static String formatAtmosphere(Random random) {
        int index = random.nextInt(ATMOSPHERE_POOL.length);
        double pressure = switch (index) {
            case 0 -> 0.00;
            case 1 -> 0.03 + random.nextDouble() * 0.10;
            case 2 -> 0.10 + random.nextDouble() * 0.35;
            case 3 -> 0.75 + random.nextDouble() * 0.55;
            case 4 -> 1.40 + random.nextDouble() * 2.20;
            case 5 -> 0.20 + random.nextDouble() * 0.95;
            case 6 -> 0.05 + random.nextDouble() * 0.25;
            default -> 0.01 + random.nextDouble() * 0.20;
        };

        return ATMOSPHERE_POOL[index] + " / " + String.format(Locale.ROOT, "%.2f bar", pressure);
    }

    private static String formatRadiation(Random random) {
        double radiation = random.nextDouble() * 180.0;
        return String.format(Locale.ROOT, "%.2f μSv/h", radiation);
    }

    private static String formatOrbit(Random random) {
        double eccentricity = random.nextDouble() * 0.72;
        double inclination = random.nextDouble() * 32.0;
        double drift = random.nextDouble() * 12.0;
        return "e=" + String.format(Locale.ROOT, "%.2f", eccentricity)
                + " / i=" + String.format(Locale.ROOT, "%.1f", inclination) + "°"
                + " / 漂移 " + String.format(Locale.ROOT, "%.1f", drift) + "%";
    }

    private static String formatSector(Random random) {
        String ring = pick(random, SECTOR_POOL);
        int x = random.nextInt(18) + 1;
        int y = random.nextInt(18) + 1;
        return ring + " / 星域 " + String.format(Locale.ROOT, "%02d-%02d", x, y);
    }
}
