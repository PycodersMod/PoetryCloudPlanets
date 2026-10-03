package com.pycoder.poetrycloudplanets.command;

import com.pycoder.poetrycloudplanets.PoetryCloudPlanets;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoetryCloudPlanets.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanetCommandEvents {
    private PlanetCommandEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PlanetCommands.register(event.getDispatcher());
    }
}
