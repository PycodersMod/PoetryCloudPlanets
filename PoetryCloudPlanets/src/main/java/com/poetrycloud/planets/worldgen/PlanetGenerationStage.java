package com.poetrycloud.planets.worldgen;

import net.minecraft.world.level.levelgen.GenerationStep;

public enum PlanetGenerationStage {
    RAW_GENERATION("rawGeneration", "Raw generation stage / 原始地形附加工序。", GenerationStep.Decoration.RAW_GENERATION),
    LAKES("lakes", "Lakes stage / 湖泊与大型液体坑阶段。", GenerationStep.Decoration.LAKES),
    LOCAL_MODIFICATIONS("localModifications", "Local modifications stage / 局部地形修饰阶段。", GenerationStep.Decoration.LOCAL_MODIFICATIONS),
    UNDERGROUND_STRUCTURES("undergroundStructures", "Underground structures stage / 地下类生成内容阶段。", GenerationStep.Decoration.UNDERGROUND_STRUCTURES),
    SURFACE_STRUCTURES("surfaceStructures", "Surface structures stage / 地表类生成内容阶段。", GenerationStep.Decoration.SURFACE_STRUCTURES),
    STRONGHOLDS("strongholds", "Strongholds stage / 要塞阶段。", GenerationStep.Decoration.STRONGHOLDS),
    UNDERGROUND_ORES("undergroundOres", "Underground ores stage / 地下矿物与晶洞阶段。", GenerationStep.Decoration.UNDERGROUND_ORES),
    UNDERGROUND_DECORATION("undergroundDecoration", "Underground decoration stage / 地下装饰阶段。", GenerationStep.Decoration.UNDERGROUND_DECORATION),
    FLUID_SPRINGS("fluidSprings", "Fluid springs stage / 泉眼与流体喷口阶段。", GenerationStep.Decoration.FLUID_SPRINGS),
    VEGETAL_DECORATION("vegetalDecoration", "Vegetal decoration stage / 植被阶段。", GenerationStep.Decoration.VEGETAL_DECORATION),
    TOP_LAYER_MODIFICATION("topLayerModification", "Top layer modification stage / 顶层修改阶段。", GenerationStep.Decoration.TOP_LAYER_MODIFICATION);

    private final String configKey;
    private final String comment;
    private final GenerationStep.Decoration decoration;

    PlanetGenerationStage(String configKey, String comment, GenerationStep.Decoration decoration) {
        this.configKey = configKey;
        this.comment = comment;
        this.decoration = decoration;
    }

    public String configKey() {
        return configKey;
    }

    public String comment() {
        return comment;
    }

    public GenerationStep.Decoration decoration() {
        return decoration;
    }

    public static PlanetGenerationStage fromDecoration(GenerationStep.Decoration decoration) {
        if (decoration == null) {
            return null;
        }

        for (PlanetGenerationStage stage : values()) {
            if (stage.decoration == decoration) {
                return stage;
            }
        }

        return null;
    }
}
