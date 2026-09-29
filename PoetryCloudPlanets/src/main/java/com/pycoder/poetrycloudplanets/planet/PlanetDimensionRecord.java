package com.pycoder.poetrycloudplanets.planet;

import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import com.pycoder.poetrycloudplanets.dimension.PlanetDimensionTemplates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PlanetDimensionRecord(
        ResourceLocation dimensionId,
        ResourceLocation templateDimensionId,
        boolean registered,
        boolean materialized,
        boolean persistent
) {
    private static final String TAG_DIMENSION_ID = "DimensionId";
    private static final String TAG_TEMPLATE_DIMENSION_ID = "TemplateDimensionId";
    private static final String TAG_REGISTERED = "Registered";
    private static final String TAG_MATERIALIZED = "Materialized";
    private static final String TAG_PERSISTENT = "Persistent";

    public PlanetDimensionRecord {
        dimensionId = Objects.requireNonNullElse(dimensionId, ResourceLocation.fromNamespaceAndPath(PoetryCloudPlanets.MOD_ID, "unassigned"));
        templateDimensionId = PlanetDimensionTemplates.normalize(templateDimensionId);
    }

    public static PlanetDimensionRecord unassigned() {
        return new PlanetDimensionRecord(
                ResourceLocation.fromNamespaceAndPath(PoetryCloudPlanets.MOD_ID, "unassigned"),
                PlanetDimensionTemplates.BLANK_SPECIAL_PLANET,
                false,
                false,
                false
        );
    }

    public static PlanetDimensionRecord special(ResourceLocation dimensionId) {
        return special(dimensionId, PlanetDimensionTemplates.BLANK_SPECIAL_PLANET);
    }

    public static PlanetDimensionRecord special(ResourceLocation dimensionId, ResourceLocation templateDimensionId) {
        return new PlanetDimensionRecord(dimensionId, templateDimensionId, true, true, true);
    }

    public static PlanetDimensionRecord random(ResourceLocation dimensionId) {
        return new PlanetDimensionRecord(dimensionId, PlanetDimensionTemplates.RANDOM_PLANET, false, false, true);
    }

    public PlanetDimensionRecord withTemplateDimensionId(ResourceLocation value) {
        return new PlanetDimensionRecord(dimensionId, value, registered, materialized, persistent);
    }

    public PlanetDimensionRecord withRegistered(boolean value) {
        return new PlanetDimensionRecord(dimensionId, templateDimensionId, value, materialized, persistent);
    }

    public PlanetDimensionRecord withMaterialized(boolean value) {
        return new PlanetDimensionRecord(dimensionId, templateDimensionId, registered, value, persistent);
    }

    public PlanetDimensionRecord withPersistent(boolean value) {
        return new PlanetDimensionRecord(dimensionId, templateDimensionId, registered, materialized, value);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION_ID, dimensionId.toString());
        tag.putString(TAG_TEMPLATE_DIMENSION_ID, templateDimensionId.toString());
        tag.putBoolean(TAG_REGISTERED, registered);
        tag.putBoolean(TAG_MATERIALIZED, materialized);
        tag.putBoolean(TAG_PERSISTENT, persistent);
        return tag;
    }

    public static PlanetDimensionRecord fromTag(CompoundTag tag) {
        ResourceLocation dimensionId = parseLocation(tag.getString(TAG_DIMENSION_ID), ResourceLocation.fromNamespaceAndPath(PoetryCloudPlanets.MOD_ID, "unassigned"));
        ResourceLocation templateDimensionId = parseLocation(tag.getString(TAG_TEMPLATE_DIMENSION_ID), PlanetDimensionTemplates.BLANK_SPECIAL_PLANET);

        return new PlanetDimensionRecord(
                dimensionId,
                templateDimensionId,
                tag.getBoolean(TAG_REGISTERED),
                tag.getBoolean(TAG_MATERIALIZED),
                tag.getBoolean(TAG_PERSISTENT)
        );
    }

    private static ResourceLocation parseLocation(String rawId, ResourceLocation fallback) {
        if (rawId == null || rawId.isBlank()) {
            return fallback;
        }

        ResourceLocation parsed = ResourceLocation.tryParse(rawId);
        return parsed == null ? fallback : parsed;
    }
}
