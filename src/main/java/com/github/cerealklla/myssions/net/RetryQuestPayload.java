package com.github.cerealklla.myssions.net;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Retry" button on a failed quest in {@code client.QuestLogScreen}. */
public record RetryQuestPayload(Identifier definitionId) implements CustomPacketPayload {

    public static final Type<RetryQuestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "retry_quest"));

    public static final StreamCodec<ByteBuf, RetryQuestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Identifier.CODEC), RetryQuestPayload::definitionId,
            RetryQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
