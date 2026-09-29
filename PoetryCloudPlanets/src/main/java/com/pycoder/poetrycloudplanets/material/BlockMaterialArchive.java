package com.pycoder.poetrycloudplanets.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import com.pycoder.poetrycloudplanets.config.PoetryCloudPlanetsConfig;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Locale;
import java.awt.Color;

public final class BlockMaterialArchive {
    private static final String BASE_RESOURCE_PATH = "/config/poetrycloud_block_materials.json";
    private static final String EXTRA_FILE_NAME = "poetrycloud_extra_block_materials.json";
    private static final BlockMaterialArchive INSTANCE = new BlockMaterialArchive();

    private volatile List<BlockMaterialProfile> profiles = List.of();
    private volatile Map<ResourceLocation, BlockMaterialProfile> profileIndex = Map.of();
    private final Map<ResourceLocation, BlockMaterialProfile> generatedProfiles = new LinkedHashMap<>();
    private volatile boolean loaded;

    private BlockMaterialArchive() {
        reload();
    }

    public static BlockMaterialArchive getInstance() {
        return INSTANCE;
    }

    public synchronized void reload() {
        try {
            ensureExtraFile();
            List<BlockMaterialProfile> loadedProfiles = loadProfiles();
            profiles = List.copyOf(loadedProfiles);
            LinkedHashMap<ResourceLocation, BlockMaterialProfile> index = new LinkedHashMap<>();
            for (BlockMaterialProfile profile : loadedProfiles) {
                index.put(profile.blockId(), profile);
            }
            profileIndex = Collections.unmodifiableMap(index);
            loaded = true;
        } catch (IOException ex) {
            PoetryCloudPlanets.LOGGER.error("Failed to load block material archive", ex);
            profiles = List.of();
            profileIndex = Map.of();
            loaded = false;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public synchronized List<BlockMaterialProfile> all() {
        LinkedHashMap<ResourceLocation, BlockMaterialProfile> merged = new LinkedHashMap<>();
        for (BlockMaterialProfile profile : profiles) {
            merged.put(profile.blockId(), profile);
        }
        for (BlockMaterialProfile profile : generatedProfiles.values()) {
            merged.putIfAbsent(profile.blockId(), profile);
        }
        return List.copyOf(merged.values());
    }

    public synchronized Optional<BlockMaterialProfile> find(ResourceLocation blockId) {
        if (blockId == null) {
            return Optional.empty();
        }

        BlockMaterialProfile profile = profileIndex.get(blockId);
        if (profile != null) {
            return Optional.of(profile);
        }

        return Optional.ofNullable(generatedProfiles.get(blockId));
    }

    public synchronized BlockMaterialProfile resolve(ResourceLocation blockId) {
        if (blockId == null) {
            return syntheticProfile(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"));
        }

        BlockMaterialProfile existing = profileIndex.get(blockId);
        if (existing != null) {
            return existing;
        }

        BlockMaterialProfile generated = generatedProfiles.get(blockId);
        if (generated != null) {
            return generated;
        }

        BlockMaterialProfile created = syntheticProfile(blockId);
        generatedProfiles.put(blockId, created);
        return created;
    }

    public synchronized List<BlockMaterialProfile> configuredCandidates() {
        LinkedHashMap<ResourceLocation, BlockMaterialProfile> result = new LinkedHashMap<>();

        if (PoetryCloudPlanetsConfig.COMMON.autoImportDefaultMaterials.get()) {
            for (BlockMaterialProfile profile : profiles) {
                result.put(profile.blockId(), profile);
            }
        }

        for (String rawId : PoetryCloudPlanetsConfig.COMMON.materialWhitelist.get()) {
            parseLocation(rawId).map(this::resolve).ifPresent(profile -> result.putIfAbsent(profile.blockId(), profile));
        }

        for (String rawId : PoetryCloudPlanetsConfig.COMMON.materialBlacklist.get()) {
            parseLocation(rawId).ifPresent(result::remove);
        }

        if (result.isEmpty() && !profiles.isEmpty()) {
            PoetryCloudPlanets.LOGGER.warn("Material archive filters removed all candidates; falling back to the full archive.");
            return List.copyOf(profiles);
        }

        return List.copyOf(result.values());
    }

    public synchronized List<BlockMaterialProfile> sampleForPlanet(long seed) {
        List<BlockMaterialProfile> candidates = configuredCandidates();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Random random = new Random(mixSeed(seed));
        int min = PoetryCloudPlanetsConfig.COMMON.randomMaterialCountMin();
        int max = PoetryCloudPlanetsConfig.COMMON.randomMaterialCountMax();
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        int requested = low + random.nextInt(high - low + 1);

        ArrayList<BlockMaterialProfile> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        int actual = Math.min(requested, shuffled.size());
        return List.copyOf(shuffled.subList(0, actual));
    }

    private BlockMaterialProfile syntheticProfile(ResourceLocation blockId) {
        Random random = new Random(seedFrom(blockId));
        String path = blockId.getPath().toLowerCase(Locale.ROOT);
        String category = inferCategory(path);
        double density = inferDensity(category, path, random);
        int lightLevel = inferLightLevel(category, path, random);
        int color = inferColor(category, path, random);
        double reflectance = inferReflectance(category, color, random);
        double radioactivity = inferRadioactivity(category, path, random);
        Map<String, Double> elements = inferElements(category, path, random);
        return new BlockMaterialProfile(blockId, category, density, lightLevel, color, reflectance, radioactivity, elements);
    }

    private List<BlockMaterialProfile> loadProfiles() throws IOException {
        LinkedHashMap<ResourceLocation, BlockMaterialProfile> merged = new LinkedHashMap<>();
        mergeProfiles(merged, loadBundledProfiles());
        mergeProfiles(merged, loadExtraProfiles());
        return new ArrayList<>(merged.values());
    }

    private List<BlockMaterialProfile> loadBundledProfiles() throws IOException {
        try (InputStream input = BlockMaterialArchive.class.getResourceAsStream(BASE_RESOURCE_PATH)) {
            if (input == null) {
                PoetryCloudPlanets.LOGGER.warn("Bundled block material archive resource not found: {}", BASE_RESOURCE_PATH);
                return List.of();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return readProfiles(reader, BASE_RESOURCE_PATH);
            }
        }
    }

    private List<BlockMaterialProfile> loadExtraProfiles() throws IOException {
        Path path = extraConfigPath();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readProfiles(reader, path.toString());
        }
    }

    private List<BlockMaterialProfile> readProfiles(BufferedReader reader, String sourceName) throws IOException {
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (!rootElement.isJsonObject()) {
            throw new IOException("Material archive root must be a JSON object: " + sourceName);
        }

        JsonObject root = rootElement.getAsJsonObject();
        JsonArray materials = root.has("materials") && root.get("materials").isJsonArray()
                ? root.getAsJsonArray("materials")
                : new JsonArray();

        ArrayList<BlockMaterialProfile> loadedProfiles = new ArrayList<>();
        for (JsonElement element : materials) {
            if (!element.isJsonObject()) {
                continue;
            }

            parseProfile(element.getAsJsonObject()).ifPresent(loadedProfiles::add);
        }

        return loadedProfiles;
    }

    private static void mergeProfiles(Map<ResourceLocation, BlockMaterialProfile> target, List<BlockMaterialProfile> source) {
        for (BlockMaterialProfile profile : source) {
            target.put(profile.blockId(), profile);
        }
    }

    private Optional<BlockMaterialProfile> parseProfile(JsonObject object) {
        ResourceLocation blockId = parseLocation(getString(object, "blockId")).orElse(null);
        if (blockId == null) {
            PoetryCloudPlanets.LOGGER.warn("Skip invalid material profile without blockId: {}", object);
            return Optional.empty();
        }

        String category = getString(object, "category", "generic");
        double density = getDouble(object, "density", 2.5D);
        int lightLevel = getInt(object, "lightLevel", 0);
        int color = parseColor(getString(object, "colorHex", null), getInt(object, "color", 0x7F7F7F));
        double reflectance = getDouble(object, "reflectance", 0.25D);
        double radioactivity = getDouble(object, "radioactivity", 0.0D);
        Map<String, Double> elements = parseElements(object);
        return Optional.of(new BlockMaterialProfile(blockId, category, density, lightLevel, color, reflectance, radioactivity, elements));
    }

    private static String inferCategory(String path) {
        if (containsAny(path, "glass", "crystal", "amethyst", "ice", "packed_ice", "blue_ice")) {
            return "silicate";
        }
        if (containsAny(path, "wood", "log", "plank", "stem", "bamboo", "hyphae", "mangrove", "wooden")) {
            return "organic";
        }
        if (containsAny(path, "sand", "gravel", "dirt", "clay", "mud", "terracotta", "brick", "soil")) {
            return "sediment";
        }
        if (containsAny(path, "obsidian", "netherrack", "basalt", "deepslate", "tuff", "stone", "stonebrick", "sculk")) {
            return "rock";
        }
        if (containsAny(path, "gold", "iron", "copper", "emerald", "diamond", "lapis", "quartz", "netherite", "metal")) {
            return "metal";
        }
        if (containsAny(path, "magma", "glow", "lantern", "torch", "beacon", "light", "shroomlight", "sea_lantern")) {
            return "emissive";
        }
        if (containsAny(path, "snow", "powder_snow", "frost", "frozen")) {
            return "cryogenic";
        }
        if (containsAny(path, "prismarine", "end_stone", "chorus", "purpur", "amethyst", "gem")) {
            return "exotic";
        }
        return "generic";
    }

    private static double inferDensity(String category, String path, Random random) {
        double base = switch (category) {
            case "metal" -> 7.5D;
            case "rock" -> 2.7D;
            case "sediment" -> 1.9D;
            case "organic" -> 0.65D;
            case "silicate" -> 2.45D;
            case "cryogenic" -> 0.95D;
            case "emissive" -> 2.2D;
            case "exotic" -> 2.9D;
            default -> 2.1D;
        };
        double variance = switch (category) {
            case "metal" -> 3.5D;
            case "rock" -> 0.9D;
            case "sediment" -> 0.7D;
            case "organic" -> 0.35D;
            case "silicate" -> 0.8D;
            case "cryogenic" -> 0.4D;
            case "emissive" -> 0.6D;
            case "exotic" -> 1.1D;
            default -> 0.9D;
        };
        if (containsAny(path, "gold")) {
            base = 19.0D;
            variance = 0.6D;
        } else if (containsAny(path, "diamond")) {
            base = 3.5D;
            variance = 0.25D;
        } else if (containsAny(path, "obsidian")) {
            base = 2.95D;
            variance = 0.12D;
        } else if (containsAny(path, "ice", "snow")) {
            base = 0.92D;
            variance = 0.20D;
        }
        return clamp(base + (random.nextDouble() - 0.5D) * variance, 0.05D, 25.0D);
    }

    private static int inferLightLevel(String category, String path, Random random) {
        int base = switch (category) {
            case "emissive" -> 10;
            case "cryogenic" -> 0;
            case "metal" -> 0;
            case "silicate" -> 0;
            case "exotic" -> 1;
            default -> 0;
        };
        if (containsAny(path, "glow", "lantern", "torch", "beacon", "shroomlight", "sea_lantern", "magma")) {
            base = 12;
        }
        return clamp(base + random.nextInt(3), 0, 15);
    }

    private static int inferColor(String category, String path, Random random) {
        float hue = switch (category) {
            case "metal" -> 0.08F + random.nextFloat() * 0.08F;
            case "rock" -> 0.04F + random.nextFloat() * 0.08F;
            case "sediment" -> 0.08F + random.nextFloat() * 0.10F;
            case "organic" -> 0.23F + random.nextFloat() * 0.18F;
            case "silicate" -> 0.45F + random.nextFloat() * 0.18F;
            case "cryogenic" -> 0.52F + random.nextFloat() * 0.08F;
            case "emissive" -> 0.10F + random.nextFloat() * 0.45F;
            case "exotic" -> 0.55F + random.nextFloat() * 0.30F;
            default -> random.nextFloat();
        };
        if (containsAny(path, "gold")) {
            hue = 0.12F;
        } else if (containsAny(path, "copper")) {
            hue = 0.06F;
        } else if (containsAny(path, "diamond", "emerald")) {
            hue = 0.46F;
        } else if (containsAny(path, "redstone")) {
            hue = 0.00F;
        } else if (containsAny(path, "lapis")) {
            hue = 0.60F;
        }

        float saturation = switch (category) {
            case "metal" -> 0.30F + random.nextFloat() * 0.25F;
            case "rock" -> 0.12F + random.nextFloat() * 0.18F;
            case "sediment" -> 0.18F + random.nextFloat() * 0.18F;
            case "organic" -> 0.30F + random.nextFloat() * 0.30F;
            case "silicate" -> 0.18F + random.nextFloat() * 0.20F;
            case "cryogenic" -> 0.10F + random.nextFloat() * 0.18F;
            case "emissive" -> 0.60F + random.nextFloat() * 0.35F;
            case "exotic" -> 0.45F + random.nextFloat() * 0.40F;
            default -> 0.22F + random.nextFloat() * 0.30F;
        };
        float brightness = switch (category) {
            case "metal" -> 0.40F + random.nextFloat() * 0.30F;
            case "rock" -> 0.25F + random.nextFloat() * 0.35F;
            case "sediment" -> 0.48F + random.nextFloat() * 0.28F;
            case "organic" -> 0.30F + random.nextFloat() * 0.30F;
            case "silicate" -> 0.55F + random.nextFloat() * 0.25F;
            case "cryogenic" -> 0.65F + random.nextFloat() * 0.20F;
            case "emissive" -> 0.70F + random.nextFloat() * 0.25F;
            case "exotic" -> 0.58F + random.nextFloat() * 0.30F;
            default -> 0.35F + random.nextFloat() * 0.35F;
        };
        if (containsAny(path, "gold", "diamond", "emerald", "quartz")) {
            brightness = Math.max(brightness, 0.72F);
        }
        return Color.HSBtoRGB(hue, clamp01(saturation), clamp01(brightness)) & 0xFFFFFF;
    }

    private static double inferReflectance(String category, int color, Random random) {
        double brightness = ((color >>> 16) & 0xFF) * 0.299D + ((color >>> 8) & 0xFF) * 0.587D + (color & 0xFF) * 0.114D;
        double normalizedBrightness = brightness / 255.0D;
        double base = switch (category) {
            case "metal" -> 0.38D;
            case "rock" -> 0.18D;
            case "sediment" -> 0.26D;
            case "organic" -> 0.14D;
            case "silicate" -> 0.52D;
            case "cryogenic" -> 0.68D;
            case "emissive" -> 0.84D;
            case "exotic" -> 0.58D;
            default -> 0.24D;
        };
        return clamp(base * 0.65D + normalizedBrightness * 0.25D + random.nextDouble() * 0.10D, 0.0D, 1.0D);
    }

    private static double inferRadioactivity(String category, String path, Random random) {
        double base = switch (category) {
            case "metal" -> 0.02D;
            case "rock" -> 0.03D;
            case "sediment" -> 0.00D;
            case "organic" -> 0.00D;
            case "silicate" -> 0.00D;
            case "cryogenic" -> 0.00D;
            case "emissive" -> 0.06D;
            case "exotic" -> 0.02D;
            default -> 0.01D;
        };
        if (containsAny(path, "uranium", "reactor", "nuclear", "sculk", "magma", "nether", "ancient", "radio")) {
            base += 0.18D;
        }
        if (containsAny(path, "deepslate", "obsidian", "basalt", "netherrack")) {
            base += 0.05D;
        }
        return clamp(base + random.nextDouble() * 0.06D, 0.0D, 1.0D);
    }

    private static Map<String, Double> inferElements(String category, String path, Random random) {
        String[] pool = switch (category) {
            case "metal" -> METAL_ELEMENTS;
            case "rock" -> ROCK_ELEMENTS;
            case "sediment" -> SEDIMENT_ELEMENTS;
            case "organic" -> ORGANIC_ELEMENTS;
            case "silicate" -> SILICATE_ELEMENTS;
            case "cryogenic" -> CRYOGENIC_ELEMENTS;
            case "emissive" -> EMISSIVE_ELEMENTS;
            case "exotic" -> EXOTIC_ELEMENTS;
            default -> GENERIC_ELEMENTS;
        };

        ArrayList<String> selected = new ArrayList<>(List.of(pool));
        Collections.shuffle(selected, random);
        int count = Math.min(4, selected.size());

        double w1 = 2.0D + random.nextDouble() * 5.0D;
        double w2 = 1.5D + random.nextDouble() * 3.0D;
        double w3 = 0.8D + random.nextDouble() * 2.0D;
        double w4 = 0.4D + random.nextDouble() * 1.4D;
        double total = w1 + w2 + w3 + w4;

        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        if (count > 0) {
            result.put(selected.get(0), w1 / total);
        }
        if (count > 1) {
            result.put(selected.get(1), w2 / total);
        }
        if (count > 2) {
            result.put(selected.get(2), w3 / total);
        }
        if (count > 3) {
            result.put(selected.get(3), w4 / total);
        }
        return result;
    }

    private Map<String, Double> parseElements(JsonObject object) {
        LinkedHashMap<String, Double> elements = new LinkedHashMap<>();
        if (!object.has("elements") || !object.get("elements").isJsonObject()) {
            return elements;
        }

        JsonObject rawElements = object.getAsJsonObject("elements");
        for (Map.Entry<String, JsonElement> entry : rawElements.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                continue;
            }

            double value = entry.getValue().getAsDouble();
            if (value <= 0.0D) {
                continue;
            }

            elements.put(entry.getKey(), value);
        }

        return elements;
    }

    private static Optional<ResourceLocation> parseLocation(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }

        ResourceLocation location = ResourceLocation.tryParse(rawId.trim());
        return Optional.ofNullable(location);
    }

