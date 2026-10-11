package com.github.cerealklla.myssions.net;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Set as Active Quest" button on {@code client.QuestLogScreen}. */
public record SetActiveQuestPayload(UUID instanceId) implements CustomPacketPayload {

    public static final Type<SetActiveQuestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "set_active_quest"));

    public static final StreamCodec<ByteBuf, SetActiveQuestPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetActiveQuestPayload::instanceId,
            SetActiveQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
