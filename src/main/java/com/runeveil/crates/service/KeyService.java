package com.runeveil.crates.service;

import com.runeveil.crates.RuneveilCrates;
import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.KeyConfig;
import com.runeveil.crates.util.MessageUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import com.runeveil.crates.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

public final class KeyService {
    private KeyService() {
    }

    public static ItemStack createKey(CrateConfigManager configManager, String keyId, int count) {
        KeyConfig keyConfig = configManager.getKey(keyId);
        if (keyConfig == null) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId = parseLocation(keyConfig.item);
        Item item = itemId == null ? Items.TRIPWIRE_HOOK : BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.TRIPWIRE_HOOK);
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(RuneveilCrates.KEY_TAG, CrateConfigManager.normalize(keyConfig.id));

        CompoundTag display = new CompoundTag();
        display.putString("Name", Component.Serializer.toJson(MessageUtil.parse(keyConfig.displayName)));

        ListTag loreTag = new ListTag();
        for (String line : keyConfig.lore) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(MessageUtil.parse(line))));
        }
        display.put("Lore", loreTag);
        tag.put("display", display);

        if (keyConfig.glow) {
            stack.enchant(Enchantments.UNBREAKING, 1);
            tag.putInt("HideFlags", 1);
        }

        return stack;
    }

    public static boolean isKeyFor(ItemStack stack, String keyId) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && CrateConfigManager.normalize(keyId).equals(tag.getString(RuneveilCrates.KEY_TAG));
    }

    public static void consumeKey(ServerPlayer player, ItemStack stack) {
        stack.shrink(1);
    }

    private static ResourceLocation parseLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }
}
