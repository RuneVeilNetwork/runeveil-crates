package com.runeveil.crates.config;

import java.util.ArrayList;
import java.util.List;

public class KeyConfig {
    public String id = "vote";
    public String displayName = "Vote Key";
    public List<String> lore = new ArrayList<>(List.of("Use on a Vote Crate", "Earned from voting"));
    public String item = "minecraft:tripwire_hook";
    public boolean glow = true;
}
