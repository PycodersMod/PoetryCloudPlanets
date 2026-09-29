package com.poetrycloud.planets.event;

import com.poetrycloud.planets.PoetryCloudPlanets;
import com.poetrycloud.planets.debug.SaveDebugWatchdog;
import com.poetrycloud.planets.dimension.PlanetDimensionLifecycle;
import com.poetrycloud.planets.dimension.SpecialPlanetDimensionPack;
import com.poetrycloud.planets.planet.PlanetCatalog;
import com.poetrycloud.planets.planet.PlanetRegistrySavedData;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoetryCloudPlanets.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanetLifecycleEvents {
    private PlanetLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        SpecialPlanetDimensionPack.sync(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PlanetRegistrySavedData data = PlanetRegistrySavedData.get(event.getServer());
        int added = PlanetCatalog.ensureDefaults(data);
        PlanetDimensionLifecycle.DimensionLifecycleReport lifecycleReport = PlanetDimensionLifecycle.bootstrapManagedDimensions(event.getServer(), data);
        if (added > 0) {
            PoetryCloudPlanets.LOGGER.info("Bootstrapped {} default planet records", added);
        }
        if (lifecycleReport.synchronizedPlanets() > 0) {
            PoetryCloudPlanets.LOGGER.info(
                    "Dimension lifecycle synchronized {} planet records (registered={}, materialized={}, persistent={})",
                    lifecycleReport.synchronizedPlanets(),
                    lifecycleReport.registeredPlanets(),
                    lifecycleReport.materializedPlanets(),
                    lifecycleReport.persistentPlanets()
            );
        } else {
            PoetryCloudPlanets.LOGGER.info(
                    "Dimension lifecycle ready (registered={}, materialized={}, persistent={})",
                    lifecycleReport.registeredPlanets(),
                    lifecycleReport.materializedPlanets(),
                    lifecycleReport.persistentPlanets()
            );
        }
        PoetryCloudPlanets.LOGGER.info("Planet registry ready with {} records", data.size());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SaveDebugWatchdog.markSaveStart("server-stopping", event.getServer());
        SaveDebugWatchdog.start(event.getServer(), "server-stopping");
        PlanetDimensionLifecycle.cancelDiscoveryJobs();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SaveDebugWatchdog.stop("server-stopped");
    }
}
