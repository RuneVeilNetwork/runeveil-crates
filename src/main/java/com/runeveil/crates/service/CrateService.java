package com.runeveil.crates.service;

import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.KeyConfig;
import com.runeveil.crates.config.RewardEntry;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.storage.CrateLocationKey;
import com.runeveil.crates.visual.CrateHologramManager;
import com.runeveil.crates.visual.CrateRollAnimation;
import net.minecraft.core.BlockPos;
import com.runeveil.crates.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CrateService {
    private static final Map<CooldownKey, Long> COOLDOWNS = new HashMap<>();

    private CrateService() {
    }

    public static BlockPos getTargetBlock(ServerPlayer player, int distance) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(distance));
        HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return ((BlockHitResult) hit).getBlockPos();
    }

    public static boolean isAllowedBlock(CrateConfigManager configManager, BlockState state) {
        String id = java.util.Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock())).toString();
        return configManager.getSettings().allowedBlocks.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(id.toLowerCase(Locale.ROOT)));
    }

    public static String getCrateAt(CrateConfigManager configManager, ResourceKey<Level> dimension, BlockPos pos) {
        return configManager.getLocations().locations.get(CrateLocationKey.encode(dimension, pos));
    }

    public static int cleanupInvalidLocations(CrateConfigManager configManager) {
        int removed = 0;
        Iterator<Map.Entry<String, String>> iterator = configManager.getLocations().locations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            ResourceKey<Level> dimension = CrateLocationKey.decodeDimension(entry.getKey());
            BlockPos pos = CrateLocationKey.decodePos(entry.getKey());
            ServerLevel level = dimension == null ? null : configManager.getServerLevel(dimension);
            if (pos == null || level == null) {
                iterator.remove();
                removed++;
                continue;
            }
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            if (!isAllowedBlock(configManager, level.getBlockState(pos))) {
                iterator.remove();
                CrateHologramManager.remove(level, pos);
                removed++;
            }
        }
        if (removed > 0) {
            configManager.saveLocations();
        }
        return removed;
    }

    public static boolean convert(ServerPlayer player, CrateConfigManager configManager, String crateId, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        SettingsConfig settings = configManager.getSettings();

        if (!isAllowedBlock(configManager, state)) {
            player.sendSystemMessage(message(settings, "notCrateBlock"));
            return false;
        }
        if (getCrateAt(configManager, level.dimension(), pos) != null) {
            player.sendSystemMessage(message(settings, "alreadyCrate"));
            return false;
        }

        CrateDefinition crate = configManager.getCrate(crateId);
        if (crate == null) {
            player.sendSystemMessage(Component.literal("Unknown crate type: " + crateId));
            return false;
        }

        configManager.getLocations().locations.put(CrateLocationKey.encode(level.dimension(), pos), crate.id);
        configManager.saveLocations();
        CrateHologramManager.showCrateLabel(level, pos, crate, settings);
        player.sendSystemMessage(message(settings, "converted", "crate", crate.displayName));
        return true;
    }

    public static boolean unconvert(ServerPlayer player, CrateConfigManager configManager, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        SettingsConfig settings = configManager.getSettings();
        String key = CrateLocationKey.encode(level.dimension(), pos);
        if (!configManager.getLocations().locations.containsKey(key)) {
            player.sendSystemMessage(message(settings, "notCrate"));
            return false;
        }
        configManager.getLocations().locations.remove(key);
        configManager.saveLocations();
        CrateHologramManager.remove(level, pos);
        player.sendSystemMessage(message(settings, "unconverted"));
        return true;
    }

    public static boolean open(ServerPlayer player, CrateConfigManager configManager, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        SettingsConfig settings = configManager.getSettings();
        String crateId = getCrateAt(configManager, level.dimension(), pos);
        if (crateId == null) {
            return false;
        }

        CrateDefinition crate = configManager.getCrate(crateId);
        if (crate == null) {
            return false;
        }

        if (CrateRollAnimation.isRollingAt(pos, level)) {
            return true;
        }

        KeyConfig requiredKey = configManager.getKey(crate.requiredKey);
        ItemStackHolder keyStack = findMatchingKey(player, crate.requiredKey);
        if (keyStack == null) {
            String keyName = requiredKey != null ? requiredKey.displayName : crate.requiredKey;
            player.sendSystemMessage(message(settings, "needKey", "key", keyName));
            return true;
        }

        int cooldown = crate.cooldownSeconds >= 0 ? crate.cooldownSeconds : settings.openCooldownSeconds;
        if (cooldown > 0 && isOnCooldown(player.getUUID(), crate.id)) {
            long remaining = remainingCooldown(player.getUUID(), crate.id);
            player.sendSystemMessage(message(settings, "cooldown", "seconds", Long.toString(remaining)));
            return true;
        }

        RewardEntry reward = RewardService.rollReward(crate, player, configManager);
        if (reward == null) {
            player.sendSystemMessage(Component.literal("This crate has no rewards configured.").withStyle(net.minecraft.ChatFormatting.RED));
            return true;
        }

        if (!RewardService.canGrant(player, crate, reward, configManager)) {
            player.sendSystemMessage(Component.literal("You need inventory space before opening this crate.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return true;
        }

        boolean consumeKey = crate.consumeKeyOnOpen != null ? crate.consumeKeyOnOpen : settings.consumeKeyOnOpen;
        if (consumeKey) {
            KeyService.consumeKey(player, keyStack.stack());
        }

        setCooldown(player.getUUID(), crate.id, cooldown);
        player.sendSystemMessage(message(settings, "opened", "crate", crate.displayName));

        // Grant before the cosmetic animation so a disconnect or server shutdown cannot lose the reward.
        RewardService.grantReward(player, crate, reward, configManager);

        CrateRollAnimation.start(level, pos, crate, reward, player, configManager, () -> {
        });
        return true;
    }

    private static ItemStackHolder findMatchingKey(ServerPlayer player, String keyId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (KeyService.isKeyFor(stack, keyId)) {
                return new ItemStackHolder(stack);
            }
        }
        var offhand = player.getOffhandItem();
        if (KeyService.isKeyFor(offhand, keyId)) {
            return new ItemStackHolder(offhand);
        }
        return null;
    }

    private static boolean isOnCooldown(UUID playerId, String crateId) {
        Long until = COOLDOWNS.get(new CooldownKey(playerId, CrateConfigManager.normalize(crateId)));
        return until != null && until > System.currentTimeMillis();
    }

    private static long remainingCooldown(UUID playerId, String crateId) {
        Long until = COOLDOWNS.get(new CooldownKey(playerId, CrateConfigManager.normalize(crateId)));
        if (until == null) {
            return 0;
        }
        return Math.max(1, (until - System.currentTimeMillis() + 999) / 1000);
    }

    private static void setCooldown(UUID playerId, String crateId, int seconds) {
        if (seconds <= 0) {
            return;
        }
        COOLDOWNS.put(new CooldownKey(playerId, CrateConfigManager.normalize(crateId)), System.currentTimeMillis() + seconds * 1000L);
    }

    public static void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static Component message(SettingsConfig settings, String key) {
        return message(settings, key, null, null);
    }

    private static Component message(SettingsConfig settings, String key, String placeholder, String value) {
        String template = settings.messages.getOrDefault(key, key);
        if (placeholder != null && value != null) {
            return MessageUtil.format(template, placeholder, value);
        }
        return MessageUtil.parse(template);
    }

    private record ItemStackHolder(net.minecraft.world.item.ItemStack stack) {
    }

    private record CooldownKey(UUID playerId, String crateId) {
    }
}
