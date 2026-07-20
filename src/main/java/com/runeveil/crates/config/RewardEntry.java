package com.runeveil.crates.config;

import java.util.ArrayList;
import java.util.List;

public class RewardEntry {
    public String id = "reward";
    public String type = "item";
    public String item = "minecraft:golden_apple";
    public int minCount = 1;
    public int maxCount = 1;
    public int weight = 10;
    public String rarity = "common";
    public boolean broadcast = false;
    public String displayName = "Golden Apple";
    public String itemStackNbt = "";
    public List<String> commands = new ArrayList<>();
}
