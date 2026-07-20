package com.runeveil.crates.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.KeyConfig;
import com.runeveil.crates.gui.CrateEditorService;
import com.runeveil.crates.integration.VotifierIntegration;
import com.runeveil.crates.service.CrateService;
import com.runeveil.crates.service.KeyService;
import com.runeveil.crates.service.PendingKeyService;
import com.runeveil.crates.service.RewardProbabilityService;
import com.runeveil.crates.storage.CrateLocationKey;
import com.runeveil.crates.util.MessageUtil;
import com.runeveil.crates.visual.CrateHologramManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;
import java.util.UUID;

public final class CrateCommand {
    private CrateCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<CrateConfigManager> configSupplier) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("crate")
                .then(Commands.literal("convert")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    CrateConfigManager manager = configSupplier.get();
                                    if (manager != null) {
                                        manager.getCrates().keySet().forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> convert(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("unconvert")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .executes(ctx -> unconvert(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("givekey")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    CrateConfigManager manager = configSupplier.get();
                                    if (manager != null) {
                                        manager.getKeys().keySet().forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> giveKey(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type"), ctx.getSource().getPlayerOrException(), 1))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> giveKey(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type"), EntityArgument.getPlayer(ctx, "player"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> giveKey(
                                                        ctx.getSource(),
                                                        configSupplier.get(),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                ))))))
                .then(Commands.literal("reload")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .executes(ctx -> reload(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("info")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .executes(ctx -> info(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("edit")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .executes(ctx -> edit(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("settings")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.literal("rollanimation")
                                .executes(ctx -> showRollAnimation(ctx.getSource(), configSupplier.get()))
                                .then(Commands.literal("enabled")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setRollAnimationEnabled(ctx.getSource(), configSupplier.get(), BoolArgumentType.getBool(ctx, "value")))))
                                .then(Commands.literal("ticksperstep")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 200))
                                                .executes(ctx -> setRollAnimationNumber(ctx.getSource(), configSupplier.get(), "ticksPerStep", IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.literal("maximumsteps")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> setRollAnimationNumber(ctx.getSource(), configSupplier.get(), "maximumSteps", IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.literal("finalholdticks")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 1200))
                                                .executes(ctx -> setRollAnimationNumber(ctx.getSource(), configSupplier.get(), "finalHoldTicks", IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(Commands.literal("cleanupholograms")
                        .requires(source -> hasAdminPermission(source, configSupplier))
                        .executes(ctx -> cleanupHolograms(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("validate").requires(source -> hasAdminPermission(source, configSupplier))
                        .executes(ctx -> validate(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("preview").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> preview(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("givekeyoffline").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("uuid", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                                .executes(ctx -> giveKeyOffline(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "uuid"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(Commands.literal("duplicate").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("source", StringArgumentType.word()).then(Commands.argument("newId", StringArgumentType.word())
                                .executes(ctx -> duplicate(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "source"), StringArgumentType.getString(ctx, "newId"))))))
                .then(Commands.literal("rename").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("oldId", StringArgumentType.word()).then(Commands.argument("newId", StringArgumentType.word())
                                .executes(ctx -> rename(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "oldId"), StringArgumentType.getString(ctx, "newId"))))))
                .then(Commands.literal("delete").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("type", StringArgumentType.word()).then(Commands.literal("confirm")
                                .executes(ctx -> delete(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type"))))))
                .then(Commands.literal("export").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> exportCrate(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("import").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("file", StringArgumentType.string())
                                .executes(ctx -> importCrate(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "file")))))
                .then(Commands.literal("override").requires(source -> hasAdminPermission(source, configSupplier))
                        .then(Commands.argument("type", StringArgumentType.word()).then(Commands.argument("setting", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> setOverride(ctx.getSource(), configSupplier.get(), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "setting"), StringArgumentType.getString(ctx, "value")))))));

        dispatcher.register(root);
    }

    private static boolean hasAdminPermission(CommandSourceStack source, Supplier<CrateConfigManager> supplier) {
        CrateConfigManager manager = supplier.get();
        return source.hasPermission(manager == null ? 2 : manager.getSettings().adminPermissionLevel);
    }

    private static int showRollAnimation(CommandSourceStack source, CrateConfigManager manager) {
        if (manager == null || manager.getSettings().rollAnimation == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        var roll = manager.getSettings().rollAnimation;
        source.sendSuccess(() -> Component.literal(
                "Roll animation: enabled=" + roll.enabled
                        + ", ticksPerStep=" + roll.ticksPerStep
                        + ", maximumSteps=" + roll.maximumSteps
                        + ", finalHoldTicks=" + roll.finalHoldTicks), false);
        return 1;
    }

    private static int setRollAnimationEnabled(CommandSourceStack source, CrateConfigManager manager, boolean value) {
        if (manager == null || manager.getSettings().rollAnimation == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        manager.getSettings().rollAnimation.enabled = value;
        return saveRollAnimationSetting(source, manager, "enabled", Boolean.toString(value));
    }

    private static int setRollAnimationNumber(CommandSourceStack source, CrateConfigManager manager, String setting, int value) {
        if (manager == null || manager.getSettings().rollAnimation == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        switch (setting) {
            case "ticksPerStep" -> manager.getSettings().rollAnimation.ticksPerStep = value;
            case "maximumSteps" -> manager.getSettings().rollAnimation.maximumSteps = value;
            case "finalHoldTicks" -> manager.getSettings().rollAnimation.finalHoldTicks = value;
            default -> {
                source.sendFailure(Component.literal("Unknown roll animation setting: " + setting));
                return 0;
            }
        }
        return saveRollAnimationSetting(source, manager, setting, Integer.toString(value));
    }

    private static int saveRollAnimationSetting(CommandSourceStack source, CrateConfigManager manager, String setting, String value) {
        if (!manager.saveRollAnimationSettings()) {
            source.sendFailure(Component.literal("Changed " + setting + " in memory, but failed to save settings.json."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Set rollAnimation." + setting + " to " + value + "."), true);
        return 1;
    }

    private static int cleanupHolograms(CommandSourceStack source, CrateConfigManager manager) {
        if (manager == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        int removed = CrateHologramManager.cleanupAll(manager.getServer(), manager);
        CrateHologramManager.refreshAllDimensions(manager);
        source.sendSuccess(() -> Component.literal("Removed " + removed + " crate hologram(s) and refreshed labels."), true);
        return 1;
    }

    private static int convert(CommandSourceStack source, CrateConfigManager manager, String type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (manager == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        BlockPos pos = CrateService.getTargetBlock(player, manager.getSettings().maxLookDistance);
        if (pos == null) {
            source.sendFailure(Component.literal("No block in range."));
            return 0;
        }
        return CrateService.convert(player, manager, type, pos) ? 1 : 0;
    }

    private static int unconvert(CommandSourceStack source, CrateConfigManager manager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (manager == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        BlockPos pos = CrateService.getTargetBlock(player, manager.getSettings().maxLookDistance);
        if (pos == null) {
            source.sendFailure(Component.literal("No block in range."));
            return 0;
        }
        return CrateService.unconvert(player, manager, pos) ? 1 : 0;
    }

    private static int giveKey(CommandSourceStack source, CrateConfigManager manager, String type, ServerPlayer target, int amount) {
        if (manager == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        KeyConfig key = manager.getKey(type);
        if (key == null) {
            source.sendFailure(Component.literal("Unknown key type: " + type));
            return 0;
        }
        var stack = KeyService.createKey(manager, type, amount);
        if (stack.isEmpty() || !target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        String template = manager.getSettings().messages.getOrDefault("giveKey", "&aGave &6{amount} &r{key}&a to &e{player}&a.");
        source.sendSuccess(() -> MessageUtil.format(template,
                "amount", String.valueOf(amount),
                "key", key.displayName,
                "player", target.getGameProfile().getName()), true);
        return 1;
    }

    private static int reload(CommandSourceStack source, CrateConfigManager manager) {
        if (manager == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        manager.reload();
        CrateHologramManager.refreshAllDimensions(manager);
        VotifierIntegration.initialize(manager.getServer(), manager);
        source.sendSuccess(() -> Component.literal("RuneVeil Crates config reloaded."), true);
        return 1;
    }

    private static int info(CommandSourceStack source, CrateConfigManager manager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (manager == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        BlockPos pos = CrateService.getTargetBlock(player, manager.getSettings().maxLookDistance);
        if (pos == null) {
            source.sendFailure(Component.literal("No block in range."));
            return 0;
        }
        String crateId = CrateService.getCrateAt(manager, player.serverLevel().dimension(), pos);
        if (crateId == null) {
            source.sendSuccess(() -> Component.literal("Block is not a crate."), false);
            return 1;
        }
        CrateDefinition crate = manager.getCrate(crateId);
        source.sendSuccess(() -> Component.literal("Crate: " + (crate != null ? crate.displayName : crateId) + " at " + CrateLocationKey.encode(player.serverLevel().dimension(), pos)), false);
        return 1;
    }

    private static int edit(CommandSourceStack source, CrateConfigManager manager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (manager == null) {
            source.sendFailure(Component.literal("Crate system is not loaded yet."));
            return 0;
        }
        BlockPos pos = CrateService.getTargetBlock(player, manager.getSettings().maxLookDistance);
        if (pos == null) {
            source.sendFailure(Component.literal("No block in range."));
            return 0;
        }
        return CrateEditorService.open(player, manager, pos) ? 1 : 0;
    }

    private static int validate(CommandSourceStack source, CrateConfigManager manager) {
        if (manager == null) return 0;
        var errors = manager.validateCurrent();
        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.literal("RuneVeil Crates configuration is valid.").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal("Configuration has " + errors.size() + " error(s):"));
        errors.forEach(error -> source.sendFailure(Component.literal("- " + error)));
        return 0;
    }

    private static int preview(CommandSourceStack source, CrateConfigManager manager, String type) {
        CrateDefinition crate = manager == null ? null : manager.getCrate(type);
        if (crate == null) { source.sendFailure(Component.literal("Unknown crate type: " + type)); return 0; }
        source.sendSuccess(() -> Component.literal(crate.displayName + " effective odds:"), false);
        RewardProbabilityService.probabilities(crate, manager.getSettings()).forEach((reward, chance) ->
                source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "- %s [%s]: %.3f%%", reward.displayName, reward.rarity, chance * 100.0)), false));
        return 1;
    }

    private static int giveKeyOffline(CommandSourceStack source, CrateConfigManager manager, String type, String uuidText, int amount) {
        try {
            UUID uuid = UUID.fromString(uuidText);
            if (!PendingKeyService.queue(manager, uuid, type, amount)) throw new IllegalArgumentException();
            source.sendSuccess(() -> Component.literal("Queued " + amount + " " + type + " key(s) for " + uuid + "."), true);
            return 1;
        } catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Invalid UUID, key type, or amount.")); return 0; }
    }

    private static int duplicate(CommandSourceStack source, CrateConfigManager manager, String oldId, String newId) {
        CrateDefinition crate = manager.duplicateCrate(oldId, newId);
        if (crate == null) { source.sendFailure(Component.literal("Could not duplicate crate; check IDs.")); return 0; }
        source.sendSuccess(() -> Component.literal("Duplicated " + oldId + " as " + crate.id + "."), true); return 1;
    }

    private static int rename(CommandSourceStack source, CrateConfigManager manager, String oldId, String newId) {
        if (!manager.renameCrate(oldId, newId)) { source.sendFailure(Component.literal("Could not rename crate; check IDs.")); return 0; }
        source.sendSuccess(() -> Component.literal("Renamed " + oldId + " to " + newId + "."), true); return 1;
    }

    private static int delete(CommandSourceStack source, CrateConfigManager manager, String type) {
        if (manager.getLocations().locations.containsValue(type)) { source.sendFailure(Component.literal("Unconvert all placed " + type + " crates before deleting it.")); return 0; }
        if (!manager.deleteCrate(type)) { source.sendFailure(Component.literal("Unknown crate type: " + type)); return 0; }
        source.sendSuccess(() -> Component.literal("Deleted crate " + type + "."), true); return 1;
    }

    private static int exportCrate(CommandSourceStack source, CrateConfigManager manager, String type) {
        var path = manager.exportCrate(type);
        if (path == null) { source.sendFailure(Component.literal("Unknown crate type: " + type)); return 0; }
        source.sendSuccess(() -> Component.literal("Exported to " + path), false); return 1;
    }

    private static int importCrate(CommandSourceStack source, CrateConfigManager manager, String file) {
        CrateDefinition crate = manager.importCrate(file);
        if (crate == null) { source.sendFailure(Component.literal("Import failed. Put a valid file in config/runeveilcrates/imports/.")); return 0; }
        source.sendSuccess(() -> Component.literal("Imported crate " + crate.id + "."), true); return 1;
    }

    private static int setOverride(CommandSourceStack source, CrateConfigManager manager, String type, String setting, String value) {
        CrateDefinition crate = manager.getCrate(type);
        if (crate == null) { source.sendFailure(Component.literal("Unknown crate type: " + type)); return 0; }
        boolean clear = "inherit".equalsIgnoreCase(value);
        try {
            switch (setting.toLowerCase(java.util.Locale.ROOT)) {
                case "consumeKey" -> crate.consumeKeyOnOpen = clear ? null : Boolean.parseBoolean(value);
                case "broadcastRare" -> crate.broadcastRareRewards = clear ? null : Boolean.parseBoolean(value);
                case "animation" -> crate.rollAnimationEnabled = clear ? null : Boolean.parseBoolean(value);
                case "pity" -> crate.pityPullsWithoutRarePlus = clear ? null : Math.max(1, Integer.parseInt(value));
                case "inventory" -> crate.inventoryFullPolicy = value;
                case "cooldown" -> crate.cooldownSeconds = clear ? -1 : Math.max(0, Integer.parseInt(value));
                default -> { source.sendFailure(Component.literal("Settings: consumeKey, broadcastRare, animation, pity, inventory, cooldown")); return 0; }
            }
            manager.saveCrate(crate);
            source.sendSuccess(() -> Component.literal("Set " + type + "." + setting + " to " + value + "."), true); return 1;
        } catch (RuntimeException e) { source.sendFailure(Component.literal("Invalid override value: " + value)); return 0; }
    }
}
