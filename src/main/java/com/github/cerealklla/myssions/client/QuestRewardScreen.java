package com.github.cerealklla.myssions.client;

import com.github.cerealklla.myssions.quest.QuestRewardMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * {@link QuestRewardMenu}'s screen -- a copy of vanilla's own generic container screen (same reason
 * Yconomics' own {@code bag.client.LootBagScreen} is a copy rather than a reused base class: Java
 * generics are invariant, so a screen for a {@code ChestMenu} subclass can't just extend the vanilla
 * one) plus two buttons, both sending the same generic inventory-button-click packet vanilla's own
 * furnace/enchanting-table buttons use: "Take All" (left) and "Complete Quest" (right) -- see {@code
 * QuestRewardMenu}'s own doc for what each actually does server-side.
 */
public final class QuestRewardScreen extends AbstractContainerScreen<QuestRewardMenu> {

    private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    public QuestRewardScreen(QuestRewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 84;
        addRenderableWidget(Button.builder(Component.literal("Take All"), button ->
                        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, QuestRewardMenu.TAKE_ALL_BUTTON_ID))
                .bounds(leftPos + imageWidth / 2 - buttonWidth - 2, topPos - 24, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Complete Quest"), button ->
                        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, QuestRewardMenu.COMPLETE_BUTTON_ID))
                .bounds(leftPos + imageWidth / 2 + 2, topPos - 24, buttonWidth, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo + 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }
}
