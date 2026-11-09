package com.github.cerealklla.myssions.net;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Decline" on a Quest Log row under "-- Available --" (see {@code PlayerQuestData#availableQuestIds}) -- added 2026-11-06 alongside {@code QuestDefinition#disableDecline}, mirrors {@link AcceptAvailableQuestPayload} exactly. */
public record DeclineAvailableQuestPayload(Identifier questId) implements CustomPacketPayload {

    public static final Type<DeclineAvailableQuestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "decline_available_quest"));

    public static final StreamCodec<ByteBuf, DeclineAvailableQuestPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, DeclineAvailableQuestPayload::questId,
            DeclineAvailableQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
