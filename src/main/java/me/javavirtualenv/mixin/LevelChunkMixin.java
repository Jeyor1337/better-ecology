package me.javavirtualenv.mixin;

import me.javavirtualenv.ecology.mixin.ChunkPersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin for LevelChunk to add persistent data storage capability.
 * Implements the ChunkPersistentData interface for storing custom data on chunks.
 * Note: NBT serialization is handled by ChunkSerializerMixin.
 */
@Mixin(LevelChunk.class)
public class LevelChunkMixin implements ChunkPersistentData {

    @Unique
    private CompoundTag betterEcology$persistentData;

    @Override
    public CompoundTag getOrCreatePersistentData() {
        if (this.betterEcology$persistentData == null) {
            this.betterEcology$persistentData = new CompoundTag();
        }
        return this.betterEcology$persistentData;
    }
}
