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
                boolean isOPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;

                // U Key - Toggle 5s Auto Loop
                if (isUPressed && !wasUPressed) {
                    if (!config.enabled) {
                        client.player.sendMessage(Text.literal("§c[MineGens] Mod is DISABLED! Press [O] to enable."), false);
                    } else {
                        config.autoLoopEnabled = !config.autoLoopEnabled;
                        ModConfig.save();
                        tickCounter = 0;

                        if (config.autoLoopEnabled) {
                            client.player.sendMessage(
                                    Text.literal("§a[MineGens] §fAuto Loop: §a§lSTARTED §7(Every " + config.autoLoopIntervalSeconds + "s - Press U to stop)"),
                                    false
                            );
                            AutoUpgradeHandler.triggerAutoUpgrade();
                        } else {
                            client.player.sendMessage(
                                    Text.literal("§a[MineGens] §fAuto Loop: §c§lSTOPPED"),
                                    false
                            );
                        }
                    }
                }
                wasUPressed = isUPressed;

                // O Key - Master Toggle (ON / OFF)
                if (isOPressed && !wasOPressed) {
                    config.enabled = !config.enabled;
                    if (!config.enabled) {
                        config.autoLoopEnabled = false;
                    }
                    ModConfig.save();

                    client.player.sendMessage(
                            Text.literal("§a[MineGens] §fMaster Mod Status: " + (config.enabled ? "§a§lENABLED" : "§c§lDISABLED")),
                            false
                    );
                }
                wasOPressed = isOPressed;
            } else {
                wasUPressed = false;
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
    }
}
