package com.poetrycloud.planets.dimension;

import com.poetrycloud.planets.config.PoetryCloudPlanetsConfig;
import com.poetrycloud.planets.PoetryCloudPlanets;
import com.poetrycloud.planets.planet.PlanetDimensionRecord;
import com.poetrycloud.planets.planet.PlanetRecord;
import com.poetrycloud.planets.planet.PlanetRegistrySavedData;
import com.poetrycloud.planets.planet.PlanetType;
import com.poetrycloud.planets.worldgen.PlanetTerrainChunkGenerator;
import com.poetrycloud.planets.worldgen.PlanetWorldgenProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class PlanetDimensionLifecycle {
    private static final Field STORAGE_SOURCE_FIELD = findStorageSourceField();
    private static final ExecutorService DISCOVERY_PREPARE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pcp-discovery-prepare");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentMap<String, DiscoveryTask> ACTIVE_DISCOVERIES = new ConcurrentHashMap<>();
    private static final List<String> DISCOVERY_STEPS = List.of(
            "校验星球状态",
            "读取星球基础数据",
            "读取并冻结配置",
            "解析维度模板与蓝本",
            "选定本次蓝本维度",
            "计算星球种子与生成参数",
            "构建维度快照",
            "主线程注册维度",
            "写入存档并刷新命令树"
    );
    private static final ChunkProgressListener NO_OP_PROGRESS_LISTENER = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(net.minecraft.world.level.ChunkPos chunkPos) {
        }

        @Override
        public void onStatusChange(net.minecraft.world.level.ChunkPos chunkPos, net.minecraft.world.level.chunk.ChunkStatus chunkStatus) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    private PlanetDimensionLifecycle() {
    }

    public static DimensionLifecycleReport bootstrapManagedDimensions(MinecraftServer server, PlanetRegistrySavedData data) {
        int synchronizedPlanets = 0;

        for (PlanetRecord record : data.getAllPlanetsOrdered()) {
            PlanetRecord normalized = normalizeManagedPlanet(server, record);
            if (shouldMaterializeOnBootstrap(normalized)) {
                ensureManagedWorld(server, normalized);
                normalized = normalized.withDimension(normalizeDimension(normalized.dimension(), true, true, true));
            }

            if (!normalized.equals(record)) {
                data.put(normalized);
                synchronizedPlanets++;
            }
        }

        if (synchronizedPlanets > 0) {
            server.markWorldsDirty();
        }

        return new DimensionLifecycleReport(
                synchronizedPlanets,
                countRegistered(data),
                countMaterialized(data),
                countPersistent(data)
        );
    }

    public static CurrentDimensionContext classifyCurrentDimension(CommandSourceStack source, PlanetRegistrySavedData data) {
        ResourceKey<Level> dimension = source.getLevel().dimension();
        if (dimension.equals(Level.OVERWORLD)) {
            return new CurrentDimensionContext(CurrentDimensionKind.OVERWORLD, Optional.empty());
        }

        if (dimension.equals(Level.NETHER)) {
            return new CurrentDimensionContext(CurrentDimensionKind.NETHER, Optional.empty());
        }

        if (dimension.equals(Level.END)) {
            return new CurrentDimensionContext(CurrentDimensionKind.END, Optional.empty());
        }

        Optional<PlanetRecord> matchedPlanet = data.findByDimension(dimension.location());
        if (matchedPlanet.isPresent()) {
            return new CurrentDimensionContext(CurrentDimensionKind.MANAGED_PLANET, matchedPlanet);
        }

        return new CurrentDimensionContext(CurrentDimensionKind.EXTERNAL, Optional.empty());
    }

    public static PlanetDiscoveryOutcome discoverRandomPlanet(MinecraftServer server, CommandSourceStack source, PlanetRegistrySavedData data, String planetId) {
        Optional<PlanetRecord> maybeRecord = data.find(planetId);
        if (maybeRecord.isEmpty()) {
            return PlanetDiscoveryOutcome.failure("星球不存在：" + planetId);
        }

        PlanetRecord record = maybeRecord.get();
        if (!record.isRandom()) {
            return PlanetDiscoveryOutcome.failure("只有随机星球可以手动解锁。");
        }

        if (record.dimension().materialized() || isManagedWorldLoaded(server, record)) {
            return PlanetDiscoveryOutcome.failure("该星球已经解锁。");
        }

        DiscoveryTask existing = ACTIVE_DISCOVERIES.get(record.planetId());
        if (existing != null) {
            return PlanetDiscoveryOutcome.success(existing.currentMessage(), record);
        }

        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        DiscoveryTask task = new DiscoveryTask(server, data, record.planetId(), player == null ? null : player.getUUID());
        DiscoveryTask previous = ACTIVE_DISCOVERIES.putIfAbsent(record.planetId(), task);
        if (previous != null) {
            return PlanetDiscoveryOutcome.success(previous.currentMessage(), record);
        }

        task.start();
        return PlanetDiscoveryOutcome.success("已开始解锁随机星球：" + planetId, record);
    }

    public static boolean isDiscoveryInProgress(String planetId) {
        return ACTIVE_DISCOVERIES.containsKey(normalizePlanetId(planetId));
    }

    public static void cancelDiscoveryJobs() {
        for (DiscoveryTask task : ACTIVE_DISCOVERIES.values()) {
            task.cancel();
        }
        ACTIVE_DISCOVERIES.clear();
    }

    private static String normalizePlanetId(String planetId) {
        if (planetId == null) {
            return "unnamed_planet";
        }

        String value = planetId.trim();
        return value.isEmpty() ? "unnamed_planet" : value;
    }

    private static PlanetRecord normalizeManagedPlanet(MinecraftServer server, PlanetRecord record) {
        boolean loaded = isManagedWorldLoaded(server, record);
        PlanetDimensionRecord normalizedDimension = switch (record.type()) {
            case SPECIAL -> normalizeDimension(record.dimension(), loaded, loaded, true);
            case RANDOM -> record.discovered()
                    ? normalizeDimension(record.dimension(), loaded, loaded, true)
                    : normalizeDimension(record.dimension(), false, false, true);
            case EXTERNAL -> normalizeDimension(record.dimension(), loaded, loaded, true);
        };

        PlanetRecord normalized = normalizedDimension.equals(record.dimension())
                ? record
                : record.withDimension(normalizedDimension);
        return record.type() == PlanetType.SPECIAL ? normalized.withDiscovered(true) : normalized;
    }

    private static boolean shouldMaterializeOnBootstrap(PlanetRecord record) {
        return record.isRandom() && record.discovered();
    }

    private static PlanetDimensionRecord normalizeDimension(PlanetDimensionRecord dimension, boolean registered, boolean materialized, boolean persistent) {
        PlanetDimensionRecord normalized = dimension == null ? PlanetDimensionRecord.unassigned() : dimension;
        if (normalized.registered() != registered) {
            normalized = normalized.withRegistered(registered);
        }
        if (normalized.materialized() != materialized) {
            normalized = normalized.withMaterialized(materialized);
        }
        if (normalized.persistent() != persistent) {
            normalized = normalized.withPersistent(persistent);
        }
        return normalized;
    }

    private static boolean isManagedWorldLoaded(MinecraftServer server, PlanetRecord record) {
        return server.getLevel(dimensionKey(record)) != null;
    }

    private static ServerLevel ensureManagedWorld(MinecraftServer server, PlanetRecord record) {
        PlanetWorldgenProfile profile = resolveWorldgenProfile(record);
        PreparedDimensionSnapshot snapshot = buildPreparedSnapshot(server, record, profile);
        return attachPreparedSnapshot(server, snapshot);
    }

    private static PreparedDimensionSnapshot buildPreparedSnapshot(MinecraftServer server, PlanetRecord record, PlanetWorldgenProfile profile) {
        long planetSeed = mixPlanetSeed(server.getWorldData().worldGenOptions().seed(), record);
        ReferenceWorldgen reference = resolveReferenceWorldgen(server, record, profile);
        return new PreparedDimensionSnapshot(record, profile, planetSeed, reference);
    }

    private static ServerLevel attachPreparedSnapshot(MinecraftServer server, PreparedDimensionSnapshot snapshot) {
        ResourceKey<Level> levelKey = dimensionKey(snapshot.record());
        ServerLevel existing = server.getLevel(levelKey);
        if (existing != null) {
            alignSpawn(existing, snapshot.record());
            return existing;
        }

        PoetryCloudPlanets.LOGGER.info("Attaching managed dimension {} from template {}", snapshot.record().dimension().dimensionId(), snapshot.reference().referenceDimensionId());
        ServerLevel created = createManagedWorld(server, snapshot, levelKey);
        ServerLevel previous = server.forgeGetWorldMap().putIfAbsent(levelKey, created);
        if (previous != null) {
            PoetryCloudPlanets.LOGGER.info("Managed dimension {} was attached concurrently, reusing existing world", snapshot.record().dimension().dimensionId());
            alignSpawn(previous, snapshot.record());
            return previous;
        }

        alignSpawn(created, snapshot.record());
        server.markWorldsDirty();
        PoetryCloudPlanets.LOGGER.info("Managed dimension {} attached successfully", snapshot.record().dimension().dimensionId());
        return created;
    }

    private static ServerLevel createManagedWorld(
            MinecraftServer server,
            PreparedDimensionSnapshot snapshot,
            ResourceKey<Level> levelKey
    ) {
        LevelStorageSource.LevelStorageAccess storageAccess = storageAccess(server);
        WorldData worldData = server.getWorldData();
        DerivedLevelData planetData = new DerivedLevelData(worldData, (ServerLevelData) server.overworld().getLevelData());

        ChunkGenerator generator = createChunkGenerator(snapshot);
        LevelStem levelStem = new LevelStem(snapshot.reference().dimensionType(), generator);
        PoetryCloudPlanets.LOGGER.info("Creating managed world {} with seed {}", levelKey.location(), snapshot.planetSeed());
        return new ServerLevel(
                server,
                server,
                storageAccess,
                planetData,
                levelKey,
                levelStem,
                NO_OP_PROGRESS_LISTENER,
                worldData.isDebugWorld(),
                BiomeManager.obfuscateSeed(snapshot.planetSeed()),
                List.of(),
                false,
                new RandomSequences(snapshot.planetSeed())
        );
    }

    private static ChunkGenerator createChunkGenerator(PreparedDimensionSnapshot snapshot) {
        if (shouldUseReferenceNoiseGenerator(snapshot)) {
            PoetryCloudPlanets.LOGGER.info(
                    "Using plain NoiseBasedChunkGenerator for special planet {}",
                    snapshot.record().planetId()
            );
            return new NoiseBasedChunkGenerator(
                    snapshot.reference().biomeSource(),
                    snapshot.reference().settings()
            );
        }

        return new PlanetTerrainChunkGenerator(
                snapshot.reference().biomeSource(),
                snapshot.reference().settings(),
                snapshot.record(),
                snapshot.planetSeed(),
                snapshot.profile()
        );
    }

    private static boolean shouldUseReferenceNoiseGenerator(PreparedDimensionSnapshot snapshot) {
        PlanetRecord record = snapshot.record();
        PlanetWorldgenProfile profile = snapshot.profile();
        return record.type() == PlanetType.SPECIAL
                && !profile.replaceMaterials()
                && profile.generateStructures()
                && profile.spawnMobs()
                && profile.allStagesEnabled();
    }

    private static void refreshCommandTrees(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }

    private static void alignSpawn(ServerLevel level, PlanetRecord record) {
        level.setDefaultSpawnPos(resolveDeclaredSpawn(level, record.spawnPos()), 0.0F);
    }

    private static BlockPos resolveDeclaredSpawn(ServerLevel level, BlockPos preferred) {
        BlockPos fallback = level.getSharedSpawnPos();
        BlockPos candidate = preferred == null ? fallback : preferred;
        if (level.isInWorldBounds(candidate)) {
            return candidate;
        }

        if (level.isInWorldBounds(fallback)) {
            return fallback;
        }

        return BlockPos.ZERO;
    }

    private static LevelStorageSource.LevelStorageAccess storageAccess(MinecraftServer server) {
        try {
            return (LevelStorageSource.LevelStorageAccess) STORAGE_SOURCE_FIELD.get(server);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("无法读取服务器存档访问器", exception);
        }
    }

    private static Field findStorageSourceField() {
        try {
            Field field = MinecraftServer.class.getDeclaredField("storageSource");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static ResourceKey<Level> dimensionKey(PlanetRecord record) {
        return dimensionKey(record.dimension().dimensionId());
    }

    private static ResourceKey<Level> dimensionKey(ResourceLocation dimensionId) {
        return ResourceKey.create(Registries.DIMENSION, dimensionId);
    }

    private static PlanetWorldgenProfile resolveWorldgenProfile(PlanetRecord record) {
        if (record.isRandom() || PlanetDimensionTemplates.RANDOM_PLANET.equals(record.dimension().templateDimensionId())) {
            return PoetryCloudPlanetsConfig.COMMON.randomPlanetWorldgenProfile();
        }

        return PoetryCloudPlanetsConfig.COMMON.specialPlanetWorldgenProfile();
    }

    private static ReferenceWorldgen resolveReferenceWorldgen(MinecraftServer server, PlanetRecord record, PlanetWorldgenProfile profile) {
        ResourceLocation referenceId = resolveReferenceDimensionId(server, record, profile);
        ServerLevel referenceLevel = null;
        if (referenceId != null) {
            ResourceKey<Level> referenceKey = ResourceKey.create(Registries.DIMENSION, referenceId);
            referenceLevel = server.getLevel(referenceKey);
        }

        if (referenceLevel == null) {
            referenceLevel = server.overworld();
        }

        if (referenceLevel == null) {
            throw new IllegalStateException("无法解析参考维度");
        }

        ChunkGenerator generator = referenceLevel.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        Holder<NoiseGeneratorSettings> settings = generator instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator
                ? noiseBasedChunkGenerator.generatorSettings()
                : server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new ReferenceWorldgen(referenceId, biomeSource, settings, referenceLevel.dimensionTypeRegistration());
    }

    private static ResourceLocation resolveReferenceDimensionId(MinecraftServer server, PlanetRecord record, PlanetWorldgenProfile profile) {
        ResourceLocation templateDimensionId = record == null ? null : record.dimension().templateDimensionId();
        if (templateDimensionId != null && !PlanetDimensionTemplates.isInternalTemplate(templateDimensionId)) {
            ResourceKey<Level> templateKey = ResourceKey.create(Registries.DIMENSION, templateDimensionId);
            if (server.getLevel(templateKey) != null) {
                return templateDimensionId;
            }
        }

        if (profile == null) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        }

        ArrayList<ResourceLocation> available = new ArrayList<>();
        for (ResourceLocation candidate : profile.referenceDimensionIds()) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, candidate);
            if (server.getLevel(key) != null && !available.contains(candidate)) {
                available.add(candidate);
            }
        }

        if (available.isEmpty()) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        }

        long selectorSeed = record == null ? 0L : mixPlanetSeed(record.seed(), record);
        int index = (int) Math.floorMod(selectorSeed, available.size());
        return available.get(index);
    }

    public static long mixPlanetSeed(long baseSeed, PlanetRecord record) {
        long value = baseSeed ^ record.seed();
        value ^= ((long) record.planetId().hashCode() << 32);
        value ^= record.dimension().dimensionId().hashCode();
        value = Long.rotateLeft(value, 17);
        value ^= 0x9E3779B97F4A7C15L;
        return value;
    }

    private static int countRegistered(PlanetRegistrySavedData data) {
        int count = 0;
        for (PlanetRecord record : data.getAllPlanetsOrdered()) {
            if (record.dimension().registered()) {
                count++;
            }
        }
        return count;
    }

    private static int countMaterialized(PlanetRegistrySavedData data) {
        int count = 0;
        for (PlanetRecord record : data.getAllPlanetsOrdered()) {
            if (record.dimension().materialized()) {
                count++;
            }
        }
        return count;
    }

    private static int countPersistent(PlanetRegistrySavedData data) {
        int count = 0;
        for (PlanetRecord record : data.getAllPlanetsOrdered()) {
            if (record.dimension().persistent()) {
                count++;
            }
        }
        return count;
    }

    private static void sendDiscoveryProgressMessage(MinecraftServer server, UUID playerId, String planetId, int completed, String currentStep) {
        if (playerId == null || currentStep == null || currentStep.isBlank()) {
            return;
        }

        Runnable task = () -> {
            if (server.isStopped()) {
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }

            player.sendSystemMessage(Component.literal(
                    "正在解锁星球 " + planetId + " ｜ " + completed + " / " + DISCOVERY_STEPS.size() + " ｜ 正在" + currentStep
            ));
        };
        executeOnServerThread(server, task);
    }

    private static void sendDiscoveryCompletionMessage(MinecraftServer server, UUID playerId, String planetId) {
        if (playerId == null) {
            return;
        }

        Runnable task = () -> {
            if (server.isStopped()) {
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("已解锁随机星球：" + planetId));
            }
        };
        executeOnServerThread(server, task);
    }

    private static void sendDiscoveryFailureMessage(MinecraftServer server, UUID playerId, String planetId, String stepName, Throwable throwable) {
        if (playerId == null) {
            return;
        }

        String detail = throwable == null ? "未知错误" : describeFailure(throwable);
        String phase = stepName == null || stepName.isBlank() ? "未知步骤" : stepName;
        Runnable task = () -> {
            if (server.isStopped()) {
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("解锁失败：" + planetId + " ｜ 卡在：" + phase + " ｜ " + detail));
            }
        };
        executeOnServerThread(server, task);
    }

    private static String describeFailure(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static void executeOnServerThread(MinecraftServer server, Runnable action) {
        Thread runningThread = server.getRunningThread();
        if (runningThread != null && Thread.currentThread() == runningThread) {
            action.run();
        } else {
            server.execute(action);
        }
    }

    private static <T> T supplyOnServerThread(MinecraftServer server, Supplier<T> supplier) {
        Thread runningThread = server.getRunningThread();
        if (runningThread != null && Thread.currentThread() == runningThread) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.join();
    }

    public enum CurrentDimensionKind {
        OVERWORLD,
        NETHER,
        END,
        MANAGED_PLANET,
        EXTERNAL
    }

    public record CurrentDimensionContext(CurrentDimensionKind kind, Optional<PlanetRecord> planetRecord) {
        public static CurrentDimensionContext empty(CurrentDimensionKind kind) {
            return new CurrentDimensionContext(kind, Optional.empty());
        }
    }

    public record PlanetDiscoveryOutcome(boolean success, String message, Optional<PlanetRecord> planetRecord) {
        public static PlanetDiscoveryOutcome success(String message, PlanetRecord planetRecord) {
            return new PlanetDiscoveryOutcome(true, message, Optional.ofNullable(planetRecord));
        }

        public static PlanetDiscoveryOutcome failure(String message) {
            return new PlanetDiscoveryOutcome(false, message, Optional.empty());
        }
    }

    public record DimensionLifecycleReport(
            int synchronizedPlanets,
            int registeredPlanets,
            int materializedPlanets,
            int persistentPlanets
    ) {
    }

    private record ReferenceWorldgen(
            ResourceLocation referenceDimensionId,
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            Holder<DimensionType> dimensionType
    ) {
    }

    private record PreparedDimensionSnapshot(
            PlanetRecord record,
            PlanetWorldgenProfile profile,
            long planetSeed,
            ReferenceWorldgen reference
    ) {
    }

    private enum DiscoveryStatus {
        PREPARING,
        READY_TO_ATTACH,
        ATTACHING,
        ACTIVE,
        FAILED,
        CANCELLED
    }

    private static final class DiscoveryTask {
        private final MinecraftServer server;
        private final PlanetRegistrySavedData data;
        private final String planetId;
        private final UUID playerId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile CompletableFuture<Void> future;
        private volatile PreparedDimensionSnapshot snapshot;
        private volatile DiscoveryStatus status = DiscoveryStatus.PREPARING;
        private volatile int completedSteps;
        private volatile String currentStepName = DISCOVERY_STEPS.get(0);

        private DiscoveryTask(MinecraftServer server, PlanetRegistrySavedData data, String planetId, UUID playerId) {
            this.server = server;
            this.data = data;
            this.planetId = planetId;
            this.playerId = playerId;
        }

        private void start() {
            sendDiscoveryProgressMessage(server, playerId, planetId, 0, currentStepName);
            future = CompletableFuture.runAsync(this::runPrepare, DISCOVERY_PREPARE_EXECUTOR);
        }

        private void cancel() {
            cancelled.set(true);
            status = DiscoveryStatus.CANCELLED;
            CompletableFuture<Void> current = future;
            if (current != null) {
                current.cancel(true);
            }
        }

        private String currentMessage() {
            return "正在解锁星球 " + planetId + " ｜ " + completedSteps + " / " + DISCOVERY_STEPS.size() + " ｜ 正在" + currentStepName;
        }

        private void runPrepare() {
            try {
                PlanetRecord record = stepValidate();
                stepReadPlanetRecord();
                PlanetWorldgenProfile profile = stepFreezeProfile(record);
                stepResolveTemplate();
                stepResolveReference(record, profile);
                long planetSeed = stepComputeSeed(record);
                PreparedDimensionSnapshot prepared = stepBuildSnapshot(record, profile, planetSeed);
                snapshot = prepared;
                status = DiscoveryStatus.READY_TO_ATTACH;
                completeStep(7, DISCOVERY_STEPS.get(7));
                executeOnServerThread(server, this::attachOnServerThread);
            } catch (Throwable throwable) {
                fail(throwable);
            }
        }

        private PlanetRecord stepValidate() {
            PlanetRecord record = supplyOnServerThread(server, () -> data.find(planetId).orElse(null));
            if (cancelled.get() || server.isStopped()) {
                throw new IllegalStateException("服务器正在关闭");
            }
            if (record == null) {
                throw new IllegalStateException("星球不存在");
            }
            if (!record.isRandom()) {
                throw new IllegalStateException("只有随机星球可以手动解锁");
            }
            if (record.dimension().materialized() || isManagedWorldLoaded(server, record)) {
                throw new IllegalStateException("该星球已经解锁");
            }
            completeStep(1, DISCOVERY_STEPS.get(1));
            return record;
        }

        private void stepReadPlanetRecord() {
            ensureRunning();
            completeStep(2, DISCOVERY_STEPS.get(2));
        }

        private PlanetWorldgenProfile stepFreezeProfile(PlanetRecord record) {
            ensureRunning();
            PlanetWorldgenProfile profile = resolveWorldgenProfile(record);
            completeStep(3, DISCOVERY_STEPS.get(3));
            return profile;
        }

        private void stepResolveTemplate() {
            ensureRunning();
            completeStep(4, DISCOVERY_STEPS.get(4));
        }

        private void stepResolveReference(PlanetRecord record, PlanetWorldgenProfile profile) {
            ensureRunning();
            supplyOnServerThread(server, () -> resolveReferenceDimensionId(server, record, profile));
            completeStep(5, DISCOVERY_STEPS.get(5));
        }

        private long stepComputeSeed(PlanetRecord record) {
            ensureRunning();
            long seed = mixPlanetSeed(server.getWorldData().worldGenOptions().seed(), record);
            completeStep(6, DISCOVERY_STEPS.get(6));
            return seed;
        }

        private PreparedDimensionSnapshot stepBuildSnapshot(PlanetRecord record, PlanetWorldgenProfile profile, long planetSeed) {
            ensureRunning();
            PreparedDimensionSnapshot prepared = supplyOnServerThread(
                    server,
                    () -> new PreparedDimensionSnapshot(record, profile, planetSeed, resolveReferenceWorldgen(server, record, profile))
            );
            return prepared;
        }

        private void attachOnServerThread() {
            try {
                ensureRunning();
                status = DiscoveryStatus.ATTACHING;
                PreparedDimensionSnapshot prepared = snapshot;
                if (prepared == null) {
                    throw new IllegalStateException("维度快照缺失");
                }

                PoetryCloudPlanets.LOGGER.info("Discover attach start for {}", planetId);
                attachPreparedSnapshot(server, prepared);
                completeStep(8, DISCOVERY_STEPS.get(8));

                PlanetRecord discovered = data.discover(planetId);
                if (discovered == null) {
                    throw new IllegalStateException("写入星球状态失败");
                }

                PlanetRecord updated = discovered.withDimension(normalizeDimension(discovered.dimension(), true, true, true));
                data.put(updated);
                server.markWorldsDirty();
                refreshCommandTrees(server);
                PoetryCloudPlanets.LOGGER.info("Discover attach persisted and command tree refreshed for {}", planetId);

                completedSteps = DISCOVERY_STEPS.size();
                currentStepName = "解锁完成";
                status = DiscoveryStatus.ACTIVE;
                ACTIVE_DISCOVERIES.remove(planetId, this);
                sendDiscoveryCompletionMessage(server, playerId, planetId);
            } catch (Throwable throwable) {
                fail(throwable);
            }
        }

        private void completeStep(int completed, String nextStepName) {
            completedSteps = completed;
            currentStepName = nextStepName;
            sendDiscoveryProgressMessage(server, playerId, planetId, completed, nextStepName);
        }

        private void fail(Throwable throwable) {
            if (cancelled.get() || server.isStopped()) {
                status = DiscoveryStatus.CANCELLED;
                ACTIVE_DISCOVERIES.remove(planetId, this);
                return;
            }

            status = DiscoveryStatus.FAILED;
            ACTIVE_DISCOVERIES.remove(planetId, this);
            sendDiscoveryFailureMessage(server, playerId, planetId, currentStepName, throwable);
        }

        private void ensureRunning() {
            if (cancelled.get() || server.isStopped()) {
                throw new IllegalStateException("服务器正在关闭");
            }
        }
    }
}
