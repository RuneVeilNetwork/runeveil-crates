package com.runeveil.crates.util;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Locale;

public final class RarityDefinitions {
    public static final List<String> ALL = List.of("common", "uncommon", "rare", "epic", "legendary");

    private RarityDefinitions() {
    }

    public static String normalize(String rarity) {
        if (rarity == null || rarity.isBlank()) {
            return "common";
        }
        return rarity.toLowerCase(Locale.ROOT);
    }

    public static ChatFormatting color(String rarity) {
        return switch (normalize(rarity)) {
            case "uncommon" -> ChatFormatting.GREEN;
            case "rare" -> ChatFormatting.AQUA;
            case "epic" -> ChatFormatting.LIGHT_PURPLE;
            case "legendary" -> ChatFormatting.GOLD;
            default -> ChatFormatting.WHITE;
        };
    }

    public static Item tabItem(String rarity) {
        return switch (normalize(rarity)) {
            case "uncommon" -> Items.LIME_WOOL;
            case "rare" -> Items.LIGHT_BLUE_WOOL;
            case "epic" -> Items.PURPLE_WOOL;
            case "legendary" -> Items.ORANGE_WOOL;
            default -> Items.WHITE_WOOL;
        };
    }

    public static boolean isRareOrHigher(String rarity) {
        return switch (normalize(rarity)) {
            case "rare", "epic", "legendary" -> true;
            default -> false;
        };
    }

    public static int tierIndex(String rarity) {
        int index = ALL.indexOf(normalize(rarity));
        return index >= 0 ? index : 0;
    }

    public static ChatFormatting crateColor(String crateId) {
        if (crateId == null) {
            return ChatFormatting.AQUA;
        }
        return switch (crateId.toLowerCase(Locale.ROOT)) {
            case "vote" -> ChatFormatting.GREEN;
            case "donor" -> ChatFormatting.LIGHT_PURPLE;
            case "event" -> ChatFormatting.GOLD;
            default -> ChatFormatting.AQUA;
        };
    }
}
