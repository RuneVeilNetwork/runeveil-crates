package com.runeveil.crates.service;

import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.util.RarityDefinitions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RewardProbabilityService {
    private RewardProbabilityService() {}

    public static Map<RewardEntry, Double> probabilities(CrateDefinition crate, SettingsConfig settings) {
        Map<RewardEntry, Double> result = new LinkedHashMap<>();
        if (crate == null || crate.rewards == null || crate.rewards.isEmpty()) return result;
        Map<String, Integer> odds = settings == null || settings.rarityRoll == null ? Map.of() : settings.rarityRoll.odds;
        int tierTotal = 0;
        Map<String, Integer> activeTierWeights = new LinkedHashMap<>();
        for (String rarity : RarityDefinitions.ALL) {
            boolean present = crate.rewards.stream().anyMatch(r -> RarityDefinitions.normalize(r.rarity).equals(rarity));
            int weight = present ? Math.max(0, odds == null ? 0 : odds.getOrDefault(rarity, 0)) : 0;
            activeTierWeights.put(rarity, weight);
            tierTotal += weight;
        }
        if (tierTotal <= 0) return result;
        for (String rarity : RarityDefinitions.ALL) {
            final String tier = rarity;
            List<RewardEntry> pool = crate.rewards.stream().filter(r -> RarityDefinitions.normalize(r.rarity).equals(tier)).toList();
            int rewardTotal = pool.stream().mapToInt(r -> Math.max(1, r.weight)).sum();
            if (rewardTotal == 0) continue;
            double tierChance = activeTierWeights.get(rarity) / (double) tierTotal;
            for (RewardEntry reward : pool) result.put(reward, tierChance * Math.max(1, reward.weight) / rewardTotal);
        }
        return result;
    }
}
