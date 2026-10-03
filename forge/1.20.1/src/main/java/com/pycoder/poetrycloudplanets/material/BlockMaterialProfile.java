package com.pycoder.poetrycloudplanets.material;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record BlockMaterialProfile(
        ResourceLocation blockId,
        String category,
        double density,
        int lightLevel,
        int color,
        double reflectance,
        double radioactivity,
        Map<String, Double> elements
) {
    public BlockMaterialProfile {
        blockId = Objects.requireNonNullElse(blockId, ResourceLocation.fromNamespaceAndPath("minecraft", "stone"));
        category = normalizeText(category, "generic");
        density = clamp(density, 0.0D, 100.0D);
        lightLevel = clamp(lightLevel, 0, 15);
        color = clamp(color, 0, 0xFFFFFF);
        reflectance = clamp(reflectance, 0.0D, 1.0D);
        radioactivity = clamp(radioactivity, 0.0D, 100.0D);
        elements = normalizeElements(elements);
    }

    private static String normalizeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static Map<String, Double> normalizeElements(Map<String, Double> source) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, Double> entry : source.entrySet()) {
                String key = normalizeText(entry.getKey(), "");
                Double value = entry.getValue();
                if (key.isEmpty() || value == null || value.doubleValue() <= 0.0D) {
                    continue;
                }
                normalized.put(key, value.doubleValue());
            }
        }

        if (normalized.isEmpty()) {
            normalized.put("硅(Si)", 0.5D);
            normalized.put("氧(O)", 0.5D);
        }

        double total = 0.0D;
        for (double value : normalized.values()) {
            total += value;
        }
        if (total <= 0.0D) {
            normalized.clear();
            normalized.put("硅(Si)", 0.5D);
            normalized.put("氧(O)", 0.5D);
            total = 1.0D;
        }

        Map<String, Double> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : normalized.entrySet()) {
            scaled.put(entry.getKey(), entry.getValue() / total);
        }

        return Collections.unmodifiableMap(scaled);
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
}
