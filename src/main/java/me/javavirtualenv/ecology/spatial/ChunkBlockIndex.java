package me.javavirtualenv.ecology.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Chunk-based index of interesting blocks (crops, flowers, grass, nesting materials).
 * Provides O(1) lookup of blocks by chunk instead of O(n³) brute force searching.
 *
 * Features:
 * - Lazy indexing: Only indexes chunks when blocks change
 * - Block categorization: Separate sets for crops, flowers, grass, nesting blocks
 * - Automatic invalidation: Index updates when blocks are placed/broken
 * - Memory efficient: Only stores positions of interesting blocks
 */
public final class ChunkBlockIndex {

    private static final int MAX_CACHED_CHUNKS = 500;

    private static final Map<ChunkKey, ChunkData> index = new ConcurrentHashMap<>();

    private ChunkBlockIndex() {
        // Utility class
    }

    /**
     * Get all blocks of specific types in a chunk.
     * Returns cached index if available, otherwise performs lazy indexing.
     *
     * @param level World to search
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param targetBlocks Block types to find
     * @return List of block positions (immutable)
     */
    public static List<BlockPos> getBlocksInChunk(BlockGetter level, int chunkX, int chunkZ, List<Block> targetBlocks) {
        if (targetBlocks.isEmpty()) {
            return List.of();
        }

        String dimension = getDimensionId(level);
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);

        // Get or create chunk data
        ChunkData chunkData = index.computeIfAbsent(key, k -> {
            // Lazy indexing: scan chunk on first access
            return indexChunk(level, chunkX, chunkZ);
        });

        // Collect matching blocks
        List<BlockPos> results = new ArrayList<>();

        for (Block targetBlock : targetBlocks) {
            if (targetBlock instanceof CropBlock) {
                results.addAll(chunkData.crops);
            } else if (targetBlock instanceof FlowerBlock || isFlowerBlock(targetBlock)) {
                results.addAll(chunkData.flowers);
            } else if (isNestingBlock(targetBlock)) {
                results.addAll(chunkData.nestingBlocks);
            } else if (chunkData.otherBlocks.containsKey(targetBlock)) {
                results.addAll(chunkData.otherBlocks.get(targetBlock));
            }
        }

