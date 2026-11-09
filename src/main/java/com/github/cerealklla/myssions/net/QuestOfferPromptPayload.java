package com.github.cerealklla.myssions.net;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client: show/hide the "Press &lt;button&gt; to review quest offer" HUD prompt -- see {@code quest.QuestOfferPromptTicker}'s own doc for when this actually fires. */
public record QuestOfferPromptPayload(boolean show) implements CustomPacketPayload {

    public static final Type<QuestOfferPromptPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "quest_offer_prompt"));

    public static final StreamCodec<ByteBuf, QuestOfferPromptPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, QuestOfferPromptPayload::show,
            QuestOfferPromptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
