package com.github.cerealklla.myssions.net;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Accept Quest" on a Quest Log row under "-- Available --" (see {@code PlayerQuestData#availableQuestIds}). */
public record AcceptAvailableQuestPayload(Identifier questId) implements CustomPacketPayload {

    public static final Type<AcceptAvailableQuestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "accept_available_quest"));

    public static final StreamCodec<ByteBuf, AcceptAvailableQuestPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, AcceptAvailableQuestPayload::questId,
            AcceptAvailableQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
