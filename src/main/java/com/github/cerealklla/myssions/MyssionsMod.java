package com.github.cerealklla.myssions;

import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.debug.DebugCommands;
import com.github.cerealklla.myssions.net.SetActiveQuestPayload;
import com.github.cerealklla.myssions.quest.QuestDeadlineTicker;
import com.github.cerealklla.myssions.quest.QuestKillListener;
import com.github.cerealklla.myssions.quest.QuestNpcEntity;
import com.github.cerealklla.myssions.quest.QuestNpcTalkListener;
import com.github.cerealklla.myssions.quest.NpcQuests;
import com.github.cerealklla.myssions.quest.SampleQuests;
import com.github.cerealklla.myssions.registration.ModAttachments;
import com.github.cerealklla.myssions.registration.ModEntities;
import com.github.cerealklla.myssions.registration.ModItems;
import com.github.cerealklla.myssions.registration.ModMenus;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(MyssionsMod.MODID)
public class MyssionsMod {

    public static final String MODID = "myssions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MyssionsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModItems.DATA_COMPONENTS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        com.github.cerealklla.myssions.registration.ModBlocks.BLOCKS.register(modEventBus);
        com.github.cerealklla.myssions.registration.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerAttributes);

        NeoForge.EVENT_BUS.register(new QuestDeadlineTicker());
        NeoForge.EVENT_BUS.register(new QuestKillListener());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.quest.MobRegionTagListener());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.marker.MarkerBreakListener());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.quest.QuestMobSpawnTicker());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.quest.QuestReportBackTicker());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.quest.QuestEscortTicker());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.quest.RoadQuestTicker());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.quest.NaturalVillageHighwayTicker());
        NeoForge.EVENT_BUS.register(new QuestNpcTalkListener());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));

        // Optional soft dependency -- only ever constructed/registered when Settlemynts is actually
        // loaded, so these listeners' Settlemynts-referencing bytecode is never touched (and never
        // needs to resolve) on a Settlemynts-less server. See each listener's own doc.
        if (net.neoforged.fml.ModList.get().isLoaded("settlemynts")) {
            NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.bridge.QuestNpcInteractListener());
            NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.bridge.QuestSignInteractListener());
            NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.bridge.QuestBoardMidnightTicker());
        }
        // Same reasoning -- only registered when Lyfe is loaded, since this ticker only ever does
        // anything through bridge.LyfeWaypointBridge (design doc item 2).
        if (net.neoforged.fml.ModList.get().isLoaded("lyfe")) {
            NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.quest.QuestWaypointTicker());
        }
        // Design doc item 4's quest-triggered Entrance variant -- only registered when TinyWyrlds is
        // loaded, since this listener directly references its WyrldEntranceEntity class.
        if (net.neoforged.fml.ModList.get().isLoaded("tinywyrlds")) {
            NeoForge.EVENT_BUS.register(new com.github.cerealklla.myssions.bridge.TinyWyrldsEntranceBridge());
        }
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntities.QUEST_NPC.get(), QuestNpcEntity.createAttributes().build());
        event.put(ModEntities.CARAVAN.get(), com.github.cerealklla.myssions.quest.entity.CaravanEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Runs on both physical sides -- quest DEFINITIONS (static content, identical in every
        // client/server jar) are deliberately registered here rather than sent over the network at
        // all. Only per-player INSTANCE state (which quests a player has, their progress) is ever
        // synced, via the QUEST_DATA attachment's own .sync() -- see PlayerQuestData's own doc.
        SampleQuests.registerAll();
        NpcQuests.registerAll();
        com.github.cerealklla.myssions.quest.GrocerQuests.registerAll();
        com.github.cerealklla.myssions.quest.BlacksmithQuests.registerAll();
        com.github.cerealklla.myssions.quest.ArmorerQuests.registerAll();
        com.github.cerealklla.myssions.quest.RestaurantQuests.registerAll();
        com.github.cerealklla.myssions.quest.BuildingSupplierQuests.registerAll();
        com.github.cerealklla.myssions.quest.StonemasonQuests.registerAll();
        com.github.cerealklla.myssions.quest.TavernQuests.registerAll();
        com.github.cerealklla.myssions.quest.MinerQuests.registerAll();
        com.github.cerealklla.myssions.quest.RoadQuestDefinitions.registerAll();
        LOGGER.info("Myssions common setup");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(SetActiveQuestPayload.TYPE, SetActiveQuestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Myssions.setActiveQuest(player, payload.instanceId());
                    }
                });

        registrar.playToServer(com.github.cerealklla.myssions.net.AbandonQuestPayload.TYPE,
                com.github.cerealklla.myssions.net.AbandonQuestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Myssions.abandonQuest(player, payload.instanceId());
                    }
                });

        registrar.playToServer(com.github.cerealklla.myssions.net.SubmitBugReportPayload.TYPE,
                com.github.cerealklla.myssions.net.SubmitBugReportPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    var instance = player.getData(com.github.cerealklla.myssions.registration.ModAttachments.QUEST_DATA).quests().get(payload.instanceId());
                    if (instance == null) {
                        return;
                    }
                    com.github.cerealklla.myssions.quest.QuestRegistry.get(instance.definitionId()).ifPresent(definition ->
                            com.github.cerealklla.myssions.quest.BugReportWriter.write(player, instance, definition, payload.reportText()));
                });
    }
}
