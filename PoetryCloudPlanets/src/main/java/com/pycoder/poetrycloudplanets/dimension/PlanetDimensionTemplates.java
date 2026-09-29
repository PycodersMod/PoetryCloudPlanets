package com.pycoder.poetrycloudplanets.dimension;

import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import net.minecraft.resources.ResourceLocation;

public final class PlanetDimensionTemplates {
    public static final ResourceLocation RANDOM_PLANET = ResourceLocation.fromNamespaceAndPath(PoetryCloudPlanets.MOD_ID, "dimension_template/random_planet");
    public static final ResourceLocation BLANK_SPECIAL_PLANET = ResourceLocation.fromNamespaceAndPath(PoetryCloudPlanets.MOD_ID, "dimension_template/blank_special_planet");

    private PlanetDimensionTemplates() {
    }

    public static ResourceLocation normalize(ResourceLocation templateId) {
        return templateId == null ? BLANK_SPECIAL_PLANET : templateId;
    }

    public static boolean isInternalTemplate(ResourceLocation templateId) {
        return RANDOM_PLANET.equals(templateId) || BLANK_SPECIAL_PLANET.equals(templateId);
    }
}
