package com.pycoder.poetrycloudplanets.planet;

import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import com.pycoder.poetrycloudplanets.config.PoetryCloudPlanetsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlanetCatalog {
    private static final SpecialPlanetArchive SPECIAL_ARCHIVE = SpecialPlanetArchive.getInstance();

    private PlanetCatalog() {
    }

    public static int ensureDefaults(PlanetRegistrySavedData data) {
        int changed = 0;
        for (PlanetRecord template : defaults()) {
            PlanetRecord existing = data.find(template.planetId()).orElse(null);
            if (existing == null) {
                data.put(template);
                changed++;
                continue;
            }

            if (template.isSpecial() && !existing.equals(template)) {
                data.put(template);
                changed++;
            }
        }

        return changed;
    }

    public static List<PlanetRecord> defaults() {
        ArrayList<PlanetRecord> result = new ArrayList<>(SPECIAL_ARCHIVE.materializeDefaults());
        result.addAll(randomDefaults(
                Math.max(0, PoetryCloudPlanetsConfig.COMMON.randomPlanetCount.get()),
                SPECIAL_ARCHIVE.templates(),
                SPECIAL_ARCHIVE.distanceUnit()
        ));
        return List.copyOf(result);
    }

    public static GalaxyDistanceUnit distanceUnit() {
        return SPECIAL_ARCHIVE.distanceUnit();
    }

    public static List<SpecialPlanetTemplate> specialTemplates() {
        return SPECIAL_ARCHIVE.templates();
    }

    private static List<PlanetRecord> randomDefaults(int count, List<SpecialPlanetTemplate> specialTemplates, GalaxyDistanceUnit unit) {
        ArrayList<PlanetRecord> result = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            String suffix = String.format(Locale.ROOT, "%02d", index);
            long planetSeed = 2024062410L + index;
            result.add(random(
                    "drift_" + suffix,
                    "漂移星 " + suffix,
                    "random/drift_" + suffix,
                    planetSeed,
                    randomSpawnPos(index),
                    GalaxyCoordinateGenerator.generate(planetSeed, specialTemplates, unit)
            ));
        }

        return List.copyOf(result);
    }

    private static PlanetRecord special(
            String planetId,
            String displayName,
            String dimensionPath,
            long seed,
            BlockPos spawnPos,
            GalaxyCoordinates coordinates
    ) {
        return PlanetRecord.special(planetId, displayName, dimension(dimensionPath), seed, spawnPos, coordinates)
                .withDiscovered(true);
    }

    private static PlanetRecord random(
            String planetId,
            String displayName,
            String dimensionPath,
            long seed,
            BlockPos spawnPos,
            GalaxyCoordinates coordinates
    ) {
        return PlanetRecord.random(planetId, displayName, dimension(dimensionPath), seed, spawnPos, coordinates);
    }

    private static ResourceLocation dimension(String path) {
        return ResourceLocation.fromNamespaceAndPath(PoetryCloudPlanets.MOD_ID, path);
    }

    private static BlockPos randomSpawnPos(int index) {
        int x = index * 32;
        int y = 68 + (index % 5) * 2;
        int z = (index % 2 == 0 ? -1 : 1) * index * 16;
        return new BlockPos(x, y, z);
    }
}
