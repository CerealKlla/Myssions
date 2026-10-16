package com.github.cerealklla.myssions.bridge;

import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.api.Lyfe;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * The General "assist marker" kill-credit system (explicit user spec, 2026-10-16, built in Lyfe --
 * see that mod's own {@code combat.AssistTracker}/{@code api.Lyfe#getAssistCredit}) -- optional
 * soft dependency, same isolation convention as every other bridge in this suite: every call site
 * elsewhere in Myssions must check {@link #isLoaded()} itself before touching this class at all, so
 * its {@code Lyfe.*}-referencing bytecode is never loaded on a Lyfe-less server.
 */
public final class LyfeAssistBridge {

    private LyfeAssistBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("lyfe");
    }

    /** Every player credited toward killing {@code deadMob} (every real hit within the last 15 seconds, not just the killing blow). */
    public static Set<UUID> getAssistCredit(LivingEntity deadMob) {
        return Lyfe.getAssistCredit(deadMob);
    }
}
