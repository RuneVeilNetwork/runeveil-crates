package com.runeveil.crates.visual;

import com.runeveil.crates.config.RewardEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrateRollAnimationTest {
    @Test
    void previewSequenceUsesExactMaximumStepCount() {
        RewardEntry first = new RewardEntry();
        RewardEntry second = new RewardEntry();
        List<RewardEntry> pool = List.of(first, second);

        assertEquals(1, CrateRollAnimation.buildSequence(pool, 1).size());
        assertEquals(7, CrateRollAnimation.buildSequence(pool, 7).size());
        assertEquals(24, CrateRollAnimation.buildSequence(pool, 24).size());
    }
}
