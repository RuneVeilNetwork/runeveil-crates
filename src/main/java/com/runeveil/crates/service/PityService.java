package com.runeveil.crates.service;

import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.storage.PlayerPityStorage;
import com.runeveil.crates.util.RarityDefinitions;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.Map;

public final class PityService {
    private PityService() {
    }

    public static int getPullsSinceRarePlus(CrateConfigManager manager, UUID playerId, String crateId) {
        PlayerPityStorage storage = manager.getPityStorage();
        Map<String, Integer> crateCounts = storage.players.get(playerId.toString());
        if (crateCounts == null) {
            return 0;
        }
        return Math.max(0, crateCounts.getOrDefault(CrateConfigManager.normalize(crateId), 0));
    }

    public static boolean shouldForcePity(CrateConfigManager manager, ServerPlayer player, String crateId) {
        SettingsConfig.PitySettings pity = manager.getSettings().pity;
        if (pity == null || !pity.enabled) {
            return false;
        }
        var crate = manager.getCrate(crateId);
        int threshold = crate != null && crate.pityPullsWithoutRarePlus != null
                ? crate.pityPullsWithoutRarePlus : pity.pullsWithoutRarePlus;
        return getPullsSinceRarePlus(manager, player.getUUID(), crateId) >= Math.max(1, threshold);
    }

    public static void recordRoll(CrateConfigManager manager, ServerPlayer player, String crateId, String rarity) {
        SettingsConfig.PitySettings pity = manager.getSettings().pity;
        if (pity == null || !pity.enabled) {
            return;
        }

        String playerKey = player.getUUID().toString();
        String crateKey = CrateConfigManager.normalize(crateId);
        PlayerPityStorage storage = manager.getPityStorage();
        Map<String, Integer> crateCounts = storage.players.computeIfAbsent(playerKey, ignored -> new java.util.LinkedHashMap<>());

        if (RarityDefinitions.isRareOrHigher(rarity)) {
            crateCounts.put(crateKey, 0);
        } else {
            crateCounts.put(crateKey, getPullsSinceRarePlus(manager, player.getUUID(), crateId) + 1);
        }
        manager.markPityDirty();
    }
}
