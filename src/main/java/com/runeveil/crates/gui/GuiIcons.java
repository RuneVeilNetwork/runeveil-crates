package com.runeveil.crates.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

public final class GuiIcons {
    public static final int COMMON_TAB = 2201;
    public static final int UNCOMMON_TAB = 2202;
    public static final int RARE_TAB = 2203;
    public static final int EPIC_TAB = 2204;
    public static final int LEGENDARY_TAB = 2205;
    public static final int INFO = 2206;
    public static final int PREV_PAGE = 2207;
    public static final int NEXT_PAGE = 2208;
    public static final int ADD_ITEM = 2209;
    public static final int ADD_COMMAND = 2210;
    public static final int WEIGHT_UP = 2211;
    public static final int WEIGHT_DOWN = 2212;
    public static final int REMOVE = 2213;
    public static final int SAVE = 2214;
    public static final int CLOSE = 2215;
    public static final int ADD_HOTBAR = 2216;

    private GuiIcons() {
    }

    public static int rarityTabModel(String rarity) {
        return switch (com.runeveil.crates.util.RarityDefinitions.normalize(rarity)) {
            case "uncommon" -> UNCOMMON_TAB;
            case "rare" -> RARE_TAB;
            case "epic" -> EPIC_TAB;
            case "legendary" -> LEGENDARY_TAB;
            default -> COMMON_TAB;
        };
    }

    public static ItemStack icon(int iconId, String name, String description, ChatFormatting color) {
        ItemStack stack = new ItemStack(itemFor(iconId));
        stack.setHoverName(Component.literal(name).withStyle(color));
        CrateEditorService.setLorePublic(stack, List.of(Component.literal(description).withStyle(ChatFormatting.GRAY)));
        return stack;
    }

    private static Item itemFor(int iconId) {
        return switch (iconId) {
            case COMMON_TAB -> Items.COAL;
            case UNCOMMON_TAB -> Items.COPPER_INGOT;
            case RARE_TAB -> Items.LAPIS_LAZULI;
            case EPIC_TAB -> Items.AMETHYST_SHARD;
            case LEGENDARY_TAB -> Items.NETHER_STAR;
            case INFO -> Items.WRITABLE_BOOK;
            case PREV_PAGE -> Items.ARROW;
            case NEXT_PAGE -> Items.SPECTRAL_ARROW;
            case ADD_ITEM -> Items.HOPPER;
            case ADD_COMMAND -> Items.COMMAND_BLOCK;
            case WEIGHT_UP -> Items.GOLD_NUGGET;
            case WEIGHT_DOWN -> Items.IRON_NUGGET;
            case REMOVE -> Items.BARRIER;
            case SAVE -> Items.EMERALD;
            case CLOSE -> Items.REDSTONE_BLOCK;
            case ADD_HOTBAR -> Items.CHEST;
            default -> Items.BARRIER;
        };
    }
}
