package com.runeveil.crates.config;

import java.util.ArrayList;
import java.util.List;

public class CrateDefinition {
    public String id = "vote";
    public String displayName = "Vote Crate";
    public String requiredKey = "vote";
    public int cooldownSeconds = -1;
    public List<RewardEntry> rewards = new ArrayList<>();
}
