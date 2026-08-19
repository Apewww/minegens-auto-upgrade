package com.minegens.autoupgrade.logic;

import com.minegens.autoupgrade.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class AutoRebuildHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineGensAutoRebuild");

    public enum RebuildState {
        IDLE,
        BREAKING,
        WAIT_AFTER_BREAK,
        PLACING
    }

    public static class PlaceAction {
        public final BlockPos targetPos;
        public final BlockPos clickedPos;
        public final Direction direction;

        public PlaceAction(BlockPos targetPos, BlockPos clickedPos, Direction direction) {
            this.targetPos = targetPos;
            this.clickedPos = clickedPos;
            this.direction = direction;
        }
    }

    private static RebuildState state = RebuildState.IDLE;
    private static final Deque<BlockPos> breakQueue = new ArrayDeque<>();
    private static final Deque<PlaceAction> placeQueue = new ArrayDeque<>();
    private static int delayTicks = 0;
    private static int totalToBreak = 0;
    private static int totalToPlace = 0;
    private static int brokenCount = 0;
    private static int placedCount = 0;
    private static int breakAttemptsForCurrentBlock = 0;
    private static int placeAttemptsForCurrentBlock = 0;
    private static int verificationPasses = 0;

    // Cache of the last detected 2x2 area
    private static int cachedMinX = 0, cachedMaxX = 0;
    private static int cachedMinZ = 0, cachedMaxZ = 0;
    private static int cachedTopY = 0;
    private static boolean hasCachedArea = false;

    public static boolean isRebuilding() {
        return state != RebuildState.IDLE;
    }

    public static RebuildState getState() {
        return state;
    }

    public static void cancelRebuild(String reason) {
        if (state != RebuildState.IDLE) {
            state = RebuildState.IDLE;
            breakQueue.clear();
            placeQueue.clear();
            delayTicks = 0;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.sendMessage(
                        Text.literal("§a[MineGens] §c§lAuto Rebuild CANCELLED: §f" + (reason != null ? reason : "Manually stopped.")),
                        false
                );
            }
        }
    }

    /**
     * Attempts to find and store the 2x2 pillar area below/near the player or crosshair.
     */
    public static boolean detect2x2Area() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return false;

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        BlockPos refPos = null;

        // 1. Try crosshair target if aiming at a block
        if (client.crosshairTarget instanceof BlockHitResult blockHit && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            refPos = blockHit.getBlockPos();
        }

        // 2. Fallback: check below player's feet
        if (refPos == null || world.isAir(refPos)) {
            BlockPos feet = player.getBlockPos();
            if (!world.isAir(feet.down())) {
                refPos = feet.down();
            } else if (!world.isAir(feet.down(2))) {
                refPos = feet.down(2);
            } else if (!world.isAir(feet)) {
                refPos = feet;
            }
        }

        if (refPos == null) {
            LOGGER.warn("[MineGens Auto Rebuild] No block found near player/crosshair for 2x2 detection.");
            return false;
        }

        int px = refPos.getX();
        int py = refPos.getY();
        int pz = refPos.getZ();

        // Check 4 candidate 2x2 boxes around px, pz
        int[][] offsets = {
                {0, 1, 0, 1},   // (px, px+1), (pz, pz+1)
                {-1, 0, 0, 1},  // (px-1, px), (pz, pz+1)
                {0, 1, -1, 0},  // (px, px+1), (pz-1, pz)
                {-1, 0, -1, 0}  // (px-1, px), (pz-1, pz)
        };

        int bestMinX = px, bestMaxX = px + 1;
        int bestMinZ = pz, bestMaxZ = pz + 1;
        double bestDist = Double.MAX_VALUE;
        boolean foundSolid = false;

        for (int[] off : offsets) {
            int minX = px + off[0];
            int maxX = px + off[1];
            int minZ = pz + off[2];
            int maxZ = pz + off[3];

            boolean allSolid = !world.isAir(new BlockPos(minX, py, minZ)) &&
                    !world.isAir(new BlockPos(maxX, py, minZ)) &&
                    !world.isAir(new BlockPos(minX, py, maxZ)) &&
                    !world.isAir(new BlockPos(maxX, py, maxZ));

            double centerX = (minX + maxX + 1) / 2.0;
            double centerZ = (minZ + maxZ + 1) / 2.0;
            double dist = Math.hypot(player.getX() - centerX, player.getZ() - centerZ);

            if (allSolid) {
                if (!foundSolid || dist < bestDist) {
                    foundSolid = true;
                    bestDist = dist;
                    bestMinX = minX;
                    bestMaxX = maxX;
                    bestMinZ = minZ;
                    bestMaxZ = maxZ;
                }
            } else if (!foundSolid && dist < bestDist) {
                bestDist = dist;
                bestMinX = minX;
                bestMaxX = maxX;
                bestMinZ = minZ;
                bestMaxZ = maxZ;
            }
        }

        cachedMinX = bestMinX;
        cachedMaxX = bestMaxX;
        cachedMinZ = bestMinZ;
        cachedMaxZ = bestMaxZ;
        cachedTopY = py;
        hasCachedArea = true;

        LOGGER.info("[MineGens Auto Rebuild] 2x2 Area detected: X[{}, {}], Z[{}, {}], TopY: {}",
                cachedMinX, cachedMaxX, cachedMinZ, cachedMaxZ, cachedTopY);
        return true;
    }

    /**
     * Starts the auto rebuild process:
     * 1. Breaks 2x2 column (top-down) for total blocks (e.g. 8 layers = 32 blocks).
     * 2. Places configured generator item (bottom-up) in the 2x2 column.
     * 3. Automatically resumes auto-upgrade if configured.
     */
    public static void startRebuild() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return;

        ModConfig config = ModConfig.getInstance();

        // Refresh or verify 2x2 detection
        if (!detect2x2Area() && !hasCachedArea) {
            client.player.sendMessage(
                    Text.literal("§a[MineGens] §c§lAuto Rebuild Error: §fCould not detect 2x2 generator column beneath you! Please stand/aim above the 2x2 column."),
                    false
            );
            return;
        }

        // Verify player has at least some generator items in inventory before destroying
        int availableGens = countItemInInventory(config.rebuildItemFilter);
        if (availableGens <= 0) {
            client.player.sendMessage(
                    Text.literal("§a[MineGens] §c§lAuto Rebuild Aborted: §fNo §e\"" + config.rebuildItemFilter + "\" §ffound in inventory! Please carry generators to place."),
                    false
            );
            return;
        }

        breakQueue.clear();
        placeQueue.clear();

        int height = Math.max(1, config.rebuildHeight); // Default 8 layers = 32 blocks
        int topY = cachedTopY;
        int bottomY = topY - height + 1;

        // Build Break Queue: Top to Bottom (Layer 8 down to Layer 1)
        for (int y = topY; y >= bottomY; y--) {
            breakQueue.add(new BlockPos(cachedMinX, y, cachedMinZ));
            breakQueue.add(new BlockPos(cachedMaxX, y, cachedMinZ));
            breakQueue.add(new BlockPos(cachedMinX, y, cachedMaxZ));
            breakQueue.add(new BlockPos(cachedMaxX, y, cachedMaxZ));
        }

        // Build Place Queue: Bottom to Top (Layer 1 up to Layer 8)
        for (int y = bottomY; y <= topY; y++) {
            // Layer y sits on top of layer (y - 1), so we interact with block at (y - 1) face UP
            placeQueue.add(new PlaceAction(
                    new BlockPos(cachedMinX, y, cachedMinZ),
                    new BlockPos(cachedMinX, y - 1, cachedMinZ),
                    Direction.UP
            ));
            placeQueue.add(new PlaceAction(
                    new BlockPos(cachedMaxX, y, cachedMinZ),
                    new BlockPos(cachedMaxX, y - 1, cachedMinZ),
                    Direction.UP
            ));
            placeQueue.add(new PlaceAction(
                    new BlockPos(cachedMinX, y, cachedMaxZ),
                    new BlockPos(cachedMinX, y - 1, cachedMaxZ),
                    Direction.UP
            ));
            placeQueue.add(new PlaceAction(
                    new BlockPos(cachedMaxX, y, cachedMaxZ),
                    new BlockPos(cachedMaxX, y - 1, cachedMaxZ),
                    Direction.UP
            ));
        }

        totalToBreak = breakQueue.size();
        totalToPlace = placeQueue.size();
        brokenCount = 0;
        placedCount = 0;
        breakAttemptsForCurrentBlock = 0;
        placeAttemptsForCurrentBlock = 0;
        verificationPasses = 0;
        delayTicks = 2;
        state = RebuildState.BREAKING;

        client.player.sendMessage(
                Text.literal("§a[MineGens] §6§lAUTO REBUILD STARTED! §fBreaking §e" + totalToBreak + " §fblocks (2x2 x " + height + " layers)..."),
                false
        );
    }

    public static void onClientTick() {
        if (state == RebuildState.IDLE) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            cancelRebuild("Game client or world not available.");
            return;
        }

        ModConfig config = ModConfig.getInstance();

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        double centerX = (cachedMinX + cachedMaxX + 1) / 2.0;
        double centerZ = (cachedMinZ + cachedMaxZ + 1) / 2.0;

        // 1. BREAKING PHASE
        if (state == RebuildState.BREAKING) {
            if (breakQueue.isEmpty()) {
                state = RebuildState.WAIT_AFTER_BREAK;
                delayTicks = 6; // Brief pause to ensure all server drops/block updates sync
                client.player.sendMessage(
                        Text.literal("§a[MineGens] §fAll §e" + brokenCount + " §fblocks broken! Now placing §e" + config.rebuildItemFilter + "§f..."),
                        false
                );
                return;
            }

            BlockPos targetBlock = breakQueue.peek();

            // Smoothly hover player above current breaking layer
            double targetPlayerY = targetBlock.getY() + 1.25;
            movePlayerTowards(client.player, centerX, targetPlayerY, centerZ);

            // If already air, move to next
            if (client.world.isAir(targetBlock)) {
                breakQueue.poll();
                brokenCount++;
                breakAttemptsForCurrentBlock = 0;
                delayTicks = Math.max(1, config.breakDelayTicks);
                return;
            }

            // Aim camera smoothly at target block (Baritone style)
            if (config.smoothCameraAim) {
                Vec3d aimTarget = new Vec3d(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);
                aimAt(client.player, aimTarget, config.cameraAimSpeed);
            }

            // Attack & break block
            client.interactionManager.attackBlock(targetBlock, Direction.UP);
            client.interactionManager.updateBlockBreakingProgress(targetBlock, Direction.UP);
            client.player.swingHand(Hand.MAIN_HAND);

            breakAttemptsForCurrentBlock++;
            // If instant-break didn't clear block within 15 attempts, skip to prevent stalling
            if (breakAttemptsForCurrentBlock > 15) {
                LOGGER.warn("[MineGens Auto Rebuild] Skipping stubborn block at {}", targetBlock);
                breakQueue.poll();
                breakAttemptsForCurrentBlock = 0;
            }

            delayTicks = Math.max(1, config.breakDelayTicks);
            return;
        }

        // 2. WAIT PHASE BETWEEN BREAK & PLACE
        if (state == RebuildState.WAIT_AFTER_BREAK) {
            state = RebuildState.PLACING;
            delayTicks = 2;
            return;
        }

        // 3. PLACING PHASE
        if (state == RebuildState.PLACING) {
            if (placeQueue.isEmpty()) {
                // Verification pass: sweep all 32 blocks from bottomY up to topY
                int height = Math.max(1, config.rebuildHeight);
                int topY = cachedTopY;
                int bottomY = topY - height + 1;

                java.util.List<PlaceAction> missingBlocks = new java.util.ArrayList<>();
                for (int y = bottomY; y <= topY; y++) {
                    BlockPos p1 = new BlockPos(cachedMinX, y, cachedMinZ);
                    BlockPos p2 = new BlockPos(cachedMaxX, y, cachedMinZ);
                    BlockPos p3 = new BlockPos(cachedMinX, y, cachedMaxZ);
                    BlockPos p4 = new BlockPos(cachedMaxX, y, cachedMaxZ);

                    if (client.world.isAir(p1)) missingBlocks.add(new PlaceAction(p1, p1.down(), Direction.UP));
                    if (client.world.isAir(p2)) missingBlocks.add(new PlaceAction(p2, p2.down(), Direction.UP));
                    if (client.world.isAir(p3)) missingBlocks.add(new PlaceAction(p3, p3.down(), Direction.UP));
                    if (client.world.isAir(p4)) missingBlocks.add(new PlaceAction(p4, p4.down(), Direction.UP));
                }

                if (!missingBlocks.isEmpty() && verificationPasses < 5) {
                    verificationPasses++;
                    LOGGER.info("[MineGens Auto Rebuild] Found {} missing blocks during verification pass {}. Re-placing missing blocks...", missingBlocks.size(), verificationPasses);
                    placeQueue.addAll(missingBlocks);
                    delayTicks = 3;
                    return;
                }

                state = RebuildState.IDLE;

                int finalPlaced = totalToPlace - missingBlocks.size();
                client.player.sendMessage(
                        Text.literal("§a[MineGens] §a§lREBUILD COMPLETE! §fSuccessfully placed §e" + finalPlaced + "/" + totalToPlace + " §fgenerators."),
                        false
                );

                if (config.autoRestartAfterRebuild) {
                    config.autoLoopEnabled = true;
                    ModConfig.save();

                    client.player.sendMessage(
                            Text.literal("§a[MineGens] §fResuming Auto Upgrade! §7(Target: Level §e" + config.targetLevel + "§7 - Press [U] to stop)"),
                            false
                    );

                    AutoUpgradeHandler.triggerAutoUpgrade();
                }
                return;
            }

            PlaceAction action = placeQueue.peek();

            // Automatically lift/ascend player above the layer being placed so player never gets trapped
            double targetPlayerY = action.targetPos.getY() + 1.25;
            movePlayerTowards(client.player, centerX, targetPlayerY, centerZ);

            // If player is still below the placement layer, elevate before placing
            if (client.player.getY() < action.targetPos.getY() + 0.6) {
                client.player.setPosition(centerX, targetPlayerY, centerZ);
                client.player.setVelocity(0, 0.2, 0);
            }

            // If block at target is already solid (placed), advance
            if (!client.world.isAir(action.targetPos)) {
                placeQueue.poll();
                placedCount++;
                placeAttemptsForCurrentBlock = 0;
                delayTicks = Math.max(1, config.placeDelayTicks);
                return;
            }

            // Ensure generator item is selected in hotbar
            boolean readyToPlace = equipItem(config.rebuildItemFilter);
            if (!readyToPlace) {
                cancelRebuild("Out of " + config.rebuildItemFilter + " in inventory!");
                return;
            }

            // Aim camera smoothly at placement surface (Baritone style)
            if (config.smoothCameraAim) {
                Vec3d aimTarget = new Vec3d(
                        action.clickedPos.getX() + 0.5,
                        action.clickedPos.getY() + 0.95,
                        action.clickedPos.getZ() + 0.5
                );
                boolean aimed = aimAt(client.player, aimTarget, config.cameraAimSpeed);
                if (!aimed) {
                    // Wait 1 tick for the camera to finish pointing directly at the block before clicking
                    delayTicks = 1;
                    return;
                }
            }

            // Place block on top face of clickedPos
            Vec3d hitVec = new Vec3d(
                    action.clickedPos.getX() + 0.5,
                    action.clickedPos.getY() + 1.0,
                    action.clickedPos.getZ() + 0.5
            );
            BlockHitResult hitResult = new BlockHitResult(hitVec, action.direction, action.clickedPos, false);

            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
            client.player.swingHand(Hand.MAIN_HAND);

            placeAttemptsForCurrentBlock++;
            // If after 3 attempts the block hasn't synced, advance and let verification sweep catch it
            if (placeAttemptsForCurrentBlock >= 3) {
                placeQueue.poll();
                placeAttemptsForCurrentBlock = 0;
            }

            delayTicks = Math.max(1, config.placeDelayTicks);
        }
    }

    /**
     * Smoothly moves/elevates player towards the target position (for scaffold elevator placement).
     */
    public static void movePlayerTowards(ClientPlayerEntity player, double targetX, double targetY, double targetZ) {
        if (player == null) return;

        double curX = player.getX();
        double curY = player.getY();
        double curZ = player.getZ();

        double diffX = targetX - curX;
        double diffY = targetY - curY;
        double diffZ = targetZ - curZ;

        // Auto activate flight if available
        if (player.getAbilities().allowFlying && !player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        }

        double stepX = net.minecraft.util.math.MathHelper.clamp(diffX * 0.4, -0.2, 0.2);
        double stepY = net.minecraft.util.math.MathHelper.clamp(diffY * 0.5, -0.4, 0.4);
        double stepZ = net.minecraft.util.math.MathHelper.clamp(diffZ * 0.4, -0.2, 0.2);

        if (Math.abs(diffX) > 0.03 || Math.abs(diffY) > 0.03 || Math.abs(diffZ) > 0.03) {
            player.setPosition(curX + stepX, curY + stepY, curZ + stepZ);
            player.setVelocity(stepX, stepY, stepZ);
        }
    }

    /**
     * Smoothly rotates player camera angles towards target vector (Baritone-like movement).
     */
    public static boolean aimAt(ClientPlayerEntity player, Vec3d targetVec, float maxDegreesPerTick) {
        if (player == null || targetVec == null) return false;

        Vec3d eyePos = player.getEyePos();
        double dx = targetVec.x - eyePos.x;
        double dy = targetVec.y - eyePos.y;
        double dz = targetVec.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        float yawDiff = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        if (maxDegreesPerTick <= 0 || (Math.abs(yawDiff) <= maxDegreesPerTick && Math.abs(pitchDiff) <= maxDegreesPerTick)) {
            player.setYaw(targetYaw);
            player.setPitch(net.minecraft.util.math.MathHelper.clamp(targetPitch, -90.0f, 90.0f));
            player.headYaw = targetYaw;
            player.bodyYaw = targetYaw;
            return true;
        }

        float stepYaw = net.minecraft.util.math.MathHelper.clamp(yawDiff, -maxDegreesPerTick, maxDegreesPerTick);
        float stepPitch = net.minecraft.util.math.MathHelper.clamp(pitchDiff, -maxDegreesPerTick, maxDegreesPerTick);

        player.setYaw(currentYaw + stepYaw);
        player.setPitch(net.minecraft.util.math.MathHelper.clamp(currentPitch + stepPitch, -90.0f, 90.0f));
        player.headYaw = player.getYaw();
        player.bodyYaw = player.getYaw();

        return Math.abs(yawDiff) < 25.0f && Math.abs(pitchDiff) < 25.0f;
    }

    /**
     * Finds matching item in hotbar or inventory, and selects/swaps it to active hotbar slot.
     */
    private static boolean equipItem(String filter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return false;

        ClientPlayerEntity player = client.player;

        // 1. Check if current selected slot already holds the item
        ItemStack currentStack = player.getMainHandStack();
        if (isMatchingItem(currentStack, filter)) {
            return true;
        }

        // 2. Search hotbar slots (0..8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isMatchingItem(stack, filter)) {
                player.getInventory().setSelectedSlot(i);
                if (player.networkHandler != null) {
                    player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(i));
                }
                return true;
            }
        }

        // 3. Search main inventory slots (9..35) and swap into current hotbar slot
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isMatchingItem(stack, filter)) {
                int currentHotbar = player.getInventory().getSelectedSlot();
                if (client.interactionManager != null) {
                    // In playerScreenHandler, main inventory slots are 9..35
                    client.interactionManager.clickSlot(
                            0, // playerScreenHandler syncId = 0
                            i,
                            currentHotbar, // hotbar target button 0..8
                            SlotActionType.SWAP,
                            player
                    );
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Counts total count of matching items in player's entire inventory.
     */
    public static int countItemInInventory(String filter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return 0;

        int count = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (isMatchingItem(stack, filter)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Checks if ItemStack matches the generator filter.
     */
    public static boolean isMatchingItem(ItemStack stack, String filter) {
        if (stack == null || stack.isEmpty()) return false;

        if (filter == null || filter.trim().isEmpty()) {
            return stack.getName().getString().toLowerCase().contains("generator");
        }

        String lowerFilter = filter.trim().toLowerCase();
        String name = stack.getName().getString().toLowerCase();

        // 1. Direct name match
        if (name.contains(lowerFilter)) {
            return true;
        }

        // 2. Special case for "Wheat Generator" -> Hay block item
        if ((lowerFilter.contains("wheat") || lowerFilter.contains("hay")) && stack.isOf(Items.HAY_BLOCK)) {
            return true;
        }

        // 3. Inspect lore data component (1.20.5+ / 1.21.x)
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                if (line.getString().toLowerCase().contains(lowerFilter)) {
                    return true;
                }
            }
        }

        // 4. Inspect tooltips
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                Item.TooltipContext ctx = client.world != null ? Item.TooltipContext.create(client.world) : Item.TooltipContext.DEFAULT;
                List<Text> tooltips = stack.getTooltip(ctx, client.player, TooltipType.BASIC);
                if (tooltips != null) {
                    for (Text line : tooltips) {
                        if (line.getString().toLowerCase().contains(lowerFilter)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
