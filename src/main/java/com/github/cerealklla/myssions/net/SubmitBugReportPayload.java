package com.github.cerealklla.myssions.net;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Submit" on {@code client.BugReportScreen} (the "Report Bug" button on a selected quest in {@code client.QuestLogScreen}). */
public record SubmitBugReportPayload(UUID instanceId, String reportText) implements CustomPacketPayload {

    // Plenty of headroom for a player's own free-text description without being unbounded.
    private static final int MAX_REPORT_TEXT_LENGTH = 1000;

    public static final Type<SubmitBugReportPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "submit_bug_report"));

    public static final StreamCodec<ByteBuf, SubmitBugReportPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SubmitBugReportPayload::instanceId,
            ByteBufCodecs.stringUtf8(MAX_REPORT_TEXT_LENGTH), SubmitBugReportPayload::reportText,
            SubmitBugReportPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
