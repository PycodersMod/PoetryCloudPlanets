package com.pycoder.poetrycloudplanets.dimension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import com.pycoder.poetrycloudplanets.config.PoetryCloudPlanetsConfig;
import com.pycoder.poetrycloudplanets.planet.PlanetRecord;
import com.pycoder.poetrycloudplanets.planet.SpecialPlanetArchive;
import com.pycoder.poetrycloudplanets.planet.SpecialPlanetTemplate;
import com.pycoder.poetrycloudplanets.worldgen.PlanetWorldgenProfile;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class SpecialPlanetDimensionPack {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PACK_NAME = "poetrycloud_special_planets";

    private SpecialPlanetDimensionPack() {
    }

    public static void sync(MinecraftServer server) {
        SpecialPlanetArchive archive = SpecialPlanetArchive.getInstance();
        archive.reload();

        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME);
        try {
            deleteRecursively(packRoot);
            List<SpecialPlanetTemplate> templates = archive.templates();
            if (templates.isEmpty()) {
                PoetryCloudPlanets.LOGGER.info("No special planet templates configured; skipped generated dimension pack");
                return;
            }

            Files.createDirectories(packRoot);
            writePackMeta(packRoot);

            ArrayList<String> dimensionIds = new ArrayList<>(templates.size());
            for (SpecialPlanetTemplate template : templates) {
                PlanetRecord record = archive.materialize(template);
                LevelStem stem = buildStem(server, record);
                writeDimensionFile(server.registryAccess(), packRoot, record.dimension().dimensionId(), stem);
                dimensionIds.add(record.dimension().dimensionId().toString());
            }

            PoetryCloudPlanets.LOGGER.info(
                    "Generated special planet datapack with {} dimensions: {}",
                    dimensionIds.size(),
                    String.join(", ", dimensionIds)
            );
        } catch (Exception exception) {
            PoetryCloudPlanets.LOGGER.error("Failed to generate special planet dimension pack", exception);
        }
    }

    private static LevelStem buildStem(MinecraftServer server, PlanetRecord record) {
        RegistryAccess access = server.registryAccess();
        PlanetWorldgenProfile profile = PoetryCloudPlanetsConfig.COMMON.specialPlanetWorldgenProfile();
        ResourceLocation referenceDimensionId = resolveReferenceDimensionId(access, record, profile);
        LevelStem referenceStem = resolveReferenceStem(access, referenceDimensionId);
        return new LevelStem(referenceStem.type(), referenceStem.generator());
    }

    private static ResourceLocation resolveReferenceDimensionId(RegistryAccess access, PlanetRecord record, PlanetWorldgenProfile profile) {
        ResourceLocation templateDimensionId = record.dimension().templateDimensionId();
        if (templateDimensionId != null && !PlanetDimensionTemplates.isInternalTemplate(templateDimensionId) && hasLevelStem(access, templateDimensionId)) {
            return templateDimensionId;
        }

        for (ResourceLocation candidate : profile.referenceDimensionIds()) {
            if (hasLevelStem(access, candidate)) {
                return candidate;
            }
        }

        return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    }

    private static boolean hasLevelStem(RegistryAccess access, ResourceLocation dimensionId) {
        return findLevelStem(access, dimensionId).isPresent();
    }

    private static LevelStem resolveReferenceStem(RegistryAccess access, ResourceLocation dimensionId) {
        return findLevelStem(access, dimensionId)
                .orElseGet(() -> findLevelStem(access, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
                        .orElseThrow(() -> new IllegalStateException("Missing overworld LevelStem")))
                .value();
    }

    private static Optional<Holder.Reference<LevelStem>> findLevelStem(RegistryAccess access, ResourceLocation dimensionId) {
        Registry<LevelStem> levelStems = access.registryOrThrow(Registries.LEVEL_STEM);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ResourceKey<LevelStem> levelStemKey = Registries.levelToLevelStem(levelKey);
        return levelStems.getHolder(levelStemKey);
    }

    private static void writePackMeta(Path packRoot) throws IOException {
        int packFormat = SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA);
        String packMeta = """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "Poetry Cloud Planets generated special dimension pack"
                  }
                }
                """.formatted(packFormat);
        Files.writeString(packRoot.resolve("pack.mcmeta"), packMeta, StandardCharsets.UTF_8);
    }

    private static void writeDimensionFile(RegistryAccess access, Path packRoot, ResourceLocation dimensionId, LevelStem stem) throws IOException {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        JsonElement json = LevelStem.CODEC.encodeStart(ops, stem)
                .getOrThrow(false, message -> PoetryCloudPlanets.LOGGER.error("Failed to encode level stem {}: {}", dimensionId, message));

        Path dimensionPath = packRoot
                .resolve("data")
                .resolve(dimensionId.getNamespace())
                .resolve("dimension")
                .resolve(dimensionId.getPath() + ".json");
        Files.createDirectories(dimensionPath.getParent());
        try (Writer writer = Files.newBufferedWriter(dimensionPath, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }
}
