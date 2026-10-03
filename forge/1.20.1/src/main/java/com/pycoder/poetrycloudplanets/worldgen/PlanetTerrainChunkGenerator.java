package com.pycoder.poetrycloudplanets.worldgen;

import com.pycoder.poetrycloudplanets.planet.PlanetRecord;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.GenerationStep;

import java.util.List;
import java.util.Objects;

public final class PlanetTerrainChunkGenerator extends NoiseBasedChunkGenerator {
    private final PlanetRecord record;
    private final long planetSeed;
    private final PlanetWorldgenProfile profile;
    private final List<FeatureSorter.StepFeatureData> featureSteps;

    public PlanetTerrainChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            PlanetRecord record,
            long planetSeed,
            PlanetWorldgenProfile profile
    ) {
        super(biomeSource, settings);
        this.record = record;
        this.planetSeed = planetSeed;
        this.profile = Objects.requireNonNullElseGet(profile, () -> new PlanetWorldgenProfile(
                java.util.List.of(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")),
                true,
                false,
                false,
                java.util.Set.<PlanetGenerationStage>of()
        ));
        this.featureSteps = FeatureSorter.buildFeaturesPerStep(
                List.copyOf(this.biomeSource.possibleBiomes()),
                holder -> this.getBiomeGenerationSettings(holder).features(),
                true
        );
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {
        if (profile.generateStructures()) {
            super.createStructures(registryAccess, structureState, structureManager, chunk, templateManager);
        }
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        if (profile.generateStructures()) {
            super.createReferences(level, structureManager, chunk);
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        if (profile.enabledStages().isEmpty()) {
            return;
        }

        if (profile.allStagesEnabled()) {
            super.applyBiomeDecoration(level, chunk, structureManager);
            return;
        }

        WorldgenRandom random = new WorldgenRandom(RandomSource.create(level.getSeed()));
        random.setDecorationSeed(level.getSeed(), chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());

        BlockPos origin = chunk.getPos().getWorldPosition();
        GenerationStep.Decoration[] decorations = GenerationStep.Decoration.values();
        for (int stepIndex = 0; stepIndex < featureSteps.size() && stepIndex < decorations.length; stepIndex++) {
            if (!profile.isStageEnabled(decorations[stepIndex])) {
                continue;
            }

            FeatureSorter.StepFeatureData stepData = featureSteps.get(stepIndex);
            for (Object featureObject : stepData.features()) {
                if (!(featureObject instanceof PlacedFeature placedFeature)) {
                    continue;
                }

                placedFeature.placeWithBiomeCheck(level, this, random, origin);
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        if (profile.spawnMobs()) {
            super.spawnOriginalMobs(region);
        }
    }
}
