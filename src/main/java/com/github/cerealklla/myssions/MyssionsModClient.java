package com.github.cerealklla.myssions;

import com.mojang.blaze3d.platform.InputConstants;

import com.github.cerealklla.myssions.client.ActiveQuestOverlay;
import com.github.cerealklla.myssions.client.QuestLogScreen;
import com.github.cerealklla.myssions.client.QuestRewardScreen;
import com.github.cerealklla.myssions.registration.ModMenus;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = MyssionsMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MyssionsMod.MODID, value = Dist.CLIENT)
public class MyssionsModClient {

    public MyssionsModClient(ModContainer container) {
    }

    // Menu consolidation (2026-10-17) -- contributes the "Quests" tab to Nuclyus's shared "K"
    // menu. See com.github.cerealklla.nuclyus.menu.client.MenuContributor's own doc. "J" below is
    // untouched and keeps working standalone, same reasoning as every other contributing mod.
    @SubscribeEvent
    static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        com.github.cerealklla.nuclyus.menu.client.NuclyusMenuRegistry.registerContributor(
                () -> java.util.List.of(new com.github.cerealklla.myssions.menu.client.QuestLogTab()));
    }

    // "Press J to open the Quest Log" -- "J" (for Journal) is free across the suite (Settlemynts
    // uses G/H, Lyfe uses [ ] O K), flagged as an easy-to-rebind placeholder like every other
    // keybind in this suite.
    public static final KeyMapping OPEN_QUEST_LOG = new KeyMapping(
            "key.myssions.open_quest_log", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KeyMapping.Category.MISC);

    // "Press U to review quest offer" (request #3, 2026-11-06) -- "U" is free across the suite,
    // flagged as an easy-to-rebind placeholder like every other keybind here. Opens the unified
    // Nuclyus "K" menu directly to the Quests tab, not the standalone J-bound QuestLogScreen.
    public static final KeyMapping OPEN_QUEST_OFFER_REVIEW = new KeyMapping(
            "key.myssions.open_quest_offer_review", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, KeyMapping.Category.MISC);

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_QUEST_LOG);
        event.register(OPEN_QUEST_OFFER_REVIEW);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Reuses vanilla's own VillagerRenderer directly -- a real Villager subclass needs no
        // bespoke model, same precedent as Settlemynts' own plot worker NPCs.
        event.registerEntityRenderer(com.github.cerealklla.myssions.registration.ModEntities.QUEST_NPC.get(),
                net.minecraft.client.renderer.entity.VillagerRenderer::new);
        // Same reasoning -- a real Villager subclass needs no bespoke model.
        event.registerEntityRenderer(com.github.cerealklla.myssions.registration.ModEntities.CARAVAN.get(),
                net.minecraft.client.renderer.entity.VillagerRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.QUEST_REWARD.get(), QuestRewardScreen::new);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "active_quest_overlay"), new ActiveQuestOverlay());
        event.registerAboveAll(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "quest_offer_prompt_overlay"),
                new com.github.cerealklla.myssions.client.QuestOfferPromptOverlay());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_QUEST_LOG.consumeClick()) {
            if (minecraft.player == null) {
                continue;
            }
            if (minecraft.screen instanceof QuestLogScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.screen == null) {
                minecraft.setScreen(new QuestLogScreen());
            }
        }
        while (OPEN_QUEST_OFFER_REVIEW.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            com.github.cerealklla.nuclyus.menu.client.NuclyusMenuScreen.requestInitialTab("Quests");
            minecraft.setScreen(new com.github.cerealklla.nuclyus.menu.client.NuclyusMenuScreen());
        }
    }
}
