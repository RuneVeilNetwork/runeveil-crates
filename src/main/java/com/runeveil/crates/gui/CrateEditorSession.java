package com.runeveil.crates.gui;

import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.util.RarityDefinitions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class CrateEditorSession {
    public static final int REWARDS_PER_PAGE = 36;

    public final BlockPos cratePos;
    public final ResourceKey<Level> dimension;
    public final String crateId;
    public final CrateDefinition workingCopy;
    public String currentRarity = "common";
    public int currentPage = 0;
    public RewardEntry selectedReward = null;

    public CrateEditorSession(BlockPos cratePos, ResourceKey<Level> dimension, String crateId, CrateDefinition source) {
        this.cratePos = cratePos;
        this.dimension = dimension;
        this.crateId = crateId;
        this.workingCopy = copy(source);
    }

    public List<RewardEntry> rewardsForRarity() {
        String rarity = RarityDefinitions.normalize(currentRarity);
        List<RewardEntry> filtered = new ArrayList<>();
        if (workingCopy.rewards == null) {
            return filtered;
        }
        for (RewardEntry reward : workingCopy.rewards) {
            if (RarityDefinitions.normalize(reward.rarity).equals(rarity)) {
                filtered.add(reward);
            }
        }
        return filtered;
    }

    public int totalPages() {
        int count = rewardsForRarity().size();
        return Math.max(1, (count + REWARDS_PER_PAGE - 1) / REWARDS_PER_PAGE);
    }

    public List<RewardEntry> pageRewards() {
        List<RewardEntry> all = rewardsForRarity();
        int start = currentPage * REWARDS_PER_PAGE;
        if (start >= all.size()) {
            return List.of();
        }
        return all.subList(start, Math.min(start + REWARDS_PER_PAGE, all.size()));
    }

    public void clampPage() {
        int maxPage = totalPages() - 1;
        if (currentPage > maxPage) {
            currentPage = maxPage;
        }
        if (currentPage < 0) {
            currentPage = 0;
        }
    }

    private static CrateDefinition copy(CrateDefinition source) {
        CrateDefinition copy = new CrateDefinition();
        copy.id = source.id;
        copy.displayName = source.displayName;
        copy.requiredKey = source.requiredKey;
        copy.cooldownSeconds = source.cooldownSeconds;
        copy.rewards = new ArrayList<>();
        if (source.rewards != null) {
            for (RewardEntry reward : source.rewards) {
                copy.rewards.add(copyReward(reward));
            }
        }
        return copy;
    }

    private static RewardEntry copyReward(RewardEntry reward) {
        RewardEntry copy = new RewardEntry();
        copy.id = reward.id;
        copy.type = reward.type;
        copy.item = reward.item;
        copy.minCount = reward.minCount;
        copy.maxCount = reward.maxCount;
        copy.weight = reward.weight;
        copy.rarity = reward.rarity;
        copy.broadcast = reward.broadcast;
        copy.displayName = reward.displayName;
        copy.itemStackNbt = reward.itemStackNbt;
        copy.commands = reward.commands == null ? new ArrayList<>() : new ArrayList<>(reward.commands);
        return copy;
    }
}
