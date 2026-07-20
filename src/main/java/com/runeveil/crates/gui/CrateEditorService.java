package com.runeveil.crates.gui;

import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.util.RarityDefinitions;
import com.runeveil.crates.util.RewardItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CrateEditorService {
    private static final int RARITY_TAB_START = 0;
    private static final int RARITY_TAB_END = 4;
    private static final int SLOT_ADD_HOTBAR = 6;
    private static final int HOTBAR_SLOT_START = 0;
    private static final int HOTBAR_SLOT_END = 8;
    private static final int SLOT_INFO = 8;
    private static final int REWARD_START = 9;
    private static final int REWARD_END = 44;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 46;
    private static final int SLOT_ADD_ITEM = 47;
    private static final int SLOT_ADD_COMMAND = 48;
    private static final int SLOT_WEIGHT_UP = 49;
    private static final int SLOT_WEIGHT_DOWN = 50;
    private static final int SLOT_REMOVE = 51;
    private static final int SLOT_SAVE = 52;
    private static final int SLOT_CLOSE = 53;

    private static final Map<UUID, CrateEditorSession> SESSIONS = new HashMap<>();

    private CrateEditorService() {
    }

    public static boolean open(ServerPlayer player, CrateConfigManager manager, BlockPos pos) {
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        String crateId = manager.getLocations().locations.get(com.runeveil.crates.storage.CrateLocationKey.encode(dimension, pos));
        if (crateId == null) {
            player.sendSystemMessage(Component.literal("This block is not a crate.").withStyle(ChatFormatting.RED));
            return false;
        }
        CrateDefinition crate = manager.getCrate(crateId);
        if (crate == null) {
            player.sendSystemMessage(Component.literal("Unknown crate type: " + crateId).withStyle(ChatFormatting.RED));
            return false;
        }

        CrateEditorSession session = new CrateEditorSession(pos, dimension, crateId, crate);
        SESSIONS.put(player.getUUID(), session);
        SimpleContainer container = new SimpleContainer(54);
        populateContainer(container, session);

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Edit " + session.workingCopy.displayName);
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player ignored) {
                return new CrateEditorMenu(containerId, inventory, container, session, manager, player);
            }
        });
        return true;
    }

    static void populateContainer(SimpleContainer container, CrateEditorSession session) {
        container.clearContent();
        for (int i = 0; i < 54; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        List<String> rarities = RarityDefinitions.ALL;
        for (int i = 0; i < rarities.size() && i <= RARITY_TAB_END; i++) {
            String rarity = rarities.get(i);
            boolean selected = rarity.equalsIgnoreCase(session.currentRarity);
            ItemStack tab = GuiIcons.icon(
                    GuiIcons.rarityTabModel(rarity),
                    (selected ? "> " : "") + capitalize(rarity),
                    "View " + rarity + " rewards (" + countForRarity(session, rarity) + ")",
                    RarityDefinitions.color(rarity)
            );
            if (selected) {
                tab.setHoverName(Component.literal((selected ? "> " : "") + capitalize(rarity))
                        .withStyle(RarityDefinitions.color(rarity), ChatFormatting.BOLD));
            }
            container.setItem(RARITY_TAB_START + i, tab);
        }

        session.clampPage();
        ItemStack info = GuiIcons.icon(GuiIcons.INFO, session.workingCopy.displayName, "Crate loot table editor", ChatFormatting.GOLD);
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.literal("Crate ID: " + session.crateId).withStyle(ChatFormatting.GRAY));
        infoLore.add(Component.literal("Rarity: " + capitalize(session.currentRarity)).withStyle(RarityDefinitions.color(session.currentRarity)));
        infoLore.add(Component.literal("Page: " + (session.currentPage + 1) + "/" + session.totalPages()).withStyle(ChatFormatting.YELLOW));
        infoLore.add(Component.literal("Count: " + countForRarity(session, session.currentRarity)).withStyle(ChatFormatting.DARK_GRAY));
        setLore(info, infoLore);
        container.setItem(SLOT_ADD_HOTBAR, GuiIcons.icon(GuiIcons.ADD_HOTBAR, "Add Hotbar", "Adds all 9 hotbar slots as rewards", ChatFormatting.GREEN));
        container.setItem(SLOT_INFO, info);

        List<RewardEntry> pageRewards = session.pageRewards();
        for (int i = 0; i < pageRewards.size() && i <= (REWARD_END - REWARD_START); i++) {
            RewardEntry reward = pageRewards.get(i);
            container.setItem(REWARD_START + i, rewardIcon(reward, reward == session.selectedReward));
        }

        container.setItem(SLOT_PREV_PAGE, GuiIcons.icon(GuiIcons.PREV_PAGE, "Previous Page", "Shows earlier " + session.currentRarity + " rewards", ChatFormatting.YELLOW));
        container.setItem(SLOT_NEXT_PAGE, GuiIcons.icon(GuiIcons.NEXT_PAGE, "Next Page", "Shows more " + session.currentRarity + " rewards", ChatFormatting.YELLOW));
        container.setItem(SLOT_ADD_ITEM, GuiIcons.icon(GuiIcons.ADD_ITEM, "Add Item Reward", "Uses the item in your main hand", ChatFormatting.GREEN));
        container.setItem(SLOT_ADD_COMMAND, GuiIcons.icon(GuiIcons.ADD_COMMAND, "Add Command Reward", "Adds: give {player} diamond 1", ChatFormatting.AQUA));
        container.setItem(SLOT_WEIGHT_UP, GuiIcons.icon(GuiIcons.WEIGHT_UP, "Weight +5", "Chance within this rarity tier", ChatFormatting.YELLOW));
        container.setItem(SLOT_WEIGHT_DOWN, GuiIcons.icon(GuiIcons.WEIGHT_DOWN, "Weight -5", "Chance within this rarity tier", ChatFormatting.GRAY));
        container.setItem(SLOT_REMOVE, GuiIcons.icon(GuiIcons.REMOVE, "Remove Reward", "Removes the selected reward", ChatFormatting.RED));
        container.setItem(SLOT_SAVE, GuiIcons.icon(GuiIcons.SAVE, "Save Loot Table", "Writes changes to config file", ChatFormatting.GREEN));
        container.setItem(SLOT_CLOSE, GuiIcons.icon(GuiIcons.CLOSE, "Close", "Close without saving again", ChatFormatting.RED));
    }

    private static int countForRarity(CrateEditorSession session, String rarity) {
        int count = 0;
        if (session.workingCopy.rewards == null) {
            return 0;
        }
        for (RewardEntry reward : session.workingCopy.rewards) {
            if (RarityDefinitions.normalize(reward.rarity).equals(RarityDefinitions.normalize(rarity))) {
                count++;
            }
        }
        return count;
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Common";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static ItemStack rewardIcon(RewardEntry reward, boolean selected) {
        ItemStack stack;
        if ("command".equalsIgnoreCase(reward.type)) {
            stack = new ItemStack(Items.COMMAND_BLOCK);
            stack.setHoverName(Component.literal((selected ? "> " : "") + reward.displayName)
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        } else {
            stack = RewardItems.previewStack(reward);
            if (selected) {
                stack.setHoverName(Component.literal("> ").append(stack.getHoverName()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            }
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Type: " + reward.type).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Weight: " + reward.weight + " (within " + reward.rarity + ")").withStyle(ChatFormatting.GOLD));
        lore.add(Component.literal("Rarity: " + reward.rarity).withStyle(RarityDefinitions.color(reward.rarity)));
        if ("item".equalsIgnoreCase(reward.type)) {
            lore.add(Component.literal("Item: " + reward.item).withStyle(ChatFormatting.DARK_GRAY));
            lore.add(Component.literal("Count: " + reward.minCount + "-" + reward.maxCount).withStyle(ChatFormatting.DARK_GRAY));
            if (RewardItems.hasStoredStackData(reward)) {
                lore.add(Component.literal("Stored with full item data (mod NBT)").withStyle(ChatFormatting.DARK_GREEN));
            }
        } else if (reward.commands != null && !reward.commands.isEmpty()) {
            lore.add(Component.literal("Command: " + reward.commands.get(0)).withStyle(ChatFormatting.DARK_GRAY));
        }
        setLore(stack, lore);
        return stack;
    }

    static void setLorePublic(ItemStack stack, List<Component> lore) {
        setLore(stack, lore);
    }

    private static void setLore(ItemStack stack, List<Component> lore) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag display = tag.getCompound("display");
        if (!tag.contains("display")) {
            display = new CompoundTag();
            tag.put("display", display);
        }
        ListTag loreTag = new ListTag();
        for (Component line : lore) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", loreTag);
    }

    public static class CrateEditorMenu extends ChestMenu {
        private final SimpleContainer editorContainer;
        private final CrateEditorSession session;
        private final CrateConfigManager manager;
        private final ServerPlayer owner;

        public CrateEditorMenu(int containerId, Inventory inventory, SimpleContainer container, CrateEditorSession session,
                               CrateConfigManager manager, ServerPlayer owner) {
            super(MenuType.GENERIC_9x6, containerId, inventory, container, 6);
            this.editorContainer = container;
            this.session = session;
            this.manager = manager;
            this.owner = owner;
        }

        @Override
        public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
            if (!(player instanceof ServerPlayer serverPlayer) || slotId < 0 || slotId >= 54) {
                super.clicked(slotId, dragType, clickType, player);
                return;
            }

            if (slotId >= RARITY_TAB_START && slotId <= RARITY_TAB_END) {
                session.currentRarity = RarityDefinitions.ALL.get(slotId - RARITY_TAB_START);
                session.currentPage = 0;
                session.selectedReward = null;
                populateContainer(editorContainer, session);
                broadcastChanges();
                return;
            }

            if (slotId == SLOT_ADD_HOTBAR) {
                addHotbarRewards(serverPlayer);
                populateContainer(editorContainer, session);
                broadcastChanges();
                return;
            }

            if (slotId >= REWARD_START && slotId <= REWARD_END) {
                ItemStack carried = getCarried();
                if (!carried.isEmpty()) {
                    tryAddRewardFromStack(serverPlayer, carried);
                    populateContainer(editorContainer, session);
                    broadcastChanges();
                    return;
                }

                int index = slotId - REWARD_START;
                List<RewardEntry> pageRewards = session.pageRewards();
                session.selectedReward = index < pageRewards.size() ? pageRewards.get(index) : null;
                populateContainer(editorContainer, session);
                broadcastChanges();
                return;
            }

            switch (slotId) {
                case SLOT_PREV_PAGE -> {
                    if (session.currentPage > 0) {
                        session.currentPage--;
                        session.selectedReward = null;
                    }
                }
                case SLOT_NEXT_PAGE -> {
                    if (session.currentPage < session.totalPages() - 1) {
                        session.currentPage++;
                        session.selectedReward = null;
                    }
                }
                case SLOT_ADD_ITEM -> addItemReward(serverPlayer);
                case SLOT_ADD_COMMAND -> addCommandReward();
                case SLOT_WEIGHT_UP -> adjustWeight(5);
                case SLOT_WEIGHT_DOWN -> adjustWeight(-5);
                case SLOT_REMOVE -> removeSelected();
                case SLOT_SAVE -> save();
                case SLOT_CLOSE -> {
                    SESSIONS.remove(owner.getUUID());
                    owner.closeContainer();
                    return;
                }
                default -> {
                    return;
                }
            }

            populateContainer(editorContainer, session);
            broadcastChanges();
        }

        private void tryAddRewardFromStack(ServerPlayer player, ItemStack stack) {
            if (!isValidRewardStack(stack)) {
                player.sendSystemMessage(Component.literal("That item cannot be added as a reward.").withStyle(ChatFormatting.RED));
                return;
            }
            addReward(player, RewardItems.createRewardFromStack(stack, session.currentRarity, null));
            player.sendSystemMessage(Component.literal("Added " + stack.getHoverName().getString()
                    + " to " + capitalize(session.currentRarity) + " rewards.").withStyle(ChatFormatting.GREEN));
        }

        private void addItemReward(ServerPlayer player) {
            ItemStack hand = player.getMainHandItem();
            if (!isValidRewardStack(hand)) {
                player.sendSystemMessage(Component.literal("Hold the item you want to add as a reward in your main hand.").withStyle(ChatFormatting.RED));
                return;
            }
            tryAddRewardFromStack(player, hand);
        }

        private void addHotbarRewards(ServerPlayer player) {
            if (session.workingCopy.rewards == null) {
                session.workingCopy.rewards = new ArrayList<>();
            }

            int added = 0;
            RewardEntry lastAdded = null;
            for (int slot = HOTBAR_SLOT_START; slot <= HOTBAR_SLOT_END; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!isValidRewardStack(stack)) {
                    continue;
                }
                RewardEntry reward = RewardItems.createRewardFromStack(stack, session.currentRarity, "slot" + slot);
                session.workingCopy.rewards.add(reward);
                lastAdded = reward;
                added++;
            }

            if (added == 0) {
                player.sendSystemMessage(Component.literal("Your hotbar has no valid items to add.").withStyle(ChatFormatting.RED));
                return;
            }

            session.selectedReward = lastAdded;
            session.clampPage();
            player.sendSystemMessage(Component.literal("Added " + added + " hotbar item(s) to " + capitalize(session.currentRarity) + " rewards.")
                    .withStyle(ChatFormatting.GREEN));
        }

        private void addReward(ServerPlayer player, RewardEntry reward) {
            if (session.workingCopy.rewards == null) {
                session.workingCopy.rewards = new ArrayList<>();
            }
            session.workingCopy.rewards.add(reward);
            session.selectedReward = reward;
            session.clampPage();
        }

        private static boolean isValidRewardStack(ItemStack stack) {
            return !stack.isEmpty();
        }

        private void addCommandReward() {
            RewardEntry reward = new RewardEntry();
            reward.id = "command_reward_" + (session.workingCopy.rewards == null ? 1 : session.workingCopy.rewards.size() + 1);
            reward.type = "command";
            reward.weight = 5;
            reward.rarity = session.currentRarity;
            reward.broadcast = false;
            reward.displayName = "Command Reward";
            reward.commands = new ArrayList<>(List.of("give {player} minecraft:diamond 1"));
            if (session.workingCopy.rewards == null) {
                session.workingCopy.rewards = new ArrayList<>();
            }
            session.workingCopy.rewards.add(reward);
            session.selectedReward = reward;
            session.clampPage();
        }

        private void adjustWeight(int amount) {
            RewardEntry reward = selectedReward();
            if (reward != null) {
                reward.weight = Math.max(1, reward.weight + amount);
            }
        }

        private void removeSelected() {
            RewardEntry reward = selectedReward();
            if (reward != null && session.workingCopy.rewards != null) {
                session.workingCopy.rewards.remove(reward);
                session.selectedReward = null;
                session.clampPage();
            }
        }

        private RewardEntry selectedReward() {
            if (session.selectedReward == null) {
                owner.sendSystemMessage(Component.literal("Select a reward first.").withStyle(ChatFormatting.RED));
                return null;
            }
            return session.selectedReward;
        }

        private void save() {
            manager.saveCrate(session.workingCopy);
            manager.reload();
            owner.sendSystemMessage(Component.literal("Saved loot table for " + session.workingCopy.displayName + ".").withStyle(ChatFormatting.GREEN));
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (player instanceof ServerPlayer serverPlayer && index >= 54) {
                ItemStack stack = slots.get(index).getItem();
                if (isValidRewardStack(stack)) {
                    tryAddRewardFromStack(serverPlayer, stack);
                    populateContainer(editorContainer, session);
                    broadcastChanges();
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return player.distanceToSqr(session.cratePos.getX() + 0.5, session.cratePos.getY() + 0.5, session.cratePos.getZ() + 0.5) <= 64;
        }

        @Override
        public void removed(Player player) {
            SESSIONS.remove(player.getUUID());
            super.removed(player);
        }
    }
}
