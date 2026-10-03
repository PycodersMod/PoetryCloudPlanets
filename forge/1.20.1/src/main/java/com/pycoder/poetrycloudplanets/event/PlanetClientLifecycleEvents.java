package com.pycoder.poetrycloudplanets.event;

import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import com.pycoder.poetrycloudplanets.dimension.PlanetDimensionLifecycle;
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
