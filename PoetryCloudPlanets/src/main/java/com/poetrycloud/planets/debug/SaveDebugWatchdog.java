package com.poetrycloud.planets.debug;

import com.poetrycloud.planets.PoetryCloudPlanets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SaveDebugWatchdog {
    private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "pcp-save-watchdog");
        thread.setDaemon(true);
        return thread;
    };

    private static volatile ScheduledExecutorService executor;
    private static volatile Instant startedAt;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private SaveDebugWatchdog() {
    }

    public static void markSaveStart(String phase, MinecraftServer server) {
        PoetryCloudPlanets.LOGGER.info("[save-debug] save phase started: {}", phase);
        logServerSnapshot(server, "phase=" + phase);
    }

    public static void start(MinecraftServer server, String reason) {
        if (!running.compareAndSet(false, true)) {
            PoetryCloudPlanets.LOGGER.info("[save-debug] watchdog already running: {}", reason);
            return;
        }

        startedAt = Instant.now();
        executor = Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);
        PoetryCloudPlanets.LOGGER.info("[save-debug] watchdog started: {}", reason);
        logServerSnapshot(server, "watchdog-start");
        executor.scheduleAtFixedRate(() -> {
            try {
                logServerSnapshot(server, "watchdog-tick");
                logRelevantThreads();
            } catch (Throwable throwable) {
                PoetryCloudPlanets.LOGGER.error("[save-debug] watchdog tick failed", throwable);
            }
        }, 2L, 2L, TimeUnit.SECONDS);
    }

    public static void stop(String reason) {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }

        Duration duration = startedAt == null ? Duration.ZERO : Duration.between(startedAt, Instant.now());
        PoetryCloudPlanets.LOGGER.info("[save-debug] watchdog stopped: {} after {} ms", reason, duration.toMillis());
    }

    public static void logLevelSave(ServerLevel level) {
        PoetryCloudPlanets.LOGGER.info(
                "[save-debug] level save event: dimension={}, players={}, chunkGenerator={}",
                level.dimension().location(),
                level.players().size(),
                level.getChunkSource().getGenerator().getClass().getName()
        );
    }

    private static void logServerSnapshot(MinecraftServer server, String label) {
        if (server == null) {
            PoetryCloudPlanets.LOGGER.info("[save-debug] snapshot {} skipped: server null", label);
            return;
        }

        StringBuilder levels = new StringBuilder();
        for (ServerLevel level : server.getAllLevels()) {
            if (!levels.isEmpty()) {
                levels.append(" | ");
            }
            levels.append(level.dimension().location())
                    .append(" players=").append(level.players().size())
                    .append(" generator=").append(level.getChunkSource().getGenerator().getClass().getSimpleName());
        }

        StringBuilder players = new StringBuilder();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!players.isEmpty()) {
                players.append(" | ");
            }
            players.append(player.getGameProfile().getName())
                    .append("@")
                    .append(player.level().dimension().location())
                    .append(" ")
                    .append(player.blockPosition());
        }

        PoetryCloudPlanets.LOGGER.info(
                "[save-debug] snapshot {}: stopped={}, running={}, players=[{}], levels=[{}]",
                label,
                server.isStopped(),
                server.isRunning(),
                players,
                levels
        );
    }

    private static void logRelevantThreads() {
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            String name = thread.getName();
            if (!name.contains("Server thread") && !name.contains("Chunk source main thread executor")) {
                continue;
            }

            ThreadInfo info = THREAD_BEAN.getThreadInfo(thread.getId(), 40);
            if (info == null) {
                continue;
            }

            PoetryCloudPlanets.LOGGER.info(
                    "[save-debug] thread snapshot: name='{}', state={}, lockName={}, lockOwner={}",
                    info.getThreadName(),
                    info.getThreadState(),
                    info.getLockName(),
                    info.getLockOwnerName()
            );

            StackTraceElement[] stack = info.getStackTrace();
            for (int i = 0; i < Math.min(stack.length, 25); i++) {
                PoetryCloudPlanets.LOGGER.info("[save-debug]   at {}", stack[i]);
            }
        }
    }
}
