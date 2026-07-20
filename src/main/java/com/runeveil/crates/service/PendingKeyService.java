package com.runeveil.crates.service;

import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.KeyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PendingKeyService {
    private PendingKeyService() {}

    public static boolean queue(CrateConfigManager manager, UUID playerId, String keyId, int amount) {
        if (manager.getKey(keyId) == null || amount < 1) return false;
        Map<String, Integer> keys = manager.getPendingKeys().players.computeIfAbsent(playerId.toString(), ignored -> new LinkedHashMap<>());
        keys.merge(CrateConfigManager.normalize(keyId), amount, Integer::sum);
        manager.savePendingKeys();
        return true;
    }

    public static int deliver(ServerPlayer player, CrateConfigManager manager) {
        Map<String, Integer> pending = manager.getPendingKeys().players.remove(player.getUUID().toString());
        if (pending == null || pending.isEmpty()) return 0;
        int delivered = 0;
        for (Map.Entry<String, Integer> entry : pending.entrySet()) {
            KeyConfig key = manager.getKey(entry.getKey());
            if (key == null) continue;
            int remaining = entry.getValue();
            while (remaining > 0) {
                int batch = Math.min(64, remaining);
                ItemStack stack = KeyService.createKey(manager, entry.getKey(), batch);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                remaining -= batch;
                delivered += batch;
            }
        }
        manager.savePendingKeys();
        if (delivered > 0) player.sendSystemMessage(Component.literal("Delivered " + delivered + " pending crate key(s).").withStyle(ChatFormatting.GREEN));
        return delivered;
    }
}
