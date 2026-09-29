package com.poetrycloud.planets.worldgen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;

import java.util.List;
import java.util.Set;

public record PlanetWorldgenProfile(
        List<ResourceLocation> referenceDimensionIds,
        boolean replaceMaterials,
        boolean generateStructures,
        boolean spawnMobs,
        Set<PlanetGenerationStage> enabledStages
) {
    public PlanetWorldgenProfile {
        referenceDimensionIds = normalizeReferenceDimensionIds(referenceDimensionIds);
        enabledStages = enabledStages == null ? Set.of() : Set.copyOf(enabledStages);
    }

    public ResourceLocation selectReferenceDimensionId(long seed) {
        if (referenceDimensionIds.isEmpty()) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        }

        int index = (int) Math.floorMod(seed, referenceDimensionIds.size());
        return referenceDimensionIds.get(index);
    }

    public boolean isStageEnabled(PlanetGenerationStage stage) {
        return stage != null && enabledStages.contains(stage);
    }

    public boolean isStageEnabled(GenerationStep.Decoration decoration) {
        if (decoration == null) {
            return false;
        }

        return isStageEnabled(PlanetGenerationStage.fromDecoration(decoration));
    }

    public boolean allStagesEnabled() {
        return enabledStages.size() == PlanetGenerationStage.values().length;
    }

    private static List<ResourceLocation> normalizeReferenceDimensionIds(List<ResourceLocation> values) {
        if (values == null || values.isEmpty()) {
            return List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
        }

        java.util.ArrayList<ResourceLocation> normalized = new java.util.ArrayList<>(values.size());
        for (ResourceLocation value : values) {
            if (value != null && !normalized.contains(value)) {
                normalized.add(value);
            }
        }

        if (normalized.isEmpty()) {
            normalized.add(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
        }

        return List.copyOf(normalized);
    }
}
