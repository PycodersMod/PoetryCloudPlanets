package com.poetrycloud.planets.event;

import com.poetrycloud.planets.PoetryCloudPlanets;
import com.poetrycloud.planets.dimension.PlanetDimensionLifecycle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoetryCloudPlanets.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlanetClientLifecycleEvents {
    private PlanetClientLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PlanetDimensionLifecycle.cancelDiscoveryJobs();
    }
}
