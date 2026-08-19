package com.minegens.autoupgrade.logic;

import com.minegens.autoupgrade.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoUpgradeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineGensAutoUpgrade");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("(?i)(?:generator\\s+)?level\\s*[:\\s#]*(\\d+)");

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
                if (client.player.networkHandler != null) {
                    client.player.networkHandler.sendChatMessage("/" + cmd);
                }
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
                clickDelayTicks = Math.max(1, config.clickDelayTicks);
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
        ModConfig config = ModConfig.getInstance();
        if (!config.enabled) {
            clickDelayTicks = 0;
            currentHandler = null;
            return;
        }

        if (clickDelayTicks > 0) {
            clickDelayTicks--;
            if (clickDelayTicks == 0 && currentHandler != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null && client.interactionManager != null) {
                    int syncId = currentHandler.syncId;

                    // 1. Check current generator level
                    int currentLevel = extractGeneratorLevel(currentHandler, config);

                    if (currentLevel > 0) {
                        LOGGER.info("[MineGens Auto Upgrade] Detected Generator Level: {} (Target: {})", currentLevel, config.targetLevel);

                        // If target level is reached
                        if (config.stopAtTargetLevel && currentLevel >= config.targetLevel) {
                            config.autoLoopEnabled = false;
                            ModConfig.save();

                            client.player.sendMessage(
                                    Text.literal("§a[MineGens] §6§lTARGET REACHED! §fGenerator Level §e" + currentLevel + " §fhas reached target §e" + config.targetLevel + "§6."),
                                    false
                            );

                            if (shouldCloseScreen) {
                                client.player.closeHandledScreen();
                            }
                            currentHandler = null;

                            // If auto-rebuild is enabled, begin breaking 2x2 & replacing with new generators
                            if (config.autoRebuild) {
                                AutoRebuildHandler.startRebuild();
                            } else {
                                client.player.sendMessage(
                                        Text.literal("§a[MineGens] §7Auto Upgrade stopped."),
                                        false
                                );
                            }
                            return;
                        }
                    }

                    // 2. Automatically detect Emerald / Upgrade All slot
                    int targetSlot = config.targetSlotId; // Default 51
                    try {
                        for (int i = 0; i < currentHandler.slots.size(); i++) {
                            Slot slot = currentHandler.getSlot(i);
                            if (slot != null && slot.hasStack()) {
                                ItemStack stack = slot.getStack();
                                if (!stack.isEmpty()) {
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

                    // 3. Send the click packet to the detected Emerald slot
                    client.interactionManager.clickSlot(
                            syncId,
                            targetSlot,
                            0, // Left click
                            SlotActionType.PICKUP,
                            client.player
                    );

                    if (config.sendNotification) {
                        if (currentLevel > 0) {
                            client.player.sendMessage(
                                    Text.literal("§a[MineGens] §eAuto Upgraded! §7(Level: §f" + currentLevel + "§7/§e" + config.targetLevel + "§7 | Slot: " + targetSlot + ")"),
                                    false
                            );
                        } else {
                            client.player.sendMessage(
                                    Text.literal("§a[MineGens] §eAuto Upgraded! §7(Clicked Emerald Slot ID: " + targetSlot + ")"),
                                    false
                            );
                        }
                    }

                    if (shouldCloseScreen) {
                        client.player.closeHandledScreen();
                    }
                }

                currentHandler = null;
            }
        }
    }

    /**
     * Extracts generator level from slot items using regex matching on lore and tooltips.
     */
    public static int extractGeneratorLevel(ScreenHandler handler, ModConfig config) {
        if (handler == null) return -1;

        // Try configured checkGeneratorSlot first (default slot 1)
        int targetSlot = config.checkGeneratorSlot;
        if (targetSlot >= 0 && targetSlot < handler.slots.size()) {
            Slot slot = handler.getSlot(targetSlot);
            if (slot != null && slot.hasStack()) {
                int level = parseLevelFromStack(slot.getStack());
                if (level > 0) return level;
            }
        }

        // Try slot 0 if slot 1 was checked or vice versa
        int altSlot = (targetSlot == 1) ? 0 : 1;
        if (altSlot >= 0 && altSlot < handler.slots.size()) {
            Slot slot = handler.getSlot(altSlot);
            if (slot != null && slot.hasStack()) {
                int level = parseLevelFromStack(slot.getStack());
                if (level > 0) return level;
            }
        }

        // Scan all container slots (slots 0..35) to find generator item
        int maxScan = Math.min(36, handler.slots.size());
        for (int i = 0; i < maxScan; i++) {
            if (i == targetSlot || i == altSlot) continue;
            Slot slot = handler.getSlot(i);
            if (slot != null && slot.hasStack()) {
                int level = parseLevelFromStack(slot.getStack());
                if (level > 0) return level;
            }
        }

        return -1;
    }

    private static int parseLevelFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;

        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Check ItemStack tooltips
        try {
            if (client != null && client.player != null) {
                Item.TooltipContext tooltipContext = Item.TooltipContext.DEFAULT;
                if (client.world != null) {
                    tooltipContext = Item.TooltipContext.create(client.world);
                }
                List<Text> tooltips = stack.getTooltip(tooltipContext, client.player, TooltipType.BASIC);
                if (tooltips != null) {
                    for (Text line : tooltips) {
                        int level = extractLevelFromText(line.getString());
                        if (level > 0) return level;
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("Failed getting tooltip: ", e);
        }

        // 2. Check Item Name
        try {
            int level = extractLevelFromText(stack.getName().getString());
            if (level > 0) return level;
        } catch (Throwable ignored) {}

        // 3. Inspect Data Component Lore directly (1.20.5+ / 1.21.x)
        try {
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore != null) {
                for (Text line : lore.lines()) {
                    int level = extractLevelFromText(line.getString());
                    if (level > 0) return level;
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("Failed reading Lore Component: ", e);
        }

        return -1;
    }

    private static int extractLevelFromText(String text) {
        if (text == null || text.isEmpty()) return -1;

        // Strip formatting codes (e.g. §a, §l, etc.)
        String clean = text.replaceAll("(?i)§[0-9a-fk-or]", "").trim();

        Matcher matcher = LEVEL_PATTERN.matcher(clean);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
}

