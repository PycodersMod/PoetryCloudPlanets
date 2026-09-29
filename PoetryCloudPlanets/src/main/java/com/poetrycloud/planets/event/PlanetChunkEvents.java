package com.poetrycloud.planets.event;

import com.poetrycloud.planets.PoetryCloudPlanets;
import com.poetrycloud.planets.dimension.PlanetDimensionLifecycle;
import com.poetrycloud.planets.planet.PlanetRecord;
import com.poetrycloud.planets.planet.PlanetRegistrySavedData;
import com.poetrycloud.planets.worldgen.PlanetTerrainChunkGenerator;
import com.poetrycloud.planets.worldgen.PlanetTerrainReplacer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoetryCloudPlanets.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanetChunkEvents {
    private PlanetChunkEvents() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        PlanetRegistrySavedData data = PlanetRegistrySavedData.get(level.getServer());
        PlanetRecord record = data.findByDimension(level.dimension().location()).orElse(null);
        if (record == null || !record.isRandom() || !record.discovered()) {
            return;
        }

        if (level.getChunkSource().getGenerator() instanceof PlanetTerrainChunkGenerator) {
            return;
        }

        long planetSeed = PlanetDimensionLifecycle.mixPlanetSeed(level.getServer().getWorldData().worldGenOptions().seed(), record);
        PlanetTerrainReplacer.replaceTerrain(chunk, record, planetSeed);
    }
}
