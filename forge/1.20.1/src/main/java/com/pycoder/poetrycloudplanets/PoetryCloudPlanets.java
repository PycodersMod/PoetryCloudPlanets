package com.pycoder.poetrycloudplanets;

import com.pycoder.poetrycloudplanets.config.PoetryCloudPlanetsConfig;
import com.pycoder.poetrycloudplanets.material.BlockMaterialArchive;
import com.pycoder.poetrycloudplanets.planet.SpecialPlanetArchive;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PoetryCloudPlanets.MOD_ID)
public class PoetryCloudPlanets {
    public static final String MOD_ID = "poetrycloud";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PoetryCloudPlanets() {
        PoetryCloudPlanetsConfig.register();
        BlockMaterialArchive.getInstance();
        SpecialPlanetArchive.getInstance();
        LOGGER.info("{} loaded", MOD_ID);
    }
}
