package com.github.cerealklla.myssions.registration;

import java.util.function.Supplier;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.quest.PlayerQuestData;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {

    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MyssionsMod.MODID);

    // copyOnDeath() is required, not optional -- NeoForge attachments do NOT survive a player's
    // death by default (a brand-new Player entity is created on respawn), so skipping this would
    // silently wipe quest progress on every death. Synced to the owning player only (never other
    // players -- quest progress is private, same reasoning Lyfe's own skill data attachment uses).
    public static final Supplier<AttachmentType<PlayerQuestData>> QUEST_DATA = ATTACHMENT_TYPES.register(
            "quest_data",
            () -> AttachmentType.builder(holder -> PlayerQuestData.EMPTY)
                    .serialize(PlayerQuestData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .sync(ByteBufCodecs.fromCodecWithRegistries(PlayerQuestData.CODEC))
                    .build()
    );
}
