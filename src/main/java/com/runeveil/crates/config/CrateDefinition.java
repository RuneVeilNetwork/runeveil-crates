package com.runeveil.crates.config;

import java.util.ArrayList;
import java.util.List;

public class CrateDefinition {
    public String id = "vote";
    public String displayName = "Vote Crate";
    public String requiredKey = "vote";
    public int cooldownSeconds = -1;
    public Boolean consumeKeyOnOpen = null;
    public Boolean broadcastRareRewards = null;
    public Boolean rollAnimationEnabled = null;
    public Integer pityPullsWithoutRarePlus = null;
    public String inventoryFullPolicy = "inherit";
    public List<RewardEntry> rewards = new ArrayList<>();
}
