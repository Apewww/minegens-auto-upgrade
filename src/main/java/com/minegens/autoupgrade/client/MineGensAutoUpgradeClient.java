package com.minegens.autoupgrade.client;

import com.minegens.autoupgrade.config.ModConfig;
import com.minegens.autoupgrade.logic.AutoUpgradeHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineGensAutoUpgradeClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineGensAutoUpgrade");
    private static int tickCounter = 0;
    private static boolean wasUPressed = false;
    private static boolean wasZPressed = false;
    private static boolean wasOPressed = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[MineGens Auto Upgrade] Client Initialized successfully!");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null) return;

            // Handle delayed slot clicks & container actions
            AutoUpgradeHandler.onClientTick();

            long window = 0;
            try {
                if (client.getWindow() != null) {
                    window = client.getWindow().getHandle();
                }
            } catch (Throwable ignored) {}

            ModConfig config = ModConfig.getInstance();

            // Detect key presses when window is valid and no chat/menu screen is open
            if (window != 0 && client.currentScreen == null) {
                boolean isUPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_U) == GLFW.GLFW_PRESS;
                boolean isZPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
                boolean isOPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;

                // Z Key - Master Mod Toggle (Enable / Disable entire mod)
                if (isZPressed && !wasZPressed) {
                    config.enabled = !config.enabled;
                    if (!config.enabled) {
                        config.autoLoopEnabled = false;
                        tickCounter = 0;
                    }
                    ModConfig.save();

                    client.player.sendMessage(
                            Text.literal("§a[MineGens] §fMaster Mod Status: " + (config.enabled ? "§a§lENABLED §7(Press [U] to start upgrade)" : "§c§lDISABLED §7(All features OFF - Press [Z] to enable)")),
                            false
                    );
                }
                wasZPressed = isZPressed;

                // U Key - Toggle Auto Upgrade Loop (Only functions when Mod is ENABLED)
                if (isUPressed && !wasUPressed) {
                    if (!config.enabled) {
                        client.player.sendMessage(
                                Text.literal("§c[MineGens] Mod is DISABLED! Press [Z] to enable first."),
                                false
                        );
                    } else {
                        config.autoLoopEnabled = !config.autoLoopEnabled;
                        ModConfig.save();
                        tickCounter = 0;

                        if (config.autoLoopEnabled) {
                            client.player.sendMessage(
                                    Text.literal("§a[MineGens] §fAuto Upgrade: §a§lSTARTED §7(Target: §e" + config.targetLevel + "§7, Delay: §e" + config.autoLoopIntervalSeconds + "s§7 - Press [U] to stop)"),
                                    false
                            );
                            AutoUpgradeHandler.triggerAutoUpgrade();
                        } else {
                            client.player.sendMessage(
                                    Text.literal("§a[MineGens] §fAuto Upgrade: §c§lSTOPPED"),
                                    false
                            );
                        }
                    }
                }
                wasUPressed = isUPressed;

                // O Key - Open Config GUI Settings Screen
                if (isOPressed && !wasOPressed) {
                    client.setScreen(new com.minegens.autoupgrade.client.gui.ModConfigScreen(null));
                }
                wasOPressed = isOPressed;
            } else {
                wasUPressed = false;
                wasZPressed = false;
                wasOPressed = false;
            }

            // Handle 5-second Auto Loop interval
            if (config.enabled && config.autoLoopEnabled) {
                tickCounter++;
                int targetTicks = Math.max(1, config.autoLoopIntervalSeconds) * 20; // 20 ticks = 1s
                if (tickCounter >= targetTicks) {
                    tickCounter = 0;
                    AutoUpgradeHandler.triggerAutoUpgrade();
                }
            } else {
                tickCounter = 0;
            }
        });

        // Register client command: /minegens
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("minegens")
                            .executes(context -> {
                                MinecraftClient client = MinecraftClient.getInstance();
                                client.send(() -> client.setScreen(new com.minegens.autoupgrade.client.gui.ModConfigScreen(null)));
                                return 1;
                            })
                            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("config")
                                    .executes(context -> {
                                        MinecraftClient client = MinecraftClient.getInstance();
                                        client.send(() -> client.setScreen(new com.minegens.autoupgrade.client.gui.ModConfigScreen(null)));
                                        return 1;
                                    })
                            )
                            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("delay")
                                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 60))
                                            .executes(context -> {
                                                int sec = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds");
                                                ModConfig config = ModConfig.getInstance();
                                                config.autoLoopIntervalSeconds = sec;
                                                ModConfig.save();
                                                context.getSource().sendFeedback(Text.literal("§a[MineGens] §fLoop interval set to: §e" + sec + "s"));
                                                return 1;
                                            })
                                    )
                            )
                            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("target")
                                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
                                            .executes(context -> {
                                                int lvl = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level");
                                                ModConfig config = ModConfig.getInstance();
                                                config.targetLevel = lvl;
                                                ModConfig.save();
                                                context.getSource().sendFeedback(Text.literal("§a[MineGens] §fTarget generator level set to: §e" + lvl));
                                                return 1;
                                            })
                                    )
                            )
                            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("enable")
                                    .executes(context -> {
                                        ModConfig config = ModConfig.getInstance();
                                        config.enabled = true;
                                        ModConfig.save();
                                        context.getSource().sendFeedback(Text.literal("§a[MineGens] §fMaster Mod Status: §a§lENABLED §7(Press [U] to start)"));
                                        return 1;
                                    })
                            )
                            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("disable")
                                    .executes(context -> {
                                        ModConfig config = ModConfig.getInstance();
                                        config.enabled = false;
                                        config.autoLoopEnabled = false;
                                        ModConfig.save();
                                        context.getSource().sendFeedback(Text.literal("§a[MineGens] §fMaster Mod Status: §c§lDISABLED §7(All features OFF)"));
                                        return 1;
                                    })
                            )
                            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("toggle")
                                    .executes(context -> {
                                        ModConfig config = ModConfig.getInstance();
                                        if (!config.enabled) {
                                            context.getSource().sendFeedback(Text.literal("§c[MineGens] Mod is DISABLED! Use '/minegens enable' or press [Z] to enable."));
                                            return 0;
                                        }
                                        config.autoLoopEnabled = !config.autoLoopEnabled;
                                        ModConfig.save();
                                        if (config.autoLoopEnabled) {
                                            context.getSource().sendFeedback(Text.literal("§a[MineGens] §fAuto Upgrade: §a§lSTARTED §7(Target: §e" + config.targetLevel + "§7, Delay: §e" + config.autoLoopIntervalSeconds + "s§7)"));
                                            AutoUpgradeHandler.triggerAutoUpgrade();
                                        } else {
                                            context.getSource().sendFeedback(Text.literal("§a[MineGens] §fAuto Upgrade: §c§lSTOPPED"));
                                        }
                                        return 1;
                                    })
                            )
            );
        });
    }
}
