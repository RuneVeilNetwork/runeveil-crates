package com.runeveil.crates.visual;

import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.util.RarityDefinitions;
import com.runeveil.crates.util.RewardItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class CrateVisuals {
    private CrateVisuals() {
    }

    public static Component crateLabel(CrateDefinition crate) {
        ChatFormatting color = RarityDefinitions.crateColor(crate.id);
        String text = crate.displayName == null || crate.displayName.isBlank() ? crate.id : crate.displayName;
        return Component.literal(stripColorCodes(text)).withStyle(color, ChatFormatting.BOLD);
    }

    public static Component rewardLabel(RewardEntry reward) {
        return Component.literal(stripColorCodes(resolveRewardName(reward)))
                .withStyle(RarityDefinitions.color(reward.rarity), ChatFormatting.BOLD);
    }

    public static Component rollingLabel(RewardEntry reward) {
        return Component.literal(stripColorCodes(resolveRewardName(reward)))
                .withStyle(RarityDefinitions.color(reward.rarity), ChatFormatting.BOLD);
    }

    private static String resolveRewardName(RewardEntry reward) {
        if ("item".equalsIgnoreCase(reward.type) && RewardItems.hasStoredStackData(reward)) {
            return RewardItems.previewStack(reward).getHoverName().getString();
        }
        if (reward.displayName != null && !reward.displayName.isBlank()) {
            return reward.displayName;
        }
        return reward.id == null ? "Reward" : reward.id;
    }

    private static String stripColorCodes(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("(?i)&[0-9a-fk-or]", "").replaceAll("(?i)\u00A7[0-9a-fk-or]", "");
    }
}
