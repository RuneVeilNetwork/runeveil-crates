package com.runeveil.crates.service;

import com.runeveil.crates.RuneveilCratesMod;
import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.KeyConfig;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.util.MessageUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class VoteRewardService {
    private VoteRewardService() {
    }

    public static void grantKeys(ServerPlayer player, CrateConfigManager configManager,
                                 SettingsConfig.VotifierIntegration settings, int voteCount) {
        if (voteCount <= 0) {
            return;
        }

        int totalKeys = Math.max(1, settings.keysPerVote) * voteCount;
        KeyConfig keyConfig = configManager.getKey(settings.voteKeyId);
        if (keyConfig == null) {
            RuneveilCratesMod.LOGGER.warn("Vote reward skipped: unknown key id '{}'", settings.voteKeyId);
            return;
        }

        ItemStack stack = KeyService.createKey(configManager, settings.voteKeyId, totalKeys);
        if (stack.isEmpty()) {
            RuneveilCratesMod.LOGGER.warn("Vote reward skipped: failed to create key '{}'", settings.voteKeyId);
            return;
        }

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        runExtraCommands(player.getServer(), player.getGameProfile().getName(), settings, voteCount);

        String template = configManager.getSettings().messages.getOrDefault(
                "voteReward",
                "&aThanks for voting! You received &6{amount} &r{key}&a."
        );
        player.sendSystemMessage(MessageUtil.format(template,
                "amount", String.valueOf(totalKeys),
                "key", keyConfig.displayName,
                "player", player.getGameProfile().getName()
        ));

        RuneveilCratesMod.LOGGER.info("Granted {} vote key(s) to {}", totalKeys, player.getGameProfile().getName());
    }

    private static void runExtraCommands(MinecraftServer server, String playerName,
                                         SettingsConfig.VotifierIntegration settings, int voteCount) {
        if (settings.extraVoteCommands == null || settings.extraVoteCommands.isEmpty()) {
            return;
        }

        for (int i = 0; i < voteCount; i++) {
            for (String command : settings.extraVoteCommands) {
                if (command == null || command.isBlank()) {
                    continue;
                }
                String cmd = command.replace("{player}", playerName);
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
            }
        }
    }

    private static SettingsConfig.VotifierIntegration voteSettings(CrateConfigManager configManager) {
        if (configManager == null) {
            return null;
        }
        SettingsConfig.VotifierIntegration settings = configManager.getSettings().votifier;
        if (settings == null || !settings.enabled) {
            return null;
        }
        return settings;
    }
}