    private static Path extraConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(EXTRA_FILE_NAME);
    }

    private void ensureExtraFile() throws IOException {
        Path path = extraConfigPath();
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(defaultExtraTemplateJson());
        }
    }

    private static String defaultExtraTemplateJson() {
        return """
                {
                  "version": 1,
                  "exampleMaterial": {
                    "blockId": "minecraft:mud",
                    "category": "sediment",
                    "density": 1.72,
                    "lightLevel": 0,
                    "colorHex": "4a3b33",
                    "reflectance": 0.09,
                    "radioactivity": 0.0,
                    "elements": {
                      "氢(H)": 0.08,
                      "氧(O)": 0.16,
                      "硅(Si)": 0.28,
                      "铝(Al)": 0.16,
                      "铁(Fe)": 0.16,
                      "碳(C)": 0.16
                    }
                  },
                  "materials": []
                }
                """;
    }

    private static String getString(JsonObject object, String key) {
        return getString(object, key, null);
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

    private static int getInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        JsonElement element = object.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
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

    private static int parseColor(String rawHex, int fallback) {
        if (rawHex == null || rawHex.isBlank()) {
            return fallback;
        }

        String value = rawHex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }

        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

    private static long seedFrom(ResourceLocation blockId) {
        long seed = 0x6A09E667F3BCC909L;
        String value = blockId.toString();
        for (int i = 0; i < value.length(); i++) {
            seed ^= value.charAt(i);
            seed *= 0x9E3779B97F4A7C15L;
            seed ^= (seed >>> 32);
        }
        return mixSeed(seed);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
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

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static final String[] ROCK_ELEMENTS = {"硅(Si)", "氧(O)", "铝(Al)", "铁(Fe)", "镁(Mg)", "钙(Ca)"};
    private static final String[] METAL_ELEMENTS = {"铁(Fe)", "镍(Ni)", "铜(Cu)", "金(Au)", "银(Ag)", "铬(Cr)", "钛(Ti)"};
    private static final String[] SEDIMENT_ELEMENTS = {"硅(Si)", "氧(O)", "钙(Ca)", "铁(Fe)", "铝(Al)", "钾(K)"};
    private static final String[] ORGANIC_ELEMENTS = {"碳(C)", "氢(H)", "氧(O)", "氮(N)", "磷(P)", "硫(S)"};
    private static final String[] SILICATE_ELEMENTS = {"硅(Si)", "氧(O)", "铝(Al)", "镁(Mg)", "钠(Na)", "钙(Ca)"};
    private static final String[] CRYOGENIC_ELEMENTS = {"氢(H)", "氧(O)", "氮(N)", "碳(C)", "氩(Ar)"};
    private static final String[] EMISSIVE_ELEMENTS = {"硅(Si)", "氧(O)", "铁(Fe)", "镁(Mg)", "硫(S)", "钙(Ca)"};
    private static final String[] EXOTIC_ELEMENTS = {"碳(C)", "硅(Si)", "氧(O)", "铍(Be)", "铬(Cr)", "铝(Al)"};
    private static final String[] GENERIC_ELEMENTS = {"硅(Si)", "氧(O)", "铁(Fe)", "碳(C)", "镁(Mg)", "铝(Al)"};
}
