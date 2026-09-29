package com.pycoder.poetrycloudplanets.planet;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

public record GalaxyCoordinates(double x, double y, double z) {
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";

    public static GalaxyCoordinates zero() {
        return new GalaxyCoordinates(0.0D, 0.0D, 0.0D);
    }

    public GalaxyCoordinates {
        if (!Double.isFinite(x)) {
            x = 0.0D;
        }
        if (!Double.isFinite(y)) {
            y = 0.0D;
        }
        if (!Double.isFinite(z)) {
            z = 0.0D;
        }
    }

    public double distanceTo(GalaxyCoordinates other) {
        if (other == null) {
            return distanceToOrigin();
        }

        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceToOrigin() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public String format(GalaxyDistanceUnit unit) {
        GalaxyDistanceUnit displayUnit = unit == null ? GalaxyDistanceUnit.AU : unit;
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f %s", x, y, z, displayUnit.symbol());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_X, x);
        tag.putDouble(TAG_Y, y);
        tag.putDouble(TAG_Z, z);
        return tag;
    }

    public static GalaxyCoordinates fromTag(CompoundTag tag) {
        if (tag == null) {
            return zero();
        }

        return new GalaxyCoordinates(tag.getDouble(TAG_X), tag.getDouble(TAG_Y), tag.getDouble(TAG_Z));
    }
}
