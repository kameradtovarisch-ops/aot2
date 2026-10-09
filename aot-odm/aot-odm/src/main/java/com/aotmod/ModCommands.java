package com.aotmod;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class ModCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("aot")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("trees")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            AotMod.fallingTrees = BoolArgumentType.getBool(ctx, "enabled");
                                            ctx.getSource().sendFeedback(() -> Text.literal("Falling trees: " + AotMod.fallingTrees), true);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("spawns")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            AotMod.naturalTitans = BoolArgumentType.getBool(ctx, "enabled");
                                            ctx.getSource().sendFeedback(() -> Text.literal("Natural titan spawns: " + AotMod.naturalTitans), true);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("gas")
                                .executes(ctx -> {
                                    OdmManager.refill(ctx.getSource().getPlayerOrThrow());
                                    ctx.getSource().sendFeedback(() -> Text.literal("Gas refilled"), false);
                                    return 1;
                                }))));
    }
}
