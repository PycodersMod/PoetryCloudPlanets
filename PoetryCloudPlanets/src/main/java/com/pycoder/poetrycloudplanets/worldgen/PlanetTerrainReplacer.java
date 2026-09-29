package com.pycoder.poetrycloudplanets.worldgen;

import com.pycoder.poetrycloudplanets.planet.PlanetRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlanetTerrainReplacer {
    private static final ConcurrentMap<String, List<BlockState>> PALETTE_CACHE = new ConcurrentHashMap<>();

    private PlanetTerrainReplacer() {
    }

    public static void replaceTerrain(ChunkAccess chunk, PlanetRecord record, long planetSeed) {
        List<BlockState> palette = paletteFor(record);
        if (palette.isEmpty()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            int sectionMinY = minY + sectionIndex * 16;
            int sectionMaxY = Math.min(sectionMinY + 16, maxY);
            for (int y = sectionMinY; y < sectionMaxY; y++) {
                int localY = y - sectionMinY;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState current = section.getBlockState(x, localY, z);
                        if (current.isAir() || current.is(Blocks.BEDROCK)) {
                            continue;
                        }

                        cursor.set(minX + x, y, minZ + z);
                        BlockState replacement = pickReplacement(palette, planetSeed, cursor);
                        if (!replacement.equals(current)) {
                            chunk.setBlockState(cursor, replacement, false);
                        }
                    }
                }
            }
        }
    }

    public static List<BlockState> paletteFor(PlanetRecord record) {
        if (record == null) {
            return List.of();
        }

        return PALETTE_CACHE.computeIfAbsent(record.planetId(), key -> {
            ArrayList<BlockState> palette = new ArrayList<>();
            for (String rawId : record.materials()) {
                BlockState state = resolveBlockState(rawId);
                if (state != null) {
                    palette.add(state);
                }
            }

            if (palette.isEmpty()) {
                palette.add(Blocks.STONE.defaultBlockState());
            }

            return List.copyOf(palette);
        });
    }

    private static BlockState resolveBlockState(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }

        var blockId = net.minecraft.resources.ResourceLocation.tryParse(rawId.trim());
        if (blockId == null) {
            return null;
        }

        return BuiltInRegistries.BLOCK.getOptional(blockId)
                .map(block -> block.defaultBlockState())
                .orElse(Blocks.STONE.defaultBlockState());
    }

    private static BlockState pickReplacement(List<BlockState> palette, long planetSeed, BlockPos pos) {
        long value = planetSeed;
        value ^= ((long) pos.getX() * 341873128712L);
        value ^= ((long) pos.getY() * 132897987541L);
        value ^= ((long) pos.getZ() * 42317861L);
        value = Long.rotateLeft(value, 21) ^ (value >>> 29);
        int index = Math.floorMod((int) (value ^ (value >>> 32)), palette.size());
        return palette.get(index);
    }
}
