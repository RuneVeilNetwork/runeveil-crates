package com.runeveil.crates.util;

import com.runeveil.crates.RuneveilCratesMod;
import com.runeveil.crates.config.RewardEntry;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

public final class RewardItems {
    private RewardItems() {
    }

    public static void captureFromHand(RewardEntry reward, ItemStack hand) {
        captureFromStack(reward, hand);
    }

    public static RewardEntry createRewardFromStack(ItemStack stack, String rarity, String idSuffix) {
        RewardEntry reward = new RewardEntry();
        String baseId = java.util.Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString().replace(':', '_');
        reward.id = idSuffix == null || idSuffix.isBlank() ? baseId : baseId + "_" + idSuffix;
        captureFromStack(reward, stack);
        reward.weight = 10;
        reward.rarity = rarity;
        return reward;
    }

    private static void captureFromStack(RewardEntry reward, ItemStack stack) {
        reward.type = "item";
        reward.item = java.util.Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString();
        reward.minCount = stack.getCount();
        reward.maxCount = stack.getCount();
        reward.displayName = stack.getHoverName().getString();

        ItemStack template = stack.copy();
        template.setCount(1);
        CompoundTag tag = new CompoundTag();
        template.save(tag);
        reward.itemStackNbt = tag.toString();
    }
    public static ItemStack createStack(RewardEntry reward) {
        int min = Math.max(1, reward.minCount);
        int max = Math.max(min, reward.maxCount);
        int count = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        return createStack(reward, count);
    }

    public static ItemStack createStack(RewardEntry reward, int count) {
        if (reward.itemStackNbt != null && !reward.itemStackNbt.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(reward.itemStackNbt);
                ItemStack stack = ItemStack.of(tag);
                if (!stack.isEmpty()) {
                    stack.setCount(Math.max(1, count));
                    return stack;
                }
            } catch (Exception e) {
                RuneveilCratesMod.LOGGER.warn("Failed to parse itemStackNbt for reward '{}': {}", reward.id, e.getMessage());
            }
        }

        ResourceLocation itemId = ResourceLocation.tryParse(reward.item);
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, count));
    }

    public static ItemStack previewStack(RewardEntry reward) {
        ItemStack stack = createStack(reward, Math.max(1, Math.min(reward.maxCount, 64)));
        if (stack.isEmpty()) {
            return new ItemStack(Items.PAPER);
        }
        return stack;
    }

    public static boolean hasStoredStackData(RewardEntry reward) {
        return reward.itemStackNbt != null && !reward.itemStackNbt.isBlank();
    }
}
