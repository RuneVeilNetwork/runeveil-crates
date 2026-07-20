package com.runeveil.crates.integration;

import com.runeveil.crates.RuneveilCratesMod;
import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.service.VoteRewardService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

public final class VotifierIntegration {
    private static final String UBERSWE_MOD_ID = "votifier";
    private static Object hookedStorage;

    private VotifierIntegration() {
    }

    public static void initialize(MinecraftServer server, CrateConfigManager configManager) {
        SettingsConfig.VotifierIntegration settings = configManager.getSettings().votifier;
        if (settings == null || !settings.enabled) {
            RuneveilCratesMod.LOGGER.info("Vote key rewards are disabled in settings.json (votifier.enabled = false).");
            return;
        }

        server.execute(() -> tryHook(server, configManager, false));
    }

    private static void tryHook(MinecraftServer server, CrateConfigManager configManager, boolean retried) {
        if (hookUbersweVotifier(server, configManager)) {
            RuneveilCratesMod.LOGGER.info("Connected to uberswe Votifier for automatic vote key rewards.");
            return;
        }

        if (!retried) {
            server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 40, () -> tryHook(server, configManager, true)));
            return;
        }

        SettingsConfig.VotifierIntegration settings = configManager.getSettings().votifier;
        if (ModList.get().isLoaded(UBERSWE_MOD_ID)) {
            RuneveilCratesMod.LOGGER.warn(
                    "Votifier mod is installed but RuneVeil Crates could not hook into it. " +
                            "Votes must be handled manually with /crate givekey {}.",
                    settings.voteKeyId
            );
        } else {
            RuneveilCratesMod.LOGGER.warn(
                    "No compatible Votifier mod detected. Install uberswe/votifier for automatic vote keys, " +
                            "or give keys manually with /crate givekey {}.",
                    settings.voteKeyId
            );
        }
    }

    public static void processUberswePendingVotes(ServerPlayer player, CrateConfigManager configManager) {
        SettingsConfig.VotifierIntegration settings = configManager.getSettings().votifier;
        if (settings == null || !settings.enabled || !ModList.get().isLoaded(UBERSWE_MOD_ID)) {
            return;
        }

        try {
            Object voteStorage = resolveUbersweVoteStorage();
            if (voteStorage == null) {
                return;
            }

            Method getAndRemove = voteStorage.getClass().getMethod("getAndRemoveVotes", String.class);
            @SuppressWarnings("unchecked")
            List<Object> votes = (List<Object>) getAndRemove.invoke(voteStorage, player.getGameProfile().getName());
            if (votes == null || votes.isEmpty()) {
                return;
            }

            VoteRewardService.grantKeys(player, configManager, settings, votes.size());
        } catch (ReflectiveOperationException e) {
            RuneveilCratesMod.LOGGER.warn("Failed to process pending uberswe votes for {}", player.getGameProfile().getName(), e);
        }
    }

    private static boolean hookUbersweVotifier(MinecraftServer server, CrateConfigManager configManager) {
        if (!ModList.get().isLoaded(UBERSWE_MOD_ID)) {
            return false;
        }

        try {
            Object modInstance = ModList.get().getModContainerById(UBERSWE_MOD_ID)
                    .map(container -> container.getMod())
                    .orElse(null);
            if (modInstance == null) {
                return false;
            }

            Field serverField = modInstance.getClass().getDeclaredField("votifierServer");
            serverField.setAccessible(true);
            Object votifierServer = serverField.get(modInstance);
            if (votifierServer == null) {
                return false;
            }

            Method getStorage = votifierServer.getClass().getMethod("getVoteStorage");
            Object voteStorage = getStorage.invoke(votifierServer);
            if (voteStorage == null) {
                return false;
            }
            if (voteStorage == hookedStorage) return true;

            Consumer<Object> existing = readExistingCallback(voteStorage);
            Consumer<Object> replacement = vote -> {
                if (existing != null) existing.accept(vote);
                String username = readVoteUsername(vote);
                if (username == null || username.isBlank()) {
                    return;
                }
                server.execute(() -> handleIncomingVote(server, configManager, username));
            };

            Method setCallback = voteStorage.getClass().getMethod("setVoteCallback", Consumer.class);
            setCallback.invoke(voteStorage, replacement);
            hookedStorage = voteStorage;
            return true;
        } catch (ReflectiveOperationException e) {
            RuneveilCratesMod.LOGGER.warn("Failed to hook uberswe Votifier", e);
            return false;
        }
    }

    private static void handleIncomingVote(MinecraftServer server, CrateConfigManager configManager, String username) {
        SettingsConfig.VotifierIntegration settings = configManager.getSettings().votifier;
        if (settings == null || !settings.enabled) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player != null) {
            int pendingVotes = drainUberswePendingVotes(username);
            VoteRewardService.grantKeys(player, configManager, settings, Math.max(1, pendingVotes));
            return;
        }

        if (settings.logOfflineVotes) {
            RuneveilCratesMod.LOGGER.info(
                    "Offline vote received for {} ({} key(s) will be granted on next login).",
                    username,
                    Math.max(1, settings.keysPerVote)
            );
        }
    }

    private static int drainUberswePendingVotes(String playerName) {
        try {
            Object voteStorage = resolveUbersweVoteStorage();
            if (voteStorage == null) {
                return 0;
            }
            Method getAndRemove = voteStorage.getClass().getMethod("getAndRemoveVotes", String.class);
            Object value = getAndRemove.invoke(voteStorage, playerName);
            return value instanceof List<?> votes ? votes.size() : 0;
        } catch (ReflectiveOperationException e) {
            RuneveilCratesMod.LOGGER.warn("Failed to clear pending uberswe votes for {}", playerName, e);
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> readExistingCallback(Object voteStorage) {
        for (String name : List.of("voteCallback", "callback")) {
            try {
                Field field = voteStorage.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(voteStorage);
                if (value instanceof Consumer<?> consumer) return (Consumer<Object>) consumer;
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static Object resolveUbersweVoteStorage() throws ReflectiveOperationException {
        Object modInstance = ModList.get().getModContainerById(UBERSWE_MOD_ID)
                .map(container -> container.getMod())
                .orElse(null);
        if (modInstance == null) {
            return null;
        }

        Field serverField = modInstance.getClass().getDeclaredField("votifierServer");
        serverField.setAccessible(true);
        Object votifierServer = serverField.get(modInstance);
        if (votifierServer == null) {
            return null;
        }

        Method getStorage = votifierServer.getClass().getMethod("getVoteStorage");
        return getStorage.invoke(votifierServer);
    }

    private static String readVoteUsername(Object vote) {
        if (vote == null) {
            return null;
        }
        try {
            Method getUsername = vote.getClass().getMethod("getUsername");
            Object value = getUsername.invoke(vote);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
