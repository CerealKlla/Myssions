package com.github.cerealklla.myssions.debug;

import com.mojang.brigadier.CommandDispatcher;

import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.quest.SampleQuests;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** DEBUG ONLY -- deliberately no permission gate, same precedent as every other sibling mod's own {@code debug.DebugCommands}. */
public final class DebugCommands {

    private DebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("myssions")
                .requires(source -> true)
                .then(Commands.literal("givetest")
                        .executes(ctx -> giveTestQuest(ctx.getSource()))));
    }

    private static int giveTestQuest(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        boolean assigned = Myssions.assignQuest(player, SampleQuests.WIRING_TEST_ID).isPresent();
        if (assigned) {
            source.sendSuccess(() -> Component.literal("Assigned the wiring-test quest."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Already have that quest in progress."));
        return 0;
    }
}
