package com.runeveil.crates.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettingsConfig {
    public boolean consumeKeyOnOpen = true;
    public int openCooldownSeconds = 0;
    public boolean broadcastRareRewards = true;
    public int maxLookDistance = 6;
    public List<String> allowedBlocks = List.of(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel",
            "minecraft:shulker_box",
            "minecraft:white_shulker_box",
            "minecraft:orange_shulker_box",
            "minecraft:magenta_shulker_box",
            "minecraft:light_blue_shulker_box",
            "minecraft:yellow_shulker_box",
            "minecraft:lime_shulker_box",
            "minecraft:pink_shulker_box",
            "minecraft:gray_shulker_box",
            "minecraft:light_gray_shulker_box",
            "minecraft:cyan_shulker_box",
            "minecraft:purple_shulker_box",
            "minecraft:blue_shulker_box",
            "minecraft:brown_shulker_box",
            "minecraft:green_shulker_box",
            "minecraft:red_shulker_box",
            "minecraft:black_shulker_box"
    );
    public Map<String, String> messages = defaultMessages();
    public VotifierIntegration votifier = new VotifierIntegration();

    private static Map<String, String> defaultMessages() {
        Map<String, String> messages = new LinkedHashMap<>();
        messages.put("needKey", "You need a {key} to open this crate.");
        messages.put("wrongKey", "That key does not work on this crate.");
        messages.put("opened", "You opened the {crate} crate!");
        messages.put("converted", "Converted block to {crate} crate.");
        messages.put("unconverted", "Removed crate binding from this block.");
        messages.put("notCrateBlock", "You must look at a chest, barrel, or shulker box.");
        messages.put("alreadyCrate", "This block is already a crate.");
        messages.put("notCrate", "This block is not registered as a crate.");
        messages.put("cooldown", "Please wait {seconds}s before opening another crate.");
        messages.put("rewardBroadcast", "&6{player} &ewon &b{reward} &efrom a &d{crate}&e!");
        messages.put("giveKey", "&aGave &6{amount} &r{key}&a to &e{player}&a.");
        messages.put("editorOpened", "&aOpened loot table editor for &e{crate}&a.");
        messages.put("editorNoPermission", "&cUse a crate key to open this crate.");
        messages.put("voteReward", "&aThanks for voting! You received &6{amount} &r{key}&a.");
        return messages;
    }

    public int editorPermissionLevel = 2;
    public int adminPermissionLevel = 2;
    public String inventoryFullPolicy = "drop";
    public HologramSettings hologram = new HologramSettings();
    public RollAnimation rollAnimation = new RollAnimation();
    public RarityRollSettings rarityRoll = new RarityRollSettings();
    public PitySettings pity = new PitySettings();
    public static class HologramSettings {
        public double yOffset = 1.35D;
        public boolean removeOnShutdown = false;
        public boolean removeOrphansOnStartup = true;
    }

    public static class RollAnimation {
        public boolean enabled = true;
        public int ticksPerStep = 2;
        public int minimumSteps = 24;
        public int finalHoldTicks = 20;
    }

    public static class RarityRollSettings {
        public Map<String, Integer> odds = defaultRarityOdds();

        private static Map<String, Integer> defaultRarityOdds() {
            Map<String, Integer> odds = new LinkedHashMap<>();
            odds.put("common", 45);
            odds.put("uncommon", 30);
            odds.put("rare", 15);
            odds.put("epic", 8);
            odds.put("legendary", 2);
            return odds;
        }
    }

    public static class PitySettings {
        public boolean enabled = true;
        public int pullsWithoutRarePlus = 20;
    }

    public static class VotifierIntegration {
        public boolean enabled = true;
        public String voteKeyId = "vote";
        public int keysPerVote = 1;
        public boolean logOfflineVotes = true;
        public List<String> extraVoteCommands = new ArrayList<>();
    }
}
