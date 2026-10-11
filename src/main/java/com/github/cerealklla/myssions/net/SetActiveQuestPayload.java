package com.github.cerealklla.myssions.net;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Set as Active Quest" button on {@code client.QuestLogScreen}. */
public record SetActiveQuestPayload(Identifier definitionId) implements CustomPacketPayload {

    public static final Type<SetActiveQuestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "set_active_quest"));

    public static final StreamCodec<ByteBuf, SetActiveQuestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Identifier.CODEC), SetActiveQuestPayload::definitionId,
            SetActiveQuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
