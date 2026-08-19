package com.minegens.autoupgrade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineGensAutoUpgrade-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("minegens_auto_upgrade.json")
            .toFile();

    private static ModConfig INSTANCE = new ModConfig();

    // Configuration Fields
    public boolean enabled = true;
    public boolean hidePanel = true;
    public int targetSlotId = 51; // Emerald (Upgrade All) in MineGens is Slot 51
    public String command = "/upgradegen";
    public boolean autoCloseScreen = true;
    public boolean sendNotification = true;
    public int autoLoopIntervalSeconds = 5;
    public boolean autoLoopEnabled = false;
    public int targetLevel = 50; // Target generator level to stop upgrading
    public boolean stopAtTargetLevel = true; // Auto stop when target level is reached
    public int checkGeneratorSlot = 1; // Slot ID where generator is located (default 1)
    public int clickDelayTicks = 2; // Delay in ticks before clicking upgrade slot

    // Auto Rebuild (Break 2x2 & Place Generators) Configuration
    public boolean autoRebuild = true; // Auto break 2x2 and replace with new generators when target is reached
    public String rebuildItemFilter = "Wheat Generator"; // Keyword/Name of generator item in inventory
    public int rebuildHeight = 8; // Height in layers (8 layers * 4 = 32 blocks)
    public int breakDelayTicks = 2; // Ticks delay between breaking blocks
    public int placeDelayTicks = 2; // Ticks delay between placing blocks
    public boolean autoRestartAfterRebuild = true; // Automatically restart auto-upgrade loop after rebuild
    public boolean smoothCameraAim = true; // Smoothly aim camera at target blocks like Baritone
    public float cameraAimSpeed = 45.0f; // Camera rotation speed (degrees per tick)

    public static ModConfig getInstance() {
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new ModConfig();
                }
                LOGGER.info("[MineGens Auto Upgrade] Config loaded successfully.");
            } catch (IOException e) {
                LOGGER.error("[MineGens Auto Upgrade] Failed to load config file: ", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            File parentDir = CONFIG_FILE.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
                LOGGER.info("[MineGens Auto Upgrade] Config saved successfully.");
            }
        } catch (IOException e) {
            LOGGER.error("[MineGens Auto Upgrade] Failed to save config file: ", e);
        }
    }
}
