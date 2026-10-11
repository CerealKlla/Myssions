package com.github.cerealklla.myssions;

import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.debug.DebugCommands;
import com.github.cerealklla.myssions.net.RetryQuestPayload;
import com.github.cerealklla.myssions.net.SetActiveQuestPayload;
import com.github.cerealklla.myssions.quest.QuestDeadlineTicker;
import com.github.cerealklla.myssions.quest.SampleQuests;
import com.github.cerealklla.myssions.registration.ModAttachments;

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
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.register(new QuestDeadlineTicker());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Runs on both physical sides -- quest DEFINITIONS (static content, identical in every
        // client/server jar) are deliberately registered here rather than sent over the network at
        // all. Only per-player INSTANCE state (which quests a player has, their progress) is ever
        // synced, via the QUEST_DATA attachment's own .sync() -- see PlayerQuestData's own doc.
        SampleQuests.registerAll();
        LOGGER.info("Myssions common setup");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(SetActiveQuestPayload.TYPE, SetActiveQuestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Myssions.setActiveQuest(player, payload.definitionId());
                    }
                });

        registrar.playToServer(RetryQuestPayload.TYPE, RetryQuestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Myssions.retryQuest(player, payload.definitionId());
                    }
                });
    }
}
