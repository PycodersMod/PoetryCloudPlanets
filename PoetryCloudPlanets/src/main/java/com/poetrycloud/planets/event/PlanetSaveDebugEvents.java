package com.poetrycloud.planets.event;

import com.poetrycloud.planets.PoetryCloudPlanets;
import com.poetrycloud.planets.debug.SaveDebugWatchdog;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoetryCloudPlanets.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanetSaveDebugEvents {
    private PlanetSaveDebugEvents() {
    }

    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SaveDebugWatchdog.logLevelSave(level);
        }
    }
}
