package com.runeveil.crates.visual;

import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.config.SettingsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CrateRollAnimation {
    private static final List<ActiveRoll> ACTIVE = new CopyOnWriteArrayList<>();

    private CrateRollAnimation() {
    }

    public static void start(ServerLevel level, BlockPos pos, CrateDefinition crate, RewardEntry winner,
                             ServerPlayer player, CrateConfigManager manager, Runnable onComplete) {
        SettingsConfig.RollAnimation settings = manager.getSettings().rollAnimation;
        boolean enabled = crate.rollAnimationEnabled != null ? crate.rollAnimationEnabled : settings != null && settings.enabled;
        if (settings == null || !enabled || crate.rewards == null || crate.rewards.isEmpty()) {
            CrateHologramManager.showCrateLabel(level, pos, crate, manager.getSettings());
            onComplete.run();
            return;
        }

        List<RewardEntry> pool = new ArrayList<>(crate.rewards);
        if (pool.isEmpty()) {
            CrateHologramManager.showCrateLabel(level, pos, crate, manager.getSettings());
            onComplete.run();
            return;
        }

        List<RewardEntry> sequence = buildSequence(pool, settings.maximumSteps);
        ActiveRoll roll = new ActiveRoll(level, pos, crate, winner, player, manager, sequence, settings, onComplete);
        ACTIVE.add(roll);
    }

    public static void tick() {
        Iterator<ActiveRoll> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActiveRoll roll = iterator.next();
            if (roll.tick()) {
                ACTIVE.remove(roll);
            }
        }
    }

    public static boolean isRollingAt(BlockPos pos, ServerLevel level) {
        for (ActiveRoll roll : ACTIVE) {
            if (roll.level == level && roll.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    static List<RewardEntry> buildSequence(List<RewardEntry> pool, int maximumSteps) {
        List<RewardEntry> sequence = new ArrayList<>();
        int previewCount = Math.max(1, maximumSteps);
        while (sequence.size() < previewCount) {
            List<RewardEntry> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            int remaining = previewCount - sequence.size();
            sequence.addAll(shuffled.subList(0, Math.min(remaining, shuffled.size())));
        }
        return sequence;
    }

    private static final class ActiveRoll {
        private final ServerLevel level;
        private final BlockPos pos;
        private final CrateDefinition crate;
        private final RewardEntry winner;
        private final ServerPlayer player;
        private final CrateConfigManager manager;
        private final List<RewardEntry> sequence;
        private final SettingsConfig.RollAnimation settings;
        private final Runnable onComplete;

        private int tickCounter = 0;
        private int stepCounter = 0;
        private int holdTicks = 0;

        private ActiveRoll(ServerLevel level, BlockPos pos, CrateDefinition crate, RewardEntry winner, ServerPlayer player,
                           CrateConfigManager manager, List<RewardEntry> sequence, SettingsConfig.RollAnimation settings,
                           Runnable onComplete) {
            this.level = level;
            this.pos = pos;
            this.crate = crate;
            this.winner = winner;
            this.player = player;
            this.manager = manager;
            this.sequence = sequence;
            this.settings = settings;
            this.onComplete = onComplete;
            updateDisplay(sequence.get(0));
        }

        private boolean tick() {
            tickCounter++;
            if (holdTicks > 0) {
                holdTicks--;
                if (holdTicks == 0) {
                    finish();
                    return true;
                }
                return false;
            }

            if (tickCounter % Math.max(1, settings.ticksPerStep) != 0) {
                return false;
            }

            stepCounter++;
            if (stepCounter >= sequence.size()) {
                updateDisplay(winner);
                holdTicks = Math.max(1, settings.finalHoldTicks);
                return false;
            }

            updateDisplay(sequence.get(stepCounter));
            return false;
        }

        private void updateDisplay(RewardEntry reward) {
            Component text = Component.empty()
                    .append(CrateVisuals.rollingLabel(reward))
                    .append(Component.literal(" ✦").withStyle(net.minecraft.ChatFormatting.WHITE));
            CrateHologramManager.showText(level, pos, text, manager.getSettings());
        }

        private void finish() {
            onComplete.run();
            CrateHologramManager.showCrateLabel(level, pos, crate, manager.getSettings());
        }
    }
}
