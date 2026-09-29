package com.pycoder.poetrycloudplanets.planet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import com.pycoder.poetrycloudplanets.dimension.PlanetDimensionTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SpecialPlanetArchive {
    private static final String BASE_RESOURCE_PATH = "/config/poetrycloud_special_planets.json";
    private static final String FILE_NAME = "poetrycloud_special_planets.json";
    private static final SpecialPlanetArchive INSTANCE = new SpecialPlanetArchive();

    private volatile GalaxyDistanceUnit distanceUnit = GalaxyDistanceUnit.AU;
    private volatile List<SpecialPlanetTemplate> templates = List.of();
    private volatile boolean loaded;

    private SpecialPlanetArchive() {
        reload();
    }

    public static SpecialPlanetArchive getInstance() {
        return INSTANCE;
    }

    public synchronized void reload() {
        try {
            ensureConfigFile();
            PlanetConfig loadedConfig = loadConfig();
            distanceUnit = loadedConfig.distanceUnit();
            templates = List.copyOf(loadedConfig.templates());
            loaded = true;
        } catch (IOException exception) {
            PoetryCloudPlanets.LOGGER.error("Failed to load special planet archive", exception);
            distanceUnit = GalaxyDistanceUnit.AU;
            templates = List.of();
            loaded = false;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public GalaxyDistanceUnit distanceUnit() {
        return distanceUnit;
    }

    public List<SpecialPlanetTemplate> templates() {
        return templates;
    }

    public List<String> allowedPortalIds(String planetId) {
        if (planetId == null || planetId.isBlank()) {
            return List.of();
        }

        for (SpecialPlanetTemplate template : templates) {
            if (planetId.trim().equals(template.planetId())) {
                return template.allowedPortalIds();
            }
        }

        return List.of();
    }

    public synchronized List<PlanetRecord> materializeDefaults() {
        ArrayList<PlanetRecord> result = new ArrayList<>(templates.size());
        for (SpecialPlanetTemplate template : templates) {
            result.add(materialize(template));
        }
        return List.copyOf(result);
    }

    public synchronized PlanetRecord materialize(SpecialPlanetTemplate template) {
        if (template == null) {
            return PlanetRecord.special(
                    "unnamed_planet",
                    "unnamed_planet",
                    ResourceLocation.fromNamespaceAndPath("poetrycloud", "unassigned"),
                    PlanetDimensionTemplates.BLANK_SPECIAL_PLANET,
                    0L,
                    BlockPos.ZERO,
                    GalaxyCoordinates.zero()
            ).withDiscovered(true);
        }

        long seed = seedFor(template);
        BlockPos spawnPos = deriveSpawnPos(template, seed);
        return PlanetRecord.special(
                template.planetId(),
                template.displayName(),
                template.dimensionId(),
                template.templateDimensionId(),
                seed,
                spawnPos,
                template.coordinates()
        ).withDiscovered(true);
    }

    private PlanetConfig loadConfig() throws IOException {
        Path path = configPath();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                throw new IOException("Special planet archive root must be a JSON object: " + path);
            }

            JsonObject root = rootElement.getAsJsonObject();
            GalaxyDistanceUnit unit = GalaxyDistanceUnit.parse(getString(root, "distanceUnit", "AU"));
            JsonArray planets = root.has("planets") && root.get("planets").isJsonArray()
                    ? root.getAsJsonArray("planets")
                    : new JsonArray();

            ArrayList<SpecialPlanetTemplate> templates = new ArrayList<>();
            for (JsonElement element : planets) {
                if (!element.isJsonObject()) {
                    continue;
                }

                parseTemplate(element.getAsJsonObject()).ifPresent(templates::add);
            }

            return new PlanetConfig(unit, templates);
        }
    }

    private java.util.Optional<SpecialPlanetTemplate> parseTemplate(JsonObject object) {
        ResourceLocation planetId = parseLocation(getString(object, "planetId", null)).orElse(null);
        if (planetId == null) {
            PoetryCloudPlanets.LOGGER.warn("Skip invalid special planet without planetId: {}", object);
            return java.util.Optional.empty();
        }

        String displayName = getString(object, "displayName", planetId.getPath());
        ResourceLocation dimensionId = parseLocation(getString(object, "dimensionId", null)).orElse(null);
        if (dimensionId == null) {
            PoetryCloudPlanets.LOGGER.warn("Skip invalid special planet without dimensionId: {}", object);
            return java.util.Optional.empty();
        }
        ResourceLocation templateDimensionId = parseLocation(getString(object, "templateDimensionId", null))
                .orElse(PlanetDimensionTemplates.BLANK_SPECIAL_PLANET);

        JsonObject coordinatesObject = object.has("coordinates") && object.get("coordinates").isJsonObject()
                ? object.getAsJsonObject("coordinates")
                : object;
        double x = getDouble(coordinatesObject, "x", 0.0D);
        double y = getDouble(coordinatesObject, "y", 0.0D);
        double z = getDouble(coordinatesObject, "z", 0.0D);
        List<String> allowedPortalIds = readStringList(object, "allowedPortalIds");

        return java.util.Optional.of(new SpecialPlanetTemplate(
                planetId.toString(),
                displayName,
                dimensionId,
                templateDimensionId,
                new GalaxyCoordinates(x, y, z),
                allowedPortalIds
        ));
    }

    private void ensureConfigFile() throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            return;
        }

        try (InputStream input = SpecialPlanetArchive.class.getResourceAsStream(BASE_RESOURCE_PATH)) {
            if (input != null) {
                Files.copy(input, path);
                return;
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(defaultTemplateJson());
        }
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static java.util.Optional<ResourceLocation> parseLocation(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.ofNullable(ResourceLocation.tryParse(rawId.trim()));
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        JsonElement element = object.get(key);
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }

        return fallback;
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        JsonElement element = object.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        }

        return fallback;
    }

    private static List<String> readStringList(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return List.of();
        }

        JsonArray array = object.getAsJsonArray(key);
        ArrayList<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) {
                continue;
            }

            String value = element.getAsString().trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }

        return List.copyOf(values);
    }

    private static long seedFor(SpecialPlanetTemplate template) {
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= template.planetId().hashCode();
        seed = Long.rotateLeft(seed, 11);
        seed ^= template.displayName().hashCode();
        seed = Long.rotateLeft(seed, 17);
        seed ^= template.dimensionId().hashCode();
        seed = Long.rotateLeft(seed, 7);
        seed ^= template.templateDimensionId().hashCode();
        seed = Long.rotateLeft(seed, 23);
        seed ^= Double.doubleToLongBits(template.coordinates().x());
        seed = Long.rotateLeft(seed, 13);
        seed ^= Double.doubleToLongBits(template.coordinates().y());
        seed = Long.rotateLeft(seed, 19);
        seed ^= Double.doubleToLongBits(template.coordinates().z());
        seed ^= 0xD1B54A32D192ED03L;
        return seed;
    }

    private static BlockPos deriveSpawnPos(SpecialPlanetTemplate template, long seed) {
        int x = (int) Math.floorMod(seed, 256L) - 128;
        int y = 72 + (int) Math.floorMod(seed >>> 8, 24L);
        int z = (int) Math.floorMod(seed >>> 16, 256L) - 128;
        return new BlockPos(x, y, z);
    }

    private static String defaultTemplateJson() {
        return """
                {
                  "_comment": "distanceUnit 用来统一解释下方所有坐标，坐标和相对距离都会按这里的单位读写；dimensionId 是这颗星球最终生成出的维度 ID；templateDimensionId 是蓝本来源，可以填本 mod 的内置模板 ID，也可以直接填其他 mod 的维度 ID。内置模板 ID：poetrycloud:dimension_template/random_planet 与 poetrycloud:dimension_template/blank_special_planet。",
                  "version": 1,
                  "distanceUnit": "AU",
                  "planets": []
                }
                """;
    }

    private record PlanetConfig(GalaxyDistanceUnit distanceUnit, List<SpecialPlanetTemplate> templates) {
    }
}
