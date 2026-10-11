package com.github.cerealklla.myssions.net;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Abandon Quest" button on an in-progress quest in {@code client.QuestLogScreen}. */
public record AbandonQuestPayload(UUID instanceId) implements CustomPacketPayload {

    public static final Type<AbandonQuestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "abandon_quest"));

    public static final StreamCodec<ByteBuf, AbandonQuestPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AbandonQuestPayload::instanceId,
            AbandonQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
