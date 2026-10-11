package com.github.cerealklla.myssions.bridge;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.SkillId;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * Reads a player's current level in an existing Lyfe skill -- added 2026-10-11 for the road-
 * connection quest's "a player must have the required skill of the minimum level" gate (explicit
 * user choice: reuse Lyfe's existing "Expeditionist" skill, no new skill needed). Same optional
 * soft-dependency isolation convention as every other bridge in this mod.
 */
public final class LyfeSkillLevelBridge {

    private LyfeSkillLevelBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("lyfe");
    }

    public static int getLevel(ServerPlayer player, String skillId) {
        return Lyfe.getLevel(player, new SkillId(skillId));
    }
}
