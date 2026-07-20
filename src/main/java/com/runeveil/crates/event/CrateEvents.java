package com.runeveil.crates.event;

import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.gui.CrateEditorService;
import com.runeveil.crates.integration.VotifierIntegration;
import com.runeveil.crates.service.CrateService;
import com.runeveil.crates.service.KeyService;
import com.runeveil.crates.service.PendingKeyService;
import com.runeveil.crates.visual.CrateRollAnimation;
import com.runeveil.crates.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CrateEvents {
    private static CrateConfigManager configManager;
    private static int validationTicks;

    public static void setConfigManager(CrateConfigManager manager) {
        configManager = manager;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CrateRollAnimation.tick();
            if (configManager != null && event.getServer() != null) {
                if (++validationTicks >= 200) {
                    validationTicks = 0;
                    CrateService.cleanupInvalidLocations(configManager);
                    CrateService.cleanupExpiredCooldowns();
                    configManager.flushDirtyData();
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (configManager == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VotifierIntegration.processUberswePendingVotes(player, configManager);
        PendingKeyService.deliver(player, configManager);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || configManager == null) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        String crateId = CrateService.getCrateAt(configManager, event.getLevel().dimension(), event.getPos());
        if (crateId == null) {
            return;
        }

        CrateDefinition crate = configManager.getCrate(crateId);
        if (crate == null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean hasKey = KeyService.isKeyFor(mainHand, crate.requiredKey) || KeyService.isKeyFor(offHand, crate.requiredKey);
        boolean holdingCrateKey = hasCrateKey(mainHand) || hasCrateKey(offHand);

        if (hasKey) {
            CrateService.open(player, configManager, event.getPos());
            return;
        }

        boolean emptyHands = mainHand.isEmpty() && offHand.isEmpty();
        SettingsConfig settings = configManager.getSettings();
        if (emptyHands && event.getHand() == InteractionHand.MAIN_HAND && player.hasPermissions(settings.editorPermissionLevel)) {
            CrateEditorService.open(player, configManager, event.getPos());
            return;
        }

        if (event.getHand() == InteractionHand.MAIN_HAND) {
            if (holdingCrateKey) {
                player.sendSystemMessage(MessageUtil.parse(settings.messages.getOrDefault(
                        "wrongKey", "&cThat key does not work on this crate.")));
                return;
            }
            if (player.hasPermissions(settings.editorPermissionLevel)) {
                player.sendSystemMessage(Component.literal("Hold the correct crate key to roll rewards, or use empty hands to edit loot."));
            } else {
                String keyName = configManager.getKey(crate.requiredKey) != null
                        ? configManager.getKey(crate.requiredKey).displayName
                        : crate.requiredKey;
                player.sendSystemMessage(MessageUtil.format(
                        settings.messages.getOrDefault("needKey", "&cYou need a &e{key}&c to open this crate."),
                        "key",
                        keyName
                ));
            }
        }
    }

    private static boolean hasCrateKey(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag() != null
                && !stack.getTag().getString(com.runeveil.crates.RuneveilCrates.KEY_TAG).isBlank();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || configManager == null) {
            return;
        }
        if (CrateService.getCrateAt(configManager, event.getLevel().dimension(), event.getPos()) != null) {
            event.setCanceled(true);
        }
    }
}
