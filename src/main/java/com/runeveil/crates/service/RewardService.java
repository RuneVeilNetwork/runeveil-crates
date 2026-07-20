package com.runeveil.crates.service;

import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.config.ConfigValidator;
import com.runeveil.crates.util.MessageUtil;
import com.runeveil.crates.util.RarityDefinitions;
import com.runeveil.crates.util.RewardItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class RewardService {
    private RewardService() {
    }

    public static RewardEntry rollReward(CrateDefinition crate, ServerPlayer player, CrateConfigManager configManager) {
        List<RewardEntry> rewards = crate.rewards;
        if (rewards == null || rewards.isEmpty()) {
            return null;
        }

        SettingsConfig settings = configManager.getSettings();
        boolean forcePity = PityService.shouldForcePity(configManager, player, crate.id);
        String rarity = rollRarity(crate, settings, forcePity);
        RewardEntry reward = rollWithinRarity(crate, rarity);
        if (reward == null) {
            reward = rollWithinRarity(crate, pickAnyRarityWithRewards(crate));
        }
        if (reward == null) {
            reward = rewards.get(0);
        }

        PityService.recordRoll(configManager, player, crate.id, reward.rarity);
        return reward;
    }

    public static void grantReward(ServerPlayer player, CrateDefinition crate, RewardEntry reward, CrateConfigManager configManager) {
        if (reward == null) {
            return;
        }
        if ("command".equalsIgnoreCase(reward.type)) {
            for (String command : reward.commands) {
                String parsed = command.replace("{player}", player.getGameProfile().getName());
                player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack().withSuppressedOutput(),
                        parsed.startsWith("/") ? parsed.substring(1) : parsed
                );
            }
        } else {
            ItemStack stack = RewardItems.createStack(reward);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    String policy = inventoryPolicy(crate, configManager.getSettings());
                    if ("drop".equals(policy)) player.drop(stack, false);
                }
            }
        }

        SettingsConfig settings = configManager.getSettings();
        boolean broadcastRare = crate.broadcastRareRewards != null ? crate.broadcastRareRewards : settings.broadcastRareRewards;
        if (reward.broadcast || (broadcastRare && RarityDefinitions.isRareOrHigher(reward.rarity))) {
            String rewardName = resolveRewardName(reward);
            String template = settings.messages.getOrDefault("rewardBroadcast", "&6{player} &ewon &b{reward} &efrom a &d{crate}&e!");
            player.getServer().getPlayerList().broadcastSystemMessage(
                    MessageUtil.format(template, "player", player.getGameProfile().getName(), "reward", rewardName, "crate", crate.displayName),
                    false
            );
        }
    }

    public static boolean canGrant(ServerPlayer player, CrateDefinition crate, RewardEntry reward, CrateConfigManager manager) {
        if (reward == null || "command".equalsIgnoreCase(reward.type)) return true;
        if (!"deny".equals(inventoryPolicy(crate, manager.getSettings()))) return true;
        ItemStack wanted = RewardItems.previewStack(reward).copy();
        wanted.setCount(Math.max(1, reward.maxCount));
        if (wanted.isEmpty()) return true;
        int remaining = wanted.getCount();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(existing, wanted)) remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static String inventoryPolicy(CrateDefinition crate, SettingsConfig settings) {
        String perCrate = ConfigValidator.normalizePolicy(crate.inventoryFullPolicy);
        return "inherit".equals(perCrate) ? ConfigValidator.normalizePolicy(settings.inventoryFullPolicy) : perCrate;
    }

    private static String rollRarity(CrateDefinition crate, SettingsConfig settings, boolean forcePity) {
        Map<String, Integer> odds = normalizedOdds(settings.rarityRoll == null ? null : settings.rarityRoll.odds);
        if (forcePity) {
            Map<String, Integer> rarePlusOdds = filterOdds(odds, RarityDefinitions.ALL.stream()
                    .filter(RarityDefinitions::isRareOrHigher)
                    .toList());
            String pityRarity = weightedRarityPick(crate, rarePlusOdds);
            if (pityRarity != null) {
                return pityRarity;
            }
        }

        String rarity = weightedRarityPick(crate, odds);
        return rarity != null ? rarity : pickAnyRarityWithRewards(crate);
    }

    private static RewardEntry rollWithinRarity(CrateDefinition crate, String rarity) {
        List<RewardEntry> pool = rewardsForRarity(crate, rarity);
        if (pool.isEmpty()) {
            return null;
        }
        return weightedRewardPick(pool);
    }

    private static String weightedRarityPick(CrateDefinition crate, Map<String, Integer> odds) {
        List<String> available = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (String rarity : RarityDefinitions.ALL) {
            if (!rewardsForRarity(crate, rarity).isEmpty() && odds.getOrDefault(rarity, 0) > 0) {
                available.add(rarity);
                weights.add(odds.get(rarity));
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return available.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int current = 0;
        for (int i = 0; i < available.size(); i++) {
            current += weights.get(i);
            if (roll < current) {
                return available.get(i);
            }
        }
        return available.get(available.size() - 1);
    }

    private static RewardEntry weightedRewardPick(List<RewardEntry> pool) {
        int totalWeight = pool.stream().mapToInt(RewardService::weight).sum();
        if (totalWeight <= 0) {
            return pool.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (RewardEntry reward : pool) {
            current += weight(reward);
            if (roll < current) {
                return reward;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static List<RewardEntry> rewardsForRarity(CrateDefinition crate, String rarity) {
        String normalized = RarityDefinitions.normalize(rarity);
        List<RewardEntry> pool = new ArrayList<>();
        if (crate.rewards == null) {
            return pool;
        }
        for (RewardEntry reward : crate.rewards) {
            if (RarityDefinitions.normalize(reward.rarity).equals(normalized)) {
                pool.add(reward);
            }
        }
        return pool;
    }

    private static String pickAnyRarityWithRewards(CrateDefinition crate) {
        for (String rarity : RarityDefinitions.ALL) {
            if (!rewardsForRarity(crate, rarity).isEmpty()) {
                return rarity;
            }
        }
        return "common";
    }

    private static Map<String, Integer> normalizedOdds(Map<String, Integer> configured) {
        Map<String, Integer> odds = new LinkedHashMap<>();
        for (String rarity : RarityDefinitions.ALL) {
            odds.put(rarity, 0);
        }
        if (configured != null) {
            configured.forEach((key, value) -> odds.put(RarityDefinitions.normalize(key), Math.max(0, value)));
        }
        if (odds.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
            odds.put("common", 45);
            odds.put("uncommon", 30);
            odds.put("rare", 15);
            odds.put("epic", 8);
            odds.put("legendary", 2);
        }
        return odds;
    }

    private static Map<String, Integer> filterOdds(Map<String, Integer> odds, List<String> rarities) {
        Map<String, Integer> filtered = new LinkedHashMap<>();
        for (String rarity : rarities) {
            filtered.put(rarity, odds.getOrDefault(rarity, 0));
        }
        return filtered;
    }

    private static String resolveRewardName(RewardEntry reward) {
        if ("item".equalsIgnoreCase(reward.type) && RewardItems.hasStoredStackData(reward)) {
            return RewardItems.previewStack(reward).getHoverName().getString();
        }
        if (reward.displayName != null && !reward.displayName.isBlank()) {
            return reward.displayName;
        }
        return reward.id;
    }

    private static int weight(RewardEntry reward) {
        return Math.max(1, reward.weight);
    }
}
