package com.pycoder.poetrycloudplanets.planet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PlanetRegistrySavedData extends SavedData {
    private static final String DATA_NAME = "poetrycloud_planets";
    private static final String TAG_PLANETS = "Planets";

    private final Map<String, PlanetRecord> planets = new LinkedHashMap<>();

    public PlanetRegistrySavedData() {
    }

    public static PlanetRegistrySavedData load(CompoundTag tag) {
        PlanetRegistrySavedData data = new PlanetRegistrySavedData();
        ListTag listTag = tag.getList(TAG_PLANETS, Tag.TAG_COMPOUND);
        for (Tag element : listTag) {
            if (element instanceof CompoundTag planetTag) {
                PlanetRecord record = PlanetRecord.fromTag(planetTag);
                data.planets.put(record.planetId(), record);
                if (!record.toTag().equals(planetTag)) {
                    data.setDirty();
                }
            }
        }

        return data;
    }

    public static PlanetRegistrySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlanetRegistrySavedData::load, PlanetRegistrySavedData::new, DATA_NAME);
    }

    public Collection<PlanetRecord> getAll() {
        return Collections.unmodifiableCollection(planets.values());
    }

    public int size() {
        return planets.size();
    }

    public boolean isEmpty() {
        return planets.isEmpty();
    }

    public List<PlanetRecord> getSpecialPlanets() {
        return collectByType(PlanetType.SPECIAL);
    }

    public List<PlanetRecord> getRandomPlanets() {
        return collectByType(PlanetType.RANDOM);
    }

    public List<PlanetRecord> getAllPlanetsOrdered() {
        return List.copyOf(planets.values());
    }

    public List<PlanetRecord> getGeneratedDimensionPlanets() {
        List<PlanetRecord> result = new ArrayList<>();
        result.addAll(getSpecialPlanets());
        for (PlanetRecord record : planets.values()) {
            if (record.isRandom() && record.discovered()) {
                result.add(record);
            }
        }

        return List.copyOf(result);
    }

    public List<PlanetRecord> getRegisteredDimensionPlanets() {
        List<PlanetRecord> result = new ArrayList<>();
        for (PlanetRecord record : planets.values()) {
            if (record.dimension().registered()) {
                result.add(record);
            }
        }

        return List.copyOf(result);
    }

    public List<PlanetRecord> getMaterializedDimensionPlanets() {
        List<PlanetRecord> result = new ArrayList<>();
        for (PlanetRecord record : planets.values()) {
            if (record.dimension().materialized()) {
                result.add(record);
            }
        }

        return List.copyOf(result);
    }

    public Map<String, PlanetRecord> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(planets));
    }

    public Optional<PlanetRecord> find(String planetId) {
        return Optional.ofNullable(planets.get(normalizePlanetId(planetId)));
    }

    public Optional<PlanetRecord> findByDimension(ResourceLocation dimensionId) {
        if (dimensionId == null) {
            return Optional.empty();
        }

        return planets.values().stream()
                .filter(record -> dimensionId.equals(record.dimension().dimensionId()))
                .findFirst();
    }

    public boolean contains(String planetId) {
        return planets.containsKey(normalizePlanetId(planetId));
    }

    public PlanetRecord put(PlanetRecord record) {
        String key = record.planetId();
        PlanetRecord previous = planets.put(key, record);
        if (!record.equals(previous)) {
            setDirty();
        }
        return record;
    }

    public PlanetRecord remove(String planetId) {
        PlanetRecord removed = planets.remove(normalizePlanetId(planetId));
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public PlanetRecord discover(String planetId) {
        String key = normalizePlanetId(planetId);
        PlanetRecord record = planets.get(key);
        if (record == null || record.discovered()) {
            return record;
        }

        PlanetRecord updated = record.withDiscovered(true);
        planets.put(key, updated);
        setDirty();
        return updated;
    }

    public PlanetRecord updateDimension(String planetId, PlanetDimensionRecord dimension) {
        String key = normalizePlanetId(planetId);
        PlanetRecord record = planets.get(key);
        if (record == null) {
            return null;
        }

        PlanetRecord updated = record.withDimension(dimension);
        planets.put(key, updated);
        setDirty();
        return updated;
    }

    public PlanetRecord upsert(PlanetRecord record) {
        return put(record);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (PlanetRecord record : planets.values()) {
            listTag.add(record.toTag());
        }
        tag.put(TAG_PLANETS, listTag);
        return tag;
    }

    private static String normalizePlanetId(String planetId) {
        if (planetId == null) {
            return "unnamed_planet";
        }

        String value = planetId.trim();
        return value.isEmpty() ? "unnamed_planet" : value;
    }

    private List<PlanetRecord> collectByType(PlanetType type) {
        List<PlanetRecord> result = new ArrayList<>();
        for (PlanetRecord record : planets.values()) {
            if (record.type() == type) {
                result.add(record);
            }
        }

        return List.copyOf(result);
    }
}
