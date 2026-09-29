package com.poetrycloud.planets.config;

import com.poetrycloud.planets.worldgen.PlanetGenerationStage;
import com.poetrycloud.planets.worldgen.PlanetWorldgenProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PoetryCloudPlanetsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        SPEC = builder.build();
    }

    private PoetryCloudPlanetsConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "PoetryCloudPlanets.toml");
    }

    public static final class Common {
        public final ForgeConfigSpec.IntValue randomPlanetCount;
        public final ForgeConfigSpec.BooleanValue autoImportDefaultMaterials;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> randomMaterialCountRange;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> randomPlanetDistanceScaleRange;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> materialWhitelist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> materialBlacklist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> observationVariables;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> brightnessRange;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> spectrumColorRange;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> gravityRange;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> radiationRange;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> atmosphereOptions;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> sectorOptions;
        public final WorldgenProfileConfig randomPlanetWorldgen;
        public final WorldgenProfileConfig specialPlanetWorldgen;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.comment("World and planet generation / 世界与星球生成").push("world");
            randomPlanetCount = builder
                    .comment("Random planet count; controls how many random planet templates are injected into a new save by default. / 随机星球数量，决定默认向新存档注入多少个随机星球模板。")
                    .defineInRange("randomPlanetCount", 5, 0, 1000000);
            builder.pop();

            builder.comment("Random material pool / 随机材料池").push("materials");
            autoImportDefaultMaterials = builder
                    .comment("Import the built-in natural material archive automatically, including stone, sediment, ores, ice and snow, crystals, Nether or End strata, water, lava, and similar blocks. / 是否自动导入内置自然材料档案，包括岩石、沉积物、矿石、冰雪、晶体、下界或末地地层、水、岩浆等。")
                    .define("autoImportDefaultMaterials", true);
            randomMaterialCountRange = builder
                    .comment("Random material count range per random planet, formatted as [min, max]. / 每个随机星球抽取的材料种类范围，格式为 [min, max]。")
                    .defineList("randomMaterialCountRange", List.of(4, 7), entry -> entry instanceof Number value && value.intValue() > 0);
            randomPlanetDistanceScaleRange = builder
                    .comment("Distance shell scale range k for random planets, formatted as [min, max]. The final random distance range is the special-planet distance range multiplied by this scale. / 随机星球球壳比例 k 的范围，格式为 [min, max]。最终随机距离范围会按特殊星球距离范围乘以这个比例缩放。")
                    .defineList("randomPlanetDistanceScaleRange", List.of(0.5D, 2.0D), entry -> entry instanceof Number value && value.doubleValue() > 0.0D);
            materialWhitelist = builder
                    .comment("Whitelist of block IDs that are forced into the random material pool. If a block is missing from poetrycloud_extra_block_materials.json, add it there first; otherwise the mod will generate a fallback profile from the block ID. / 手动加入随机材料池的方块 ID 白名单；如果某个方块资料不在 poetrycloud_extra_block_materials.json 里，可以先补到那里，不补则会按 block id 自动生成一份资料。")
                    .defineList("materialWhitelist", List.of("minecraft:stone"), entry -> entry instanceof String value && !value.isBlank());
            materialBlacklist = builder
                    .comment("Blacklist of block IDs removed from the random material pool. / 从随机材料池中剔除的方块 ID 黑名单。")
                    .defineList("materialBlacklist", List.of(), entry -> entry instanceof String value && !value.isBlank());
            builder.pop();

            builder.comment("Observation output / 观星结果").push("observation");
            observationVariables = builder
                    .comment("Observation variable keys that are currently generated and displayed. / 当前参与生成和展示的观星变量名。")
                    .defineList(
                            "observationVariables",
                            List.of(
                                    "brightness",
                                    "spectrum",
                                    "composition",
                                    "gravity",
                                    "atmosphere",
                                    "radiation",
                                    "orbitalAnomaly",
                                    "sector"
                            ),
                            entry -> entry instanceof String value && !value.isBlank()
                    );
            brightnessRange = builder
                    .comment("Apparent magnitude range, formatted as [min, max]. Smaller values are brighter. / 视星等范围，格式为 [min, max]。数值越小表示越亮。")
                    .defineList("brightnessRange", List.of(-2.40D, 12.80D), entry -> entry instanceof Number);
            spectrumColorRange = builder
                    .comment("Spectrum RGB range, formatted as [minHex, maxHex], for example [\"000000\", \"ffffff\"]. / 光谱 RGB 范围，格式为 [minHex, maxHex]，例如 [\"000000\", \"ffffff\"]。")
                    .defineList("spectrumColorRange", List.of("000000", "ffffff"), entry -> entry instanceof String value && !value.isBlank());
            gravityRange = builder
                    .comment("Gravity range in g, formatted as [min, max]. / 重力系数范围，格式为 [min, max]，单位 g。")
                    .defineList("gravityRange", List.of(0.08D, 4.25D), entry -> entry instanceof Number);
            radiationRange = builder
                    .comment("Radiation range in μSv/h, formatted as [min, max]. / 辐射范围，格式为 [min, max]，单位 μSv/h。")
                    .defineList("radiationRange", List.of(0.0D, 180.0D), entry -> entry instanceof Number);
            atmosphereOptions = builder
                    .comment("Candidate atmosphere descriptions. / 大气描述候选项。")
                    .defineList(
                            "atmosphereOptions",
                            List.of(
                                    "真空",
                                    "极稀薄氮氧层",
                                    "稀薄氮氧层",
                                    "标准氮氧层",
                                    "高压二氧化碳层",
                                    "甲烷雾层",
                                    "尘埃悬浮层",
                                    "离子薄层"
                            ),
                            entry -> entry instanceof String value && !value.isBlank()
                    );
            sectorOptions = builder
                    .comment("Candidate sector descriptions. / 星域描述候选项。")
                    .defineList(
                            "sectorOptions",
                            List.of("内环", "中环", "外环", "边缘带", "断裂层", "核心带"),
                            entry -> entry instanceof String value && !value.isBlank()
                    );
            builder.pop();

            builder.comment("Dimension generation profiles / 维度生成模板配置").push("dimensionGeneration");
            randomPlanetWorldgen = new WorldgenProfileConfig(
                    builder,
                    "randomPlanet",
                    "Random planet profile / 随机星球维度模板",
                    List.of("minecraft:overworld"),
                    true,
                    false,
                    false,
                    false
            );
            specialPlanetWorldgen = new WorldgenProfileConfig(
                    builder,
                    "specialPlanet",
                    "Blank special planet profile / 空白特殊星球维度模板",
                    List.of("minecraft:overworld"),
                    false,
                    true,
                    true,
                    true
            );
            builder.pop();
        }

        public PlanetWorldgenProfile randomPlanetWorldgenProfile() {
            return randomPlanetWorldgen.snapshot();
        }

        public PlanetWorldgenProfile specialPlanetWorldgenProfile() {
            return specialPlanetWorldgen.snapshot();
        }

        public int randomMaterialCountMin() {
            return readInt(randomMaterialCountRange.get(), 0, 4);
        }

        public int randomMaterialCountMax() {
            int min = randomMaterialCountMin();
            return readInt(randomMaterialCountRange.get(), 1, min);
        }

        public double brightnessMinMag() {
            return readDouble(brightnessRange.get(), 0, -2.40D);
        }

        public double brightnessMaxMag() {
            double min = brightnessMinMag();
            return readDouble(brightnessRange.get(), 1, min);
        }

        public double randomPlanetDistanceScaleMin() {
            return readDouble(randomPlanetDistanceScaleRange.get(), 0, 0.5D);
        }

        public double randomPlanetDistanceScaleMax() {
            double min = randomPlanetDistanceScaleMin();
            return readDouble(randomPlanetDistanceScaleRange.get(), 1, min);
        }

        public String spectrumColorMin() {
            return readString(spectrumColorRange.get(), 0, "000000");
        }

        public String spectrumColorMax() {
            return readString(spectrumColorRange.get(), 1, spectrumColorMin());
        }

        public double gravityMin() {
            return readDouble(gravityRange.get(), 0, 0.08D);
        }

        public double gravityMax() {
            double min = gravityMin();
            return readDouble(gravityRange.get(), 1, min);
        }

        public double radiationMin() {
            return readDouble(radiationRange.get(), 0, 0.0D);
        }

        public double radiationMax() {
            double min = radiationMin();
            return readDouble(radiationRange.get(), 1, min);
        }

        private static int readInt(List<?> values, int index, int fallback) {
            Object value = readValue(values, index, fallback);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return fallback;
        }

        private static double readDouble(List<?> values, int index, double fallback) {
            Object value = readValue(values, index, fallback);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text) {
                try {
                    return Double.parseDouble(text.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return fallback;
        }

        private static String readString(List<?> values, int index, String fallback) {
            Object value = readValue(values, index, fallback);
            if (value == null) {
                return fallback;
            }
            String text = value.toString().trim();
            return text.isEmpty() ? fallback : text;
        }

        private static Object readValue(List<?> values, int index, Object fallback) {
            if (values == null || index < 0 || index >= values.size()) {
                return fallback;
            }
            Object value = values.get(index);
            return value == null ? fallback : value;
        }
    }

    public static final class WorldgenProfileConfig {
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> referenceDimensionIds;
        private final ForgeConfigSpec.BooleanValue replaceMaterials;
        private final ForgeConfigSpec.BooleanValue generateStructures;
        private final ForgeConfigSpec.BooleanValue spawnMobs;
        private final Map<PlanetGenerationStage, ForgeConfigSpec.BooleanValue> stageToggles;

        private WorldgenProfileConfig(
                ForgeConfigSpec.Builder builder,
                String sectionName,
                String sectionComment,
                List<String> defaultReferenceDimensions,
                boolean defaultReplaceMaterials,
                boolean defaultGenerateStructures,
                boolean defaultSpawnMobs,
                boolean defaultStagesEnabled
        ) {
            builder.comment(sectionComment).push(sectionName);
            referenceDimensionIds = builder
                    .comment("Reference dimension ID list used to borrow biome source and noise settings. Invalid entries are ignored; if the list becomes empty, the Overworld is used. If multiple valid dimensions remain, each planet stably picks one from the list. / 参考维度 ID 列表，用来借用生物群系源和噪声设置；非法项会被忽略，若最终为空则回退到主世界；若存在多个有效维度，则每个星球会稳定地从列表中选一个。")
                    .defineList("referenceDimension", defaultReferenceDimensions, entry -> entry instanceof String value && !value.isBlank());
            replaceMaterials = builder
                    .comment("Replace natural terrain blocks with the selected random material pool. / 是否用选中的随机材料池替换自然地形方块。")
                    .define("replaceMaterials", defaultReplaceMaterials);
            generateStructures = builder
                    .comment("Generate structures in this managed dimension. / 是否在这个托管维度中生成结构。")
                    .define("generateStructures", defaultGenerateStructures);
            spawnMobs = builder
                    .comment("Allow vanilla mob spawning in this managed dimension. / 是否允许这个托管维度刷出原版生物。")
                    .define("spawnMobs", defaultSpawnMobs);

            builder.comment("Shared decoration stages / 通用装饰生成阶段").push("generateStages");
            LinkedHashMap<PlanetGenerationStage, ForgeConfigSpec.BooleanValue> toggles = new LinkedHashMap<>();
            for (PlanetGenerationStage stage : PlanetGenerationStage.values()) {
                toggles.put(
                        stage,
                        builder.comment(stage.comment()).define(stage.configKey(), defaultStagesEnabled)
                );
            }
            builder.pop();
            builder.pop();
            stageToggles = Map.copyOf(toggles);
        }

        public PlanetWorldgenProfile snapshot() {
            List<ResourceLocation> referenceDimensions = parseLocations(referenceDimensionIds.get());
            EnumSet<PlanetGenerationStage> enabled = EnumSet.noneOf(PlanetGenerationStage.class);
            for (Map.Entry<PlanetGenerationStage, ForgeConfigSpec.BooleanValue> entry : stageToggles.entrySet()) {
                if (entry.getValue().get()) {
                    enabled.add(entry.getKey());
                }
            }

            return new PlanetWorldgenProfile(
                    referenceDimensions,
                    replaceMaterials.get(),
                    generateStructures.get(),
                    spawnMobs.get(),
                    enabled
            );
        }

        private static List<ResourceLocation> parseLocations(List<? extends String> rawIds) {
            java.util.ArrayList<ResourceLocation> parsed = new java.util.ArrayList<>();
            if (rawIds != null) {
                for (String rawId : rawIds) {
                    if (rawId == null || rawId.isBlank()) {
                        continue;
                    }

                    ResourceLocation location = ResourceLocation.tryParse(rawId.trim());
                    if (location != null && !parsed.contains(location)) {
                        parsed.add(location);
                    }
                }
            }

            if (parsed.isEmpty()) {
                parsed.add(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
            }

            return List.copyOf(parsed);
        }
    }
}
