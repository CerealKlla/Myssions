package com.github.cerealklla.myssions.net;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Retry" button on a failed quest in {@code client.QuestLogScreen}. */
public record RetryQuestPayload(UUID instanceId) implements CustomPacketPayload {

    public static final Type<RetryQuestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "retry_quest"));

    public static final StreamCodec<ByteBuf, RetryQuestPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RetryQuestPayload::instanceId,
            RetryQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
