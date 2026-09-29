package com.pycoder.poetrycloudplanets.planet;

import com.pycoder.poetrycloudplanets.material.BlockMaterialArchive;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record PlanetRecord(
        String planetId,
        String displayName,
        PlanetType type,
        long seed,
        boolean discovered,
        PlanetDimensionRecord dimension,
        BlockPos spawnPos,
        GalaxyCoordinates coordinates,
        PlanetObservationRecord observation,
        List<String> materials
) {
    private static final String TAG_PLANET_ID = "PlanetId";
    private static final String TAG_DISPLAY_NAME = "DisplayName";
    private static final String TAG_TYPE = "Type";
    private static final String TAG_SEED = "Seed";
    private static final String TAG_DISCOVERED = "Discovered";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_SPAWN = "Spawn";
    private static final String TAG_COORDINATES = "Coordinates";
    private static final String TAG_OBSERVATION = "Observation";
    private static final String TAG_MATERIALS = "Materials";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";

    public PlanetRecord {
        planetId = normalizePlanetId(planetId);
        displayName = normalizeDisplayName(displayName, planetId);
        type = type == null ? PlanetType.RANDOM : type;
        dimension = dimension == null ? PlanetDimensionRecord.unassigned() : dimension;
        spawnPos = Objects.requireNonNullElse(spawnPos, BlockPos.ZERO);
        coordinates = normalizeCoordinates(coordinates);
        observation = normalizeObservation(type, seed, observation);
        materials = normalizeMaterials(materials, seed);
    }

    public static PlanetRecord special(String planetId, String displayName, ResourceLocation dimensionId, long seed, BlockPos spawnPos, GalaxyCoordinates coordinates) {
        return special(planetId, displayName, dimensionId, null, seed, spawnPos, coordinates);
    }

    public static PlanetRecord special(String planetId, String displayName, ResourceLocation dimensionId, ResourceLocation templateDimensionId, long seed, BlockPos spawnPos, GalaxyCoordinates coordinates) {
        List<String> materials = resolveMaterialIds(seed);
        return new PlanetRecord(planetId, displayName, PlanetType.SPECIAL, seed, false, PlanetDimensionRecord.special(dimensionId, templateDimensionId), spawnPos, coordinates, PlanetObservationRecord.generatedFromMaterialSnapshot(materials, seed), materials);
    }

    public static PlanetRecord random(String planetId, String displayName, ResourceLocation dimensionId, long seed, BlockPos spawnPos, GalaxyCoordinates coordinates) {
        List<String> materials = resolveMaterialIds(seed);
        return new PlanetRecord(planetId, displayName, PlanetType.RANDOM, seed, false, PlanetDimensionRecord.random(dimensionId), spawnPos, coordinates, PlanetObservationRecord.generatedFromMaterialSnapshot(materials, seed), materials);
    }

    public static PlanetRecord external(String planetId, String displayName, ResourceLocation dimensionId, long seed, BlockPos spawnPos, GalaxyCoordinates coordinates) {
        List<String> materials = resolveMaterialIds(seed);
        return new PlanetRecord(planetId, displayName, PlanetType.EXTERNAL, seed, false, PlanetDimensionRecord.special(dimensionId), spawnPos, coordinates, PlanetObservationRecord.generatedFromMaterialSnapshot(materials, seed), materials);
    }

    public static PlanetRecord special(String planetId, String displayName, ResourceLocation dimensionId, long seed, BlockPos spawnPos) {
        return special(planetId, displayName, dimensionId, null, seed, spawnPos, GalaxyCoordinates.zero());
    }

    public static PlanetRecord special(String planetId, String displayName, ResourceLocation dimensionId, ResourceLocation templateDimensionId, long seed, BlockPos spawnPos) {
        return special(planetId, displayName, dimensionId, templateDimensionId, seed, spawnPos, GalaxyCoordinates.zero());
    }

    public static PlanetRecord random(String planetId, String displayName, ResourceLocation dimensionId, long seed, BlockPos spawnPos) {
        return random(planetId, displayName, dimensionId, seed, spawnPos, GalaxyCoordinates.zero());
    }

    public static PlanetRecord external(String planetId, String displayName, ResourceLocation dimensionId, long seed, BlockPos spawnPos) {
        return external(planetId, displayName, dimensionId, seed, spawnPos, GalaxyCoordinates.zero());
    }

    public PlanetRecord withDiscovered(boolean value) {
        return new PlanetRecord(planetId, displayName, type, seed, value, dimension, spawnPos, coordinates, observation, materials);
    }

    public PlanetRecord withDimension(PlanetDimensionRecord value) {
        return new PlanetRecord(planetId, displayName, type, seed, discovered, value, spawnPos, coordinates, observation, materials);
    }

    public PlanetRecord withSpawnPos(BlockPos value) {
        return new PlanetRecord(planetId, displayName, type, seed, discovered, dimension, value, coordinates, observation, materials);
    }

    public PlanetRecord withCoordinates(GalaxyCoordinates value) {
        return new PlanetRecord(planetId, displayName, type, seed, discovered, dimension, spawnPos, value, observation, materials);
    }

    public PlanetRecord withObservation(PlanetObservationRecord value) {
        return new PlanetRecord(planetId, displayName, type, seed, discovered, dimension, spawnPos, coordinates, value, materials);
    }

    public boolean isSpecial() {
        return type == PlanetType.SPECIAL;
    }

    public boolean isRandom() {
        return type == PlanetType.RANDOM;
    }

    public boolean isExternal() {
        return type == PlanetType.EXTERNAL;
    }

    public boolean isGeneratedDimensionListMember() {
        return isSpecial() || (isRandom() && discovered);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_PLANET_ID, planetId);
        tag.putString(TAG_DISPLAY_NAME, displayName);
        tag.putString(TAG_TYPE, type.serializedName());
        tag.putLong(TAG_SEED, seed);
        tag.putBoolean(TAG_DISCOVERED, discovered);
        tag.put(TAG_DIMENSION, dimension.toTag());
        tag.put(TAG_SPAWN, writeBlockPos(spawnPos));
        tag.put(TAG_COORDINATES, coordinates.toTag());
        tag.put(TAG_OBSERVATION, observation.toTag());
        tag.put(TAG_MATERIALS, writeStringList(materials));
        return tag;
    }

    public static PlanetRecord fromTag(CompoundTag tag) {
        String planetId = tag.getString(TAG_PLANET_ID);
        String displayName = tag.getString(TAG_DISPLAY_NAME);
        PlanetType type = PlanetType.fromSerializedName(tag.getString(TAG_TYPE));
        long seed = tag.getLong(TAG_SEED);
        boolean discovered = tag.getBoolean(TAG_DISCOVERED);

        PlanetDimensionRecord dimension = tag.contains(TAG_DIMENSION, Tag.TAG_COMPOUND)
                ? PlanetDimensionRecord.fromTag(tag.getCompound(TAG_DIMENSION))
                : PlanetDimensionRecord.unassigned();
        BlockPos spawnPos = tag.contains(TAG_SPAWN, Tag.TAG_COMPOUND)
                ? readBlockPos(tag.getCompound(TAG_SPAWN))
                : BlockPos.ZERO;
        GalaxyCoordinates coordinates = tag.contains(TAG_COORDINATES, Tag.TAG_COMPOUND)
                ? GalaxyCoordinates.fromTag(tag.getCompound(TAG_COORDINATES))
                : GalaxyCoordinates.zero();
        PlanetObservationRecord observation = tag.contains(TAG_OBSERVATION, Tag.TAG_COMPOUND)
                ? PlanetObservationRecord.fromTag(tag.getCompound(TAG_OBSERVATION))
                : PlanetObservationRecord.placeholder();
        List<String> materials = readStringList(tag);
        if (observation.isPlaceholder()) {
            observation = PlanetObservationRecord.generatedFromMaterialSnapshot(materials, seed);
        }

        return new PlanetRecord(planetId, displayName, type, seed, discovered, dimension, spawnPos, coordinates, observation, materials);
    }

    private static String normalizePlanetId(String planetId) {
        if (planetId == null) {
            return "unnamed_planet";
        }

        String value = planetId.trim();
        return value.isEmpty() ? "unnamed_planet" : value;
    }

    private static String normalizeDisplayName(String displayName, String planetId) {
        if (displayName == null) {
            return normalizePlanetId(planetId);
        }

        String value = displayName.trim();
        return value.isEmpty() ? normalizePlanetId(planetId) : value;
    }

    private static PlanetObservationRecord normalizeObservation(PlanetType type, long seed, PlanetObservationRecord observation) {
        if (observation != null && !observation.isPlaceholder()) {
            return observation;
        }

        if (type == PlanetType.RANDOM) {
            return PlanetObservationRecord.generated(seed);
        }

        return observation == null ? PlanetObservationRecord.placeholder() : observation;
    }

    private static GalaxyCoordinates normalizeCoordinates(GalaxyCoordinates coordinates) {
        if (coordinates == null) {
            return GalaxyCoordinates.zero();
        }

        if (!Double.isFinite(coordinates.x()) || !Double.isFinite(coordinates.y()) || !Double.isFinite(coordinates.z())) {
            return GalaxyCoordinates.zero();
        }

        return coordinates;
    }

    private static List<String> normalizeMaterials(List<String> source, long seed) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (source != null) {
            for (String value : source) {
                if (value == null) {
                    continue;
                }

                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.addAll(resolveMaterialIds(seed));
        }

        if (normalized.isEmpty()) {
            normalized.add("minecraft:stone");
        }

        return List.copyOf(normalized);
    }

    private static List<String> resolveMaterialIds(long seed) {
        ArrayList<String> result = new ArrayList<>();
        for (var profile : BlockMaterialArchive.getInstance().sampleForPlanet(seed)) {
            result.add(profile.blockId().toString());
        }

        if (result.isEmpty()) {
            result.add("minecraft:stone");
        }

        return List.copyOf(result);
    }

    private static ListTag writeStringList(List<String> values) {
        ListTag listTag = new ListTag();
        if (values == null) {
            return listTag;
        }

        for (String value : values) {
            if (value == null) {
                continue;
            }

            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                listTag.add(StringTag.valueOf(trimmed));
            }
        }

        return listTag;
    }

    private static List<String> readStringList(CompoundTag tag) {
        if (!tag.contains(TAG_MATERIALS, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag listTag = tag.getList(TAG_MATERIALS, Tag.TAG_STRING);
        ArrayList<String> values = new ArrayList<>(listTag.size());
        for (int index = 0; index < listTag.size(); index++) {
            String trimmed = listTag.getString(index).trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }

        return List.copyOf(values);
    }

    private static CompoundTag writeBlockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_X, pos.getX());
        tag.putInt(TAG_Y, pos.getY());
        tag.putInt(TAG_Z, pos.getZ());
        return tag;
    }

    private static BlockPos readBlockPos(CompoundTag tag) {
        return new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
    }
}
