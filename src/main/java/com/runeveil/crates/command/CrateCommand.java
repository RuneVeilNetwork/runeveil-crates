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

public final class CrateCommand {
    private CrateCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<CrateConfigManager> configSupplier) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("crate")
                .then(Commands.literal("convert")
                        .requires(source -> source.hasPermission(2))
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
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> unconvert(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("givekey")
                        .requires(source -> source.hasPermission(2))
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
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> reload(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> info(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("edit")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> edit(ctx.getSource(), configSupplier.get())))
                .then(Commands.literal("settings")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("rollanimation")
                                .executes(ctx -> showRollAnimation(ctx.getSource(), configSupplier.get()))
                                .then(Commands.literal("enabled")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setRollAnimationEnabled(ctx.getSource(), configSupplier.get(), BoolArgumentType.getBool(ctx, "value")))))
                                .then(Commands.literal("ticksperstep")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 200))
                                                .executes(ctx -> setRollAnimationNumber(ctx.getSource(), configSupplier.get(), "ticksPerStep", IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.literal("minimumsteps")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> setRollAnimationNumber(ctx.getSource(), configSupplier.get(), "minimumSteps", IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.literal("finalholdticks")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 1200))
                                                .executes(ctx -> setRollAnimationNumber(ctx.getSource(), configSupplier.get(), "finalHoldTicks", IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(Commands.literal("cleanupholograms")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> cleanupHolograms(ctx.getSource(), configSupplier.get())));

        dispatcher.register(root);
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
                        + ", minimumSteps=" + roll.minimumSteps
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
            case "minimumSteps" -> manager.getSettings().rollAnimation.minimumSteps = value;
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
}
