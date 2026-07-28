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

    public static int giveOrQueue(ServerPlayer player, CrateConfigManager manager, String keyId, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            ItemStack stack = KeyService.createKey(manager, keyId, batch);
            if (stack.isEmpty()) break;
            player.getInventory().add(stack);
            int inserted = batch - stack.getCount();
            remaining -= inserted;
            if (!stack.isEmpty()) break;
        }
        if (remaining > 0 && queue(manager, player.getUUID(), keyId, remaining)) {
            player.sendSystemMessage(Component.literal("Your inventory is full. Make space, then run /crate accept to receive " + remaining + " pending key(s).").withStyle(ChatFormatting.YELLOW));
        }
        return amount - remaining;
    }

    public static void notifyPending(ServerPlayer player, CrateConfigManager manager) {
        Map<String, Integer> pending = manager.getPendingKeys().players.get(player.getUUID().toString());
        if (pending != null && !pending.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have pending crate keys. Make inventory space, then run /crate accept.").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static int accept(ServerPlayer player, CrateConfigManager manager) {
        if (manager == null) return 0;
        int delivered = deliver(player, manager);
        if (delivered == 0) {
            player.sendSystemMessage(Component.literal("No crate keys could be accepted. Make sure you have inventory space.").withStyle(ChatFormatting.YELLOW));
        }
        return delivered;
    }

    public static int deliver(ServerPlayer player, CrateConfigManager manager) {
        Map<String, Integer> pending = manager.getPendingKeys().players.get(player.getUUID().toString());
        if (pending == null || pending.isEmpty()) return 0;
        int delivered = 0;
        Map<String, Integer> stillPending = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : pending.entrySet()) {
            KeyConfig key = manager.getKey(entry.getKey());
            if (key == null) { stillPending.put(entry.getKey(), entry.getValue()); continue; }
            int remaining = entry.getValue();
            while (remaining > 0) {
                int batch = Math.min(64, remaining);
                ItemStack stack = KeyService.createKey(manager, entry.getKey(), batch);
                player.getInventory().add(stack);
                int inserted = batch - stack.getCount();
                remaining -= inserted;
                delivered += inserted;
                if (!stack.isEmpty()) break;
            }
            if (remaining > 0) stillPending.put(entry.getKey(), remaining);
        }
        if (stillPending.isEmpty()) manager.getPendingKeys().players.remove(player.getUUID().toString());
        else manager.getPendingKeys().players.put(player.getUUID().toString(), stillPending);
        manager.savePendingKeys();
        if (delivered > 0) player.sendSystemMessage(Component.literal("Delivered " + delivered + " pending crate key(s).").withStyle(ChatFormatting.GREEN));
        if (!stillPending.isEmpty()) player.sendSystemMessage(Component.literal("Some keys are still pending. Make more space and run /crate accept again.").withStyle(ChatFormatting.YELLOW));
        return delivered;
    }
}
