package com.runeveil.crates.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateHologramManagerTest {
    @Test
    void matchesPositiveAndNegativeBlockCoordinatesToTheirChunk() {
        assertTrue(CrateHologramManager.isInChunk(new BlockPos(31, 64, 47), new ChunkPos(1, 2)));
        assertTrue(CrateHologramManager.isInChunk(new BlockPos(-1, 64, -16), new ChunkPos(-1, -1)));
        assertFalse(CrateHologramManager.isInChunk(new BlockPos(32, 64, 47), new ChunkPos(1, 2)));
    }
}