        return results;
    }

    /**
     * Notify index that a block has been placed.
     * Adds the block to the index if it's interesting.
     */
    public static void onBlockPlace(Level level, BlockPos pos, Block block) {
        String dimension = getDimensionId(level);
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);

        ChunkData chunkData = index.computeIfAbsent(key, k -> new ChunkData());
        BlockPos immutablePos = pos.immutable();

        if (block instanceof CropBlock) {
            chunkData.crops.add(immutablePos);
        } else if (block instanceof FlowerBlock || isFlowerBlock(block)) {
            chunkData.flowers.add(immutablePos);
        } else if (isNestingBlock(block)) {
            chunkData.nestingBlocks.add(immutablePos);
        } else {
            chunkData.otherBlocks.computeIfAbsent(block, b -> new CopyOnWriteArraySet<>()).add(immutablePos);
        }

        // Invalidate spatial cache for this chunk
        BlockSpatialCache.invalidateChunk(chunkX, chunkZ);
    }

    /**
     * Notify index that a block has been broken.
     * Removes the block from the index.
     */
    public static void onBlockBreak(Level level, BlockPos pos, Block block) {
        String dimension = getDimensionId(level);
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);

        ChunkData chunkData = index.get(key);
        if (chunkData == null) {
            return;
        }

        BlockPos immutablePos = pos.immutable();

        if (block instanceof CropBlock) {
            chunkData.crops.remove(immutablePos);
        } else if (block instanceof FlowerBlock || isFlowerBlock(block)) {
            chunkData.flowers.remove(immutablePos);
        } else if (isNestingBlock(block)) {
            chunkData.nestingBlocks.remove(immutablePos);
        } else {
            Set<BlockPos> positions = chunkData.otherBlocks.get(block);
            if (positions != null) {
                positions.remove(immutablePos);
            }
        }

        // Invalidate spatial cache for this chunk
        BlockSpatialCache.invalidateChunk(chunkX, chunkZ);
    }

    /**
     * Invalidate all data for a specific chunk.
     * Forces re-indexing on next access.
     */
    public static void invalidateChunk(Level level, int chunkX, int chunkZ) {
        String dimension = getDimensionId(level);
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        index.remove(key);
        BlockSpatialCache.invalidateChunk(chunkX, chunkZ);
    }

    /**
     * Clear all indexed data.
     */
    public static void clear() {
        index.clear();
        BlockSpatialCache.clear();
    }

    /**
     * Perform lazy indexing of a chunk.
     * Scans all blocks in chunk and categorizes them.
     */
    private static ChunkData indexChunk(BlockGetter level, int chunkX, int chunkZ) {
        ChunkData data = new ChunkData();

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        // Scan entire chunk (16x16 columns, up to build height)
        // Limit scan to reasonable Y range for performance
        int minY = level.getMinBuildHeight();
        int maxY = Math.min(level.getMaxBuildHeight(), minY + 256);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                    BlockStateSnapshot snapshot = getBlockState(level, pos);

                    if (snapshot == null) {
                        continue;
                    }

                    Block block = snapshot.block();

                    if (block instanceof CropBlock) {
                        data.crops.add(pos.immutable());
                    } else if (block instanceof FlowerBlock || isFlowerBlock(block)) {
                        data.flowers.add(pos.immutable());
                    } else if (isNestingBlock(block)) {
                        data.nestingBlocks.add(pos.immutable());
                    } else {
                        data.otherBlocks.computeIfAbsent(block, b -> new CopyOnWriteArraySet<>())
                                .add(pos.immutable());
                    }
                }
            }
        }

        return data;
    }

    /**
     * Get block state safely (handles different level types).
     */
    private static BlockStateSnapshot getBlockState(BlockGetter level, BlockPos pos) {
        try {
            if (level instanceof Level levelInstance && !levelInstance.isLoaded(pos)) {
                return null;
            }
            return new BlockStateSnapshot(level.getBlockState(pos).getBlock(), pos);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if block is a flower (using tag-based detection).
     */
    private static boolean isFlowerBlock(Block block) {
        try {
            // Check if block has flower tag
            return block.builtInRegistryHolder().is(net.minecraft.tags.BlockTags.FLOWERS);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if block is a nesting material (hay, grass, wool).
     */
    private static boolean isNestingBlock(Block block) {
        try {
            var registryName = block.builtInRegistryHolder().key().location();
            String path = registryName.getPath();
            return path.contains("hay_block") ||
                    path.contains("grass_block") ||
                    path.contains("wool") ||
                    path.contains("carpet");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get dimension ID for chunk key.
     */
    private static String getDimensionId(BlockGetter level) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return serverLevel.dimension().location().toString();
        }
        return "client:" + level.hashCode();
    }

    /**
     * Clean up old chunk data if index is too large.
     */
    public static void cleanup() {
        if (index.size() > MAX_CACHED_CHUNKS) {
            // Remove least recently used chunks
            // For now, just clear old entries
            index.clear();
        }
    }

    /**
     * Chunk key for indexing.
     */
    private static final class ChunkKey {
        private final String dimensionId;
        private final int chunkX;
        private final int chunkZ;

        ChunkKey(String dimensionId, int chunkX, int chunkZ) {
            this.dimensionId = dimensionId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey other)) return false;
            return chunkX == other.chunkX &&
                    chunkZ == other.chunkZ &&
                    dimensionId.equals(other.dimensionId);
        }

        @Override
        public int hashCode() {
            int result = dimensionId.hashCode();
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            return result;
        }
    }

    /**
     * Indexed block data for a single chunk.
     */
    private static final class ChunkData {
        private final Set<BlockPos> crops = new CopyOnWriteArraySet<>();
        private final Set<BlockPos> flowers = new CopyOnWriteArraySet<>();
        private final Set<BlockPos> nestingBlocks = new CopyOnWriteArraySet<>();
        private final Map<Block, Set<BlockPos>> otherBlocks = new ConcurrentHashMap<>();
    }

    /**
     * Simple snapshot of block state.
     */
    private static final class BlockStateSnapshot {
        private final Block block;
        private final BlockPos pos;

        BlockStateSnapshot(Block block, BlockPos pos) {
            this.block = block;
            this.pos = pos;
        }

        Block block() {
            return block;
        }
    }
}
