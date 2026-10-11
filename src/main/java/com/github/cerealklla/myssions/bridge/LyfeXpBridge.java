package com.github.cerealklla.myssions.bridge;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.SkillId;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * Applies a {@code QuestReward.Kind.XP} reward -- optional soft dependency, same isolation
 * convention as every other bridge in this suite: every call site elsewhere in Myssions must check
 * {@link #isLoaded()} itself before touching this class at all, so its {@code Lyfe.*}-referencing
 * bytecode is never loaded on a Lyfe-less server.
 */
public final class LyfeXpBridge {

    private LyfeXpBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("lyfe");
    }

    public static void grantXp(ServerPlayer player, String skillId, long amount) {
        if (!skillId.isBlank() && amount > 0) {
            Lyfe.addXp(player, new SkillId(skillId), amount);
        }
    }
}
