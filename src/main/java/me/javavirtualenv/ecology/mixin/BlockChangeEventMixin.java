package me.javavirtualenv.ecology.mixin;

import me.javavirtualenv.ecology.spatial.ChunkBlockIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into block place/break events for ChunkBlockIndex invalidation.
 *
 * This mixin:
 * - Notifies ChunkBlockIndex when blocks are placed
 * - Notifies ChunkBlockIndex when blocks are broken
 * - Ensures block search caches stay up-to-date
 *
 * Performance impact: Minimal (O(1) cache updates on block change)
 */
@Mixin(Level.class)
public class BlockChangeEventMixin {

    /**
     * Inject after block is set (placed or broken).
     * Updates ChunkBlockIndex to keep cached searches accurate.
     */
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void onBlockSet(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfo ci) {
        // Only process on server side
        Level level = (Level) (Object) this;
        if (level.isClientSide()) {
            return;
        }

        // Get old block state (before the change)
        BlockState oldState = level.getBlockState(pos);

        // Skip if states are the same (no actual change)
        if (oldState == newState) {
            return;
        }

        Block oldBlock = oldState.getBlock();
        Block newBlock = newState.getBlock();

        // Notify index of removed block
        if (oldBlock != net.minecraft.world.level.block.Blocks.AIR) {
            ChunkBlockIndex.onBlockBreak(level, pos, oldBlock);
        }

        // Notify index of placed block
        if (newBlock != net.minecraft.world.level.block.Blocks.AIR) {
            ChunkBlockIndex.onBlockPlace(level, pos, newBlock);
        }
    }

    /**
     * Inject when block is removed (destroyed).
     */
    @Inject(method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("RETURN"))
    private void onBlockRemoved(BlockPos pos, boolean isMoving, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        if (level.isClientSide()) {
            return;
        }

        // Block is being set to AIR, but we need the old block type
        // The old block should already be cached, so we can check it
        BlockState oldState = level.getBlockState(pos);
        Block oldBlock = oldState.getBlock();

        if (oldBlock != net.minecraft.world.level.block.Blocks.AIR) {
            ChunkBlockIndex.onBlockBreak(level, pos, oldBlock);
        }
    }
}
