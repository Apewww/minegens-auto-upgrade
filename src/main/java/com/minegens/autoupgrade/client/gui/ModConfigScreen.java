package com.minegens.autoupgrade.client.gui;

import com.minegens.autoupgrade.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class ModConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;

    private TextFieldWidget commandField;
    private TextFieldWidget rebuildItemField;

    public ModConfigScreen(Screen parent) {
        super(Text.literal("MineGens Auto Upgrade Settings"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 22;
        int rowHeight = 22;
        int buttonWidth = 150;
        int fullWidth = 310;

        // Row 1: Delay Loop Interval (1s - 60s) & Click Delay Ticks (1 - 20 ticks)
        this.addDrawableChild(new SliderWidget(
                centerX - buttonWidth - 5,
                startY,
                buttonWidth,
                20,
                Text.literal("Interval: " + config.autoLoopIntervalSeconds + "s"),
                (double) (Math.max(1, Math.min(60, config.autoLoopIntervalSeconds)) - 1) / 59.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Interval: " + config.autoLoopIntervalSeconds + "s"));
            }

            @Override
            protected void applyValue() {
                config.autoLoopIntervalSeconds = 1 + (int) Math.round(this.value * 59.0);
            }
        });

        this.addDrawableChild(new SliderWidget(
                centerX + 5,
                startY,
                buttonWidth,
                20,
                Text.literal("Click Delay: " + config.clickDelayTicks + " ticks"),
                (double) (Math.max(1, Math.min(20, config.clickDelayTicks)) - 1) / 19.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Click Delay: " + config.clickDelayTicks + " ticks (" + (config.clickDelayTicks * 50) + "ms)"));
            }

            @Override
            protected void applyValue() {
                config.clickDelayTicks = 1 + (int) Math.round(this.value * 19.0);
            }
        });

        // Row 2: Target Level (1 - 200) & Stop at Target Level Toggle
        this.addDrawableChild(new SliderWidget(
                centerX - buttonWidth - 5,
                startY + rowHeight,
                buttonWidth,
                20,
                Text.literal("Target Level: " + config.targetLevel),
                (double) (Math.max(1, Math.min(200, config.targetLevel)) - 1) / 199.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Target Level: " + config.targetLevel));
            }

            @Override
            protected void applyValue() {
                config.targetLevel = 1 + (int) Math.round(this.value * 199.0);
            }
        });

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.stopAtTargetLevel)
                .build(
                        centerX + 5,
                        startY + rowHeight,
                        buttonWidth,
                        20,
                        Text.literal("Stop on Target"),
                        (button, value) -> config.stopAtTargetLevel = value
                ));

        // Row 3: Hide Panel (Silent Mode) & Auto Close Screen
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.hidePanel)
                .build(
                        centerX - buttonWidth - 5,
                        startY + rowHeight * 2,
                        buttonWidth,
                        20,
                        Text.literal("Hide GUI Panel"),
                        (button, value) -> config.hidePanel = value
                ));

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoCloseScreen)
                .build(
                        centerX + 5,
                        startY + rowHeight * 2,
                        buttonWidth,
                        20,
                        Text.literal("Auto Close GUI"),
                        (button, value) -> config.autoCloseScreen = value
                ));

        // Row 4: Chat Alerts & Auto Rebuild on Target
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.sendNotification)
                .build(
                        centerX - buttonWidth - 5,
                        startY + rowHeight * 3,
                        buttonWidth,
                        20,
                        Text.literal("Chat Alerts"),
                        (button, value) -> config.sendNotification = value
                ));

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoRebuild)
                .build(
                        centerX + 5,
                        startY + rowHeight * 3,
                        buttonWidth,
                        20,
                        Text.literal("Auto Rebuild 2x2"),
                        (button, value) -> config.autoRebuild = value
                ));

        // Row 5: Rebuild Height (1-16 layers -> 4-64 blocks) & Auto Restart After Rebuild
        this.addDrawableChild(new SliderWidget(
                centerX - buttonWidth - 5,
                startY + rowHeight * 4,
                buttonWidth,
                20,
                Text.literal("Height: " + config.rebuildHeight + " (" + (config.rebuildHeight * 4) + " blocks)"),
                (double) (Math.max(1, Math.min(16, config.rebuildHeight)) - 1) / 15.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Height: " + config.rebuildHeight + " (" + (config.rebuildHeight * 4) + " blocks)"));
            }

            @Override
            protected void applyValue() {
                config.rebuildHeight = 1 + (int) Math.round(this.value * 15.0);
            }
        });

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoRestartAfterRebuild)
                .build(
                        centerX + 5,
                        startY + rowHeight * 4,
                        buttonWidth,
                        20,
                        Text.literal("Resume Upgrade"),
                        (button, value) -> config.autoRestartAfterRebuild = value
                ));

        // Row 6: Break Delay & Place Delay
        this.addDrawableChild(new SliderWidget(
                centerX - buttonWidth - 5,
                startY + rowHeight * 5,
                buttonWidth,
                20,
                Text.literal("Break Delay: " + config.breakDelayTicks + " ticks"),
                (double) (Math.max(1, Math.min(10, config.breakDelayTicks)) - 1) / 9.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Break Delay: " + config.breakDelayTicks + " ticks (" + (config.breakDelayTicks * 50) + "ms)"));
            }

            @Override
            protected void applyValue() {
                config.breakDelayTicks = 1 + (int) Math.round(this.value * 9.0);
            }
        });

        this.addDrawableChild(new SliderWidget(
                centerX + 5,
                startY + rowHeight * 5,
                buttonWidth,
                20,
                Text.literal("Place Delay: " + config.placeDelayTicks + " ticks"),
                (double) (Math.max(1, Math.min(10, config.placeDelayTicks)) - 1) / 9.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Place Delay: " + config.placeDelayTicks + " ticks (" + (config.placeDelayTicks * 50) + "ms)"));
            }

            @Override
            protected void applyValue() {
                config.placeDelayTicks = 1 + (int) Math.round(this.value * 9.0);
            }
        });

        // Row 7: Baritone-style Smooth Aim & Aim Speed
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.smoothCameraAim)
                .build(
                        centerX - buttonWidth - 5,
                        startY + rowHeight * 6,
                        buttonWidth,
                        20,
                        Text.literal("Smooth Camera Aim"),
                        (button, value) -> config.smoothCameraAim = value
                ));

        this.addDrawableChild(new SliderWidget(
                centerX + 5,
                startY + rowHeight * 6,
                buttonWidth,
                20,
                Text.literal("Aim Speed: " + (int) config.cameraAimSpeed + "°/t"),
                (double) (Math.max(10.0f, Math.min(90.0f, config.cameraAimSpeed)) - 10.0f) / 80.0f
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Aim Speed: " + (int) config.cameraAimSpeed + "°/t"));
            }

            @Override
            protected void applyValue() {
                config.cameraAimSpeed = 10.0f + (float) Math.round(this.value * 80.0f);
            }
        });

        // Row 8: Rebuild Item Name Text Field
        int rebuildItemY = startY + rowHeight * 7 + 8;
        this.rebuildItemField = new TextFieldWidget(
                this.textRenderer,
                centerX - fullWidth / 2,
                rebuildItemY,
                fullWidth,
                16,
                Text.literal("Rebuild Item Filter")
        );
        this.rebuildItemField.setMaxLength(64);
        this.rebuildItemField.setText(config.rebuildItemFilter != null ? config.rebuildItemFilter : "Wheat Generator");
        this.rebuildItemField.setChangedListener(text -> config.rebuildItemFilter = text);
        this.addDrawableChild(this.rebuildItemField);

        // Row 9: Upgrade Command Text Field
        int cmdY = rebuildItemY + 24;
        this.commandField = new TextFieldWidget(
                this.textRenderer,
                centerX - fullWidth / 2,
                cmdY,
                fullWidth,
                16,
                Text.literal("Command")
        );
        this.commandField.setMaxLength(64);
        this.commandField.setText(config.command != null ? config.command : "/upgradegen");
        this.commandField.setChangedListener(text -> config.command = text);
        this.addDrawableChild(this.commandField);

        // Bottom Row: Done Button (Save) and Reset Defaults
        int bottomY = this.height - 24;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset Defaults"), button -> {
            config.autoLoopIntervalSeconds = 5;
            config.clickDelayTicks = 2;
            config.targetLevel = 50;
            config.stopAtTargetLevel = true;
            config.hidePanel = true;
            config.autoCloseScreen = true;
            config.sendNotification = true;
            config.checkGeneratorSlot = 1;
            config.command = "/upgradegen";
            config.targetSlotId = 51;
            config.autoRebuild = true;
            config.rebuildItemFilter = "Wheat Generator";
            config.rebuildHeight = 8;
            config.breakDelayTicks = 2;
            config.placeDelayTicks = 2;
            config.autoRestartAfterRebuild = true;
            config.smoothCameraAim = true;
            config.cameraAimSpeed = 45.0f;
            ModConfig.save();
            if (this.client != null) {
                this.client.setScreen(new ModConfigScreen(this.parent));
            }
        }).dimensions(centerX - buttonWidth - 5, bottomY, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
            saveFields();
            ModConfig.save();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(centerX + 5, bottomY, buttonWidth, 20).build());
    }

    private void saveFields() {
        if (this.commandField != null) {
            config.command = this.commandField.getText().trim();
        }
        if (this.rebuildItemField != null) {
            config.rebuildItemFilter = this.rebuildItemField.getText().trim();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Safe dark translucent gradient without invoking vanilla shader blur (prevents "Can only blur once per frame" crash)
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                8,
                0xFFFFFF
        );

        int centerX = this.width / 2;
        int fullWidth = 310;
        int startY = 22;
        int rowHeight = 22;

        int rebuildLabelY = startY + rowHeight * 7 - 1;
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Generator Item to Place (Inventory Search):"),
                centerX - fullWidth / 2,
                rebuildLabelY,
                0xAAAAAA
        );

        int cmdLabelY = rebuildLabelY + 24;
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Upgrade Command:"),
                centerX - fullWidth / 2,
                cmdLabelY,
                0xAAAAAA
        );
    }

    @Override
    public void close() {
        saveFields();
        ModConfig.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
