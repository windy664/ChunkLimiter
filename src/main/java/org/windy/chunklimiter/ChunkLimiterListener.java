package org.windy.chunklimiter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkLimiterListener {

    // 使用线程安全的缓存结构：区块位置 -> (方块类型 -> 数量)
    private static final Map<ChunkPos, Map<Block, AtomicInteger>> chunkCounter = new ConcurrentHashMap<>();
    private static final int MAX_COUNT = 10;
    private static int recalibrationTick = 0;

    // 区块加载时初始化计数器
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ChunkPos chunkPos = event.getChunk().getPos();
        Map<Block, AtomicInteger> blockCounts = new ConcurrentHashMap<>();

        // 异步初始化计数器（需确保线程安全）
        new Thread(() -> {
            BlockPos.betweenClosedStream(
                    chunkPos.getMinBlockX(),
                    level.getMinBuildHeight(),
                    chunkPos.getMinBlockZ(),
                    chunkPos.getMaxBlockX(),
                    level.getMaxBuildHeight() - 1,
                    chunkPos.getMaxBlockZ()
            ).forEach(pos -> {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    blockCounts.computeIfAbsent(state.getBlock(), k -> new AtomicInteger())
                            .incrementAndGet();
                }
            });
            chunkCounter.put(chunkPos, blockCounts);
        }).start();
    }

    // 区块卸载时清理缓存
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        chunkCounter.remove(event.getChunk().getPos());
    }

    // 处理方块放置事件
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer) || !(event.getLevel() instanceof ServerLevel)) return;

        Block block = event.getPlacedBlock().getBlock();
        ChunkPos chunkPos = new ChunkPos(event.getPos());

        Map<Block, AtomicInteger> blockCounts = chunkCounter.computeIfAbsent(chunkPos, k -> new ConcurrentHashMap<>());
        AtomicInteger count = blockCounts.computeIfAbsent(block, k -> new AtomicInteger(0));

        // 快速检查（无需等待异步线程）
        if (count.get() >= MAX_COUNT) {
            event.setCanceled(true);
            return;
        }

        // 异步更新计数器（最终一致性）
        new Thread(() -> {
            int current = count.incrementAndGet();
            if (current > MAX_COUNT) {
                count.decrementAndGet();
            }
        }).start();
    }

    // 处理方块破坏事件
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;

        Block block = event.getState().getBlock();
        ChunkPos chunkPos = new ChunkPos(event.getPos());

        chunkCounter.computeIfPresent(chunkPos, (pos, blockCounts) -> {
            blockCounts.computeIfPresent(block, (b, counter) -> {
                counter.decrementAndGet();
                return counter;
            });
            return blockCounts;
        });
    }

    // 每10分钟执行一次校准（分散到多个tick）
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++recalibrationTick % 12000 == 0) { // 每分钟校准一次（20tick/s * 60s）
            chunkCounter.keySet().forEach(chunkPos -> {
                if (((ServerLevel) event.getServer().overworld()).getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                    new Thread(new ChunkRecalibrationTask(chunkPos)).start();
                }
            });
        }
    }

    // 区块校准任务
    private static class ChunkRecalibrationTask implements Runnable {
        private final ChunkPos chunkPos;

        public ChunkRecalibrationTask(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
        }
        @Override
        public void run() {
            // 获取 MinecraftServer 实例
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return; // 服务器未运行，跳过校准
            }

            // 获取主世界（Overworld）实例
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null || !level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                return; // 如果世界未加载或区块未加载，跳过校准
            }

            // 创建一个新的计数器
            Map<Block, AtomicInteger> newCounts = new ConcurrentHashMap<>();

            // 遍历区块内的所有方块
            BlockPos.betweenClosedStream(
                    chunkPos.getMinBlockX(),
                    level.getMinBuildHeight(),
                    chunkPos.getMinBlockZ(),
                    chunkPos.getMaxBlockX(),
                    level.getMaxBuildHeight() - 1,
                    chunkPos.getMaxBlockZ()
            ).forEach(pos -> {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    newCounts.computeIfAbsent(state.getBlock(), k -> new AtomicInteger())
                            .incrementAndGet();
                }
            });

            // 更新缓存
            chunkCounter.put(chunkPos, newCounts);

            // 日志记录（可选）
            System.out.println("Recalibrated chunk at " + chunkPos + ": " + newCounts);
        }
    }
}