package com.poetrycloud.planets;

import com.poetrycloud.planets.config.PoetryCloudPlanetsConfig;
import com.poetrycloud.planets.material.BlockMaterialArchive;
import com.poetrycloud.planets.planet.SpecialPlanetArchive;
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
