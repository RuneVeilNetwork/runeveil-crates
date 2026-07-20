package com.runeveil.crates.service;

import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.config.SettingsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardProbabilityServiceTest {
    @Test
    void combinesTierOddsAndRewardWeights() {
        SettingsConfig settings = new SettingsConfig();
        settings.rarityRoll.odds = Map.of("common", 75, "rare", 25);
        RewardEntry commonA = reward("a", "common", 1);
        RewardEntry commonB = reward("b", "common", 2);
        RewardEntry rare = reward("rare", "rare", 1);
        CrateDefinition crate = new CrateDefinition();
        crate.rewards = List.of(commonA, commonB, rare);
        Map<RewardEntry, Double> result = RewardProbabilityService.probabilities(crate, settings);
        assertEquals(0.25, result.get(commonA), 0.000001);
        assertEquals(0.50, result.get(commonB), 0.000001);
        assertEquals(0.25, result.get(rare), 0.000001);
    }

    @Test
    void excludesConfiguredTiersThatHaveNoRewards() {
        SettingsConfig settings = new SettingsConfig();
        settings.rarityRoll.odds = Map.of("common", 50, "legendary", 50);
        RewardEntry common = reward("only", "common", 1);
        CrateDefinition crate = new CrateDefinition();
        crate.rewards = List.of(common);
        Map<RewardEntry, Double> result = RewardProbabilityService.probabilities(crate, settings);
        assertEquals(1.0, result.get(common), 0.000001);
        assertTrue(result.containsKey(common));
    }

    private static RewardEntry reward(String id, String rarity, int weight) {
        RewardEntry reward = new RewardEntry();
        reward.id = id;
        reward.displayName = id;
        reward.rarity = rarity;
        reward.weight = weight;
        return reward;
    }
}
