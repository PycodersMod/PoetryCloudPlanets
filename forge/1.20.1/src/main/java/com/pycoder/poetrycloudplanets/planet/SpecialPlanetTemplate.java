package com.pycoder.poetrycloudplanets.planet;

import com.pycoder.poetrycloudplanets.dimension.PlanetDimensionTemplates;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record SpecialPlanetTemplate(
        String planetId,
        String displayName,
        ResourceLocation dimensionId,
        ResourceLocation templateDimensionId,
        GalaxyCoordinates coordinates,
        List<String> allowedPortalIds
) {
    public SpecialPlanetTemplate {
        planetId = normalizePlanetId(planetId);
        displayName = normalizeDisplayName(displayName, planetId);
        dimensionId = Objects.requireNonNullElse(dimensionId, ResourceLocation.fromNamespaceAndPath("poetrycloud", "unassigned"));
        templateDimensionId = PlanetDimensionTemplates.normalize(templateDimensionId);
        coordinates = coordinates == null ? GalaxyCoordinates.zero() : coordinates;
        allowedPortalIds = normalizePortalIds(allowedPortalIds);
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

    private static List<String> normalizePortalIds(List<String> portalIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (portalIds != null) {
            for (String portalId : portalIds) {
                if (portalId == null) {
                    continue;
                }

                String trimmed = portalId.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }

        return List.copyOf(new ArrayList<>(normalized));
    }
}
