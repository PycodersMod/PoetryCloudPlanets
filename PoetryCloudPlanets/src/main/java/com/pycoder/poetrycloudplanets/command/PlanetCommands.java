package com.pycoder.poetrycloudplanets.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pycoder.poetrycloudplanets.dimension.PlanetDimensionLifecycle;
import com.pycoder.poetrycloudplanets.planet.GalaxyCoordinates;
import com.pycoder.poetrycloudplanets.planet.GalaxyDistanceUnit;
import com.pycoder.poetrycloudplanets.planet.PlanetAstrometry;
import com.pycoder.poetrycloudplanets.planet.PlanetRecord;
import com.pycoder.poetrycloudplanets.planet.PlanetRegistrySavedData;
import com.pycoder.poetrycloudplanets.planet.PlanetType;
import com.pycoder.poetrycloudplanets.planet.SpecialPlanetArchive;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class PlanetCommands {
    private static final String ROOT = "pcp";

    private PlanetCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ROOT)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .then(Commands.literal("special").executes(context -> listSpecial(context.getSource())))
                        .then(Commands.literal("random").executes(context -> listRandom(context.getSource())))
                        .then(Commands.literal("all").executes(context -> listAll(context.getSource())))
                        .then(Commands.literal("dimension").executes(context -> listDimensions(context.getSource()))))
                .then(Commands.literal("info")
                        .then(Commands.argument("planetid", StringArgumentType.word())
                                .suggests(PlanetCommands::suggestAllPlanetIds)
                                .executes(context -> info(context.getSource(), StringArgumentType.getString(context, "planetid")))))
                .then(Commands.literal("discover")
                        .then(Commands.argument("planetid", StringArgumentType.word())
                                .suggests(PlanetCommands::suggestUndiscoveredRandomPlanetIds)
                                .executes(context -> discover(context.getSource(), StringArgumentType.getString(context, "planetid")))))
                .then(Commands.literal("here").executes(context -> here(context.getSource()))));
    }

    private static int listSpecial(CommandSourceStack source) {
        PlanetRegistrySavedData data = registry(source);
        source.sendSuccess(() -> Component.literal(formatPlanetList("特殊星球列表", data.getSpecialPlanets())), false);
        return 1;
    }

    private static int listRandom(CommandSourceStack source) {
        PlanetRegistrySavedData data = registry(source);
        source.sendSuccess(() -> Component.literal(formatPlanetList("随机星球列表", data.getRandomPlanets())), false);
        return 1;
    }

    private static int listAll(CommandSourceStack source) {
        PlanetRegistrySavedData data = registry(source);
        StringBuilder builder = new StringBuilder();
        builder.append(formatPlanetList("特殊星球列表", data.getSpecialPlanets()));
        builder.append("\n\n");
        builder.append(formatPlanetList("随机星球列表", data.getRandomPlanets()));
        source.sendSuccess(() -> Component.literal(builder.toString()), false);
        return 1;
    }

    private static int listDimensions(CommandSourceStack source) {
        PlanetRegistrySavedData data = registry(source);
        source.sendSuccess(() -> Component.literal(formatDimensionList("已生成维度列表", data.getRegisteredDimensionPlanets())), false);
        return 1;
    }

    private static int info(CommandSourceStack source, String planetId) {
        PlanetRegistrySavedData data = registry(source);
        Optional<PlanetRecord> maybeRecord = data.find(planetId);
        if (maybeRecord.isEmpty()) {
            source.sendFailure(Component.literal("星球不存在：" + planetId));
            return 0;
        }

        PlanetDimensionLifecycle.CurrentDimensionContext context = PlanetDimensionLifecycle.classifyCurrentDimension(source, data);
        sendPlanetInfo(source, maybeRecord.get(), observerCoordinates(context));
        return 1;
    }

    private static int discover(CommandSourceStack source, String planetId) {
        PlanetRegistrySavedData data = registry(source);
        PlanetDimensionLifecycle.PlanetDiscoveryOutcome outcome = PlanetDimensionLifecycle.discoverRandomPlanet(source.getServer(), source, data, planetId);
        if (!outcome.success()) {
            source.sendFailure(Component.literal(outcome.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(outcome.message()), false);
        return 1;
    }

    private static int here(CommandSourceStack source) {
        PlanetRegistrySavedData data = registry(source);
        PlanetDimensionLifecycle.CurrentDimensionContext context = PlanetDimensionLifecycle.classifyCurrentDimension(source, data);

        switch (context.kind()) {
            case OVERWORLD -> {
                source.sendSuccess(() -> Component.literal("当前所在维度为主世界。"), false);
                return 1;
            }
            case NETHER -> {
                source.sendSuccess(() -> Component.literal("当前所在维度为下界。"), false);
                return 1;
            }
            case END -> {
                source.sendSuccess(() -> Component.literal("当前所在维度为末地。"), false);
                return 1;
            }
            case MANAGED_PLANET -> {
                PlanetRecord record = context.planetRecord().orElseThrow();
                sendPlanetInfo(source, record, record.coordinates());
                return 1;
            }
            case EXTERNAL -> {
                source.sendFailure(Component.literal("当前维度不属于原版和诗云星球 mod，无法查询。"));
                return 0;
            }
        }

        throw new IllegalStateException("Unexpected dimension kind: " + context.kind());
    }

    private static PlanetRegistrySavedData registry(CommandSourceStack source) {
        return PlanetRegistrySavedData.get(source.getServer());
    }

    private static void sendPlanetInfo(CommandSourceStack source, PlanetRecord record, GalaxyCoordinates observerCoordinates) {
        source.sendSuccess(() -> Component.literal(formatPlanetInfo(record, observerCoordinates)), false);
    }

    private static String formatPlanetList(String title, List<PlanetRecord> records) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append(" (").append(records.size()).append(")");
        if (records.isEmpty()) {
            builder.append("\n").append("（无）");
            return builder.toString();
        }

        for (PlanetRecord record : records) {
            builder.append("\n")
                    .append("- ")
                    .append(record.planetId())
                    .append(" | 已发现: ")
                    .append(record.discovered());
        }

        return builder.toString();
    }

    private static String formatDimensionList(String title, List<PlanetRecord> records) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append(" (").append(records.size()).append(")");
        if (records.isEmpty()) {
            builder.append("\n").append("（无）");
            return builder.toString();
        }

        for (PlanetRecord record : records) {
            builder.append("\n")
                    .append("- ")
                    .append(record.planetId())
                    .append(" | 维度: ")
                    .append(record.dimension().dimensionId())
                    .append(" | 类型: ")
                    .append(formatType(record.type()))
                    .append(" | 已发现: ")
                    .append(record.discovered());
        }

        return builder.toString();
    }

    private static String formatPlanetInfo(PlanetRecord record, GalaxyCoordinates observerCoordinates) {
        GalaxyDistanceUnit unit = SpecialPlanetArchive.getInstance().distanceUnit();
        GalaxyCoordinates coordinates = record.coordinates();
        String absoluteMagnitude = record.observation().isPlaceholder()
                ? record.observation().brightness()
                : PlanetAstrometry.formatMagnitude(record.observation().brightnessMagnitude());
        StringBuilder builder = new StringBuilder();
        builder.append("星球信息")
                .append("\n- ID: ").append(record.planetId())
                .append("\n- 显示名: ").append(record.displayName())
                .append("\n- 类型: ").append(formatType(record.type()))
                .append("\n- 已发现: ").append(record.discovered())
                .append("\n- 种子: ").append(record.seed())
                .append("\n- 出生点: ").append(formatBlockPos(record.spawnPos()))
                .append("\n- 绝对坐标: ").append(formatCoordinates(coordinates, unit));

        if (observerCoordinates != null) {
            double relativeDistance = coordinates.distanceTo(observerCoordinates);
            double observedMagnitude = PlanetAstrometry.apparentMagnitude(record.observation().brightnessMagnitude(), relativeDistance, unit);
            builder.append(" | 相对距离: ").append(PlanetAstrometry.formatDistance(relativeDistance, unit))
                    .append("\n- 绝对视星等: ").append(absoluteMagnitude)
                    .append(" | 观测视星等: ").append(PlanetAstrometry.formatMagnitude(observedMagnitude));
        } else {
            builder.append("\n- 绝对视星等: ").append(absoluteMagnitude);
        }

        builder.append("\n- 维度: ").append(record.dimension().dimensionId())
                .append("\n- 维度状态: registered=").append(record.dimension().registered())
                .append(", materialized=").append(record.dimension().materialized())
                .append(", persistent=").append(record.dimension().persistent())
                .append("\n- 观星信息")
                .append("\n  - 光谱 RGB: ").append(record.observation().spectrum())
                .append("\n  - 元素占比: ").append(record.observation().composition())
                .append("\n  - 重力系数: ").append(record.observation().gravity())
                .append("\n  - 大气: ").append(record.observation().atmosphere())
                .append("\n  - 辐射: ").append(record.observation().radiation())
                .append("\n  - 轨道参数: ").append(record.observation().orbitalAnomaly())
                .append("\n  - 星域编号: ").append(record.observation().sector())
                .append("\n- 材料列表 (").append(record.materials().size()).append(")");

        if (record.materials().isEmpty()) {
            builder.append("\n  - （无）");
        } else {
            for (String material : record.materials()) {
                builder.append("\n  - ").append(material);
            }
        }

        return builder.toString();
    }

    private static GalaxyCoordinates observerCoordinates(PlanetDimensionLifecycle.CurrentDimensionContext context) {
        return switch (context.kind()) {
            case OVERWORLD -> GalaxyCoordinates.zero();
            case MANAGED_PLANET -> context.planetRecord().map(PlanetRecord::coordinates).orElse(null);
            case NETHER, END, EXTERNAL -> null;
        };
    }

    private static String formatCoordinates(GalaxyCoordinates coordinates, GalaxyDistanceUnit unit) {
        if (coordinates == null) {
            return "（无）";
        }

        return coordinates.format(unit);
    }

    private static CompletableFuture<Suggestions> suggestAllPlanetIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        PlanetRegistrySavedData data = registry(context.getSource());
        List<String> ids = data.getAllPlanetsOrdered().stream()
                .map(PlanetRecord::planetId)
                .toList();
        return SharedSuggestionProvider.suggest(
                ids,
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestUndiscoveredRandomPlanetIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        PlanetRegistrySavedData data = registry(context.getSource());
        List<String> ids = data.getRandomPlanets().stream()
                .filter(record -> !record.discovered())
                .filter(record -> !PlanetDimensionLifecycle.isDiscoveryInProgress(record.planetId()))
                .map(PlanetRecord::planetId)
                .toList();
        return SharedSuggestionProvider.suggest(
                ids,
                builder
        );
    }

    private static String formatType(PlanetType type) {
        return switch (type) {
            case SPECIAL -> "特殊";
            case RANDOM -> "随机";
            case EXTERNAL -> "外部";
        };
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
