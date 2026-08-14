package com.minegens.autoupgrade;

import com.minegens.autoupgrade.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineGensAutoUpgrade implements ModInitializer {
    public static final String MOD_ID = "minegensautoupgrade";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[MineGens Auto Upgrade] Initializing mod...");
        ModConfig.load();
    }
}
