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

    public ModConfigScreen(Screen parent) {
        super(Text.literal("MineGens Auto Upgrade Settings"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 36;
        int rowHeight = 24;
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

        // Row 4: Send Notifications & Generator Check Slot ID
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.sendNotification)
                .build(
                        centerX - buttonWidth - 5,
                        startY + rowHeight * 3,
                        buttonWidth,
                        20,
                        Text.literal("Chat Alerts"),
                        (button, value) -> config.sendNotification = value
                ));

        this.addDrawableChild(new SliderWidget(
                centerX + 5,
                startY + rowHeight * 3,
                buttonWidth,
                20,
                Text.literal("Check Slot: " + config.checkGeneratorSlot),
                (double) (Math.max(0, Math.min(53, config.checkGeneratorSlot))) / 53.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Check Slot: " + config.checkGeneratorSlot));
            }

            @Override
            protected void applyValue() {
                config.checkGeneratorSlot = (int) Math.round(this.value * 53.0);
            }
        });

        // Row 5: Command text field
        int fieldY = startY + rowHeight * 4 + 10;
        this.commandField = new TextFieldWidget(
                this.textRenderer,
                centerX - fullWidth / 2,
                fieldY,
                fullWidth,
                20,
                Text.literal("Command")
        );
        this.commandField.setMaxLength(64);
        this.commandField.setText(config.command != null ? config.command : "/upgradegen");
        this.commandField.setChangedListener(text -> config.command = text);
        this.addDrawableChild(this.commandField);

        // Bottom Row: Done Button (Save) and Reset Defaults
        int bottomY = this.height - 32;

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
            ModConfig.save();
            if (this.client != null) {
                this.client.setScreen(new ModConfigScreen(this.parent));
            }
        }).dimensions(centerX - buttonWidth - 5, bottomY, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
            if (this.commandField != null) {
                config.command = this.commandField.getText().trim();
            }
            ModConfig.save();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(centerX + 5, bottomY, buttonWidth, 20).build());
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
                14,
                0xFFFFFF
        );

        int centerX = this.width / 2;
        int fullWidth = 310;
        int labelY = 36 + 24 * 4 - 2;
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Upgrade Command:"),
                centerX - fullWidth / 2,
                labelY,
                0xAAAAAA
        );
    }

    @Override
    public void close() {
        if (this.commandField != null) {
            config.command = this.commandField.getText().trim();
        }
        ModConfig.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
