package com.poetrycloud.planets.event;

import com.poetrycloud.planets.PoetryCloudPlanets;
import com.poetrycloud.planets.planet.PlanetRecord;
import com.poetrycloud.planets.planet.PlanetRegistrySavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoetryCloudPlanets.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanetPortalEvents {
    private PlanetPortalEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlanetRegistrySavedData data = PlanetRegistrySavedData.get(player.getServer());
        PlanetRecord record = data.findByDimension(level.dimension().location())
                .filter(PlanetRecord::isRandom)
                .orElse(null);
        if (record == null) {
            return;
        }

        boolean obsidianPortal = level.getBlockState(event.getPos()).is(Blocks.OBSIDIAN)
                && (event.getItemStack().is(Items.FLINT_AND_STEEL) || event.getItemStack().is(Items.FIRE_CHARGE));
        boolean endPortal = level.getBlockState(event.getPos()).is(Blocks.END_PORTAL_FRAME)
                && event.getItemStack().is(Items.ENDER_EYE);
        if (!obsidianPortal && !endPortal) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("随机星球不支持原版传送门，请使用 /execute 访问维度。"));
    }
}
