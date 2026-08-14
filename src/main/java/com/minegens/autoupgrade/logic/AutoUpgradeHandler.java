package com.minegens.autoupgrade.logic;

import com.minegens.autoupgrade.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoUpgradeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineGensAutoUpgrade");
    private static ScreenHandler currentHandler = null;
    private static int clickDelayTicks = 0;
    private static boolean shouldCloseScreen = false;
    private static boolean isSuppressingScreen = false;

    public static void triggerAutoUpgrade() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.enabled) return;

        String cmd = config.command != null ? config.command.trim() : "/upgradegen";
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }

        try {
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendChatCommand(cmd);
            }
        } catch (Throwable e) {
            try {
                client.player.networkHandler.sendChatMessage("/" + cmd);
            } catch (Throwable ex) {
                LOGGER.error("Failed to send command: ", ex);
            }
        }

        if (config.sendNotification) {
            client.player.sendMessage(Text.literal("§a[MineGens] §fSending command §e/" + cmd + "§f..."), false);
        }
    }

    public static void onScreenOpened(Screen screen) {
        if (isSuppressingScreen) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.enabled || screen == null) return;

        if (screen instanceof HandledScreen<?> handledScreen) {
            ScreenHandler handler = handledScreen.getScreenHandler();
            if (handler != null) {
                currentHandler = handler;
                clickDelayTicks = 2; // 2 ticks (100ms) to ensure slot items from server packet are loaded
                shouldCloseScreen = config.autoCloseScreen;

                if (config.hidePanel) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        isSuppressingScreen = true;
                        try {
                            client.setScreen(null);
                        } finally {
                            isSuppressingScreen = false;
                        }
                    }
                }
            }
        }
    }

    public static void onClientTick() {
        if (clickDelayTicks > 0) {
            clickDelayTicks--;
            if (clickDelayTicks == 0 && currentHandler != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null && client.interactionManager != null) {
                    ModConfig config = ModConfig.getInstance();
                    int syncId = currentHandler.syncId;

                    // Automatically detect Emerald / Upgrade All slot
                    int targetSlot = config.targetSlotId; // Default 51
                    try {
                        for (int i = 0; i < currentHandler.slots.size(); i++) {
                            Slot slot = currentHandler.getSlot(i);
                            if (slot != null && slot.hasStack()) {
                                ItemStack stack = slot.getStack();
                                if (!stack.isEmpty()) {
                                    // Check if item is Emerald or has "Upgrade All" in name
                                    String name = stack.getName().getString();
                                    if (stack.isOf(Items.EMERALD) || name.toLowerCase().contains("upgrade all")) {
                                        targetSlot = i;
                                        LOGGER.info("[MineGens Auto Upgrade] Detected Upgrade All slot at: {}", i);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        LOGGER.warn("Slot auto-detection fallback to default targetSlotId: ", e);
                    }

                    // Send the click packet to the detected Emerald slot
                    client.interactionManager.clickSlot(
                            syncId,
                            targetSlot,
                            0, // Left click
                            SlotActionType.PICKUP,
                            client.player
                    );

                    if (config.sendNotification) {
                        client.player.sendMessage(
                                Text.literal("§a[MineGens] §eAuto Upgraded! §7(Clicked Emerald Slot ID: " + targetSlot + ")"),
                                false
                        );
                    }

                    if (shouldCloseScreen) {
                        client.player.closeHandledScreen();
                    }
                }

                currentHandler = null;
            }
        }
    }
}
