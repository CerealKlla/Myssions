package com.github.cerealklla.myssions.bridge;

import java.util.Set;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.knowledge.KnowledgeFactor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Wraps Lyfe's quest-waypoint API -- optional soft dependency, only ever called from behind {@link
 * #isLoaded()} at the call site, same isolation convention as every other bridge in this mod (see
 * {@code LyfeXpBridge}'s own doc). Without Lyfe, design doc item 2 simply doesn't do anything --
 * quests still work, they just have no visual hint.
 */
public final class LyfeWaypointBridge {

    private LyfeWaypointBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("lyfe");
    }

    public static void setWaypoint(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, double radiusBlocks) {
        Lyfe.setQuestWaypoint(player, dimension, pos, radiusBlocks);
    }

    public static void clearWaypoint(ServerPlayer player) {
        Lyfe.clearQuestWaypoint(player);
    }

    public static void grantKnowledge(ServerPlayer player, long placeId, boolean direction, boolean distance, boolean position) {
        Set<KnowledgeFactor> factors = java.util.EnumSet.noneOf(KnowledgeFactor.class);
        if (direction) {
            factors.add(KnowledgeFactor.DIRECTION);
        }
        if (distance) {
            factors.add(KnowledgeFactor.DISTANCE);
        }
        if (position) {
            factors.add(KnowledgeFactor.POSITION);
        }
        Lyfe.grantKnowledgeFactors(player, placeId, factors);
    }

    public static int knownFactorCount(ServerPlayer player, long placeId) {
        return Lyfe.getKnownFactorCount(player, placeId);
    }

    /**
     * Copies {@code fromPlayer}'s real knowledge factors for {@code placeId} onto {@code toPlayer}
     * -- added 2026-10-11 for the road-connection quest's "pass the knowledge the Mayor has to the
     * player" spec. Purely additive on the receiving end (same {@code learnLocationFactors}
     * semantics every other grant already relies on). Only meaningful while {@code fromPlayer} is
     * online -- no offline-attachment-read path exists yet; the caller should fall back to a flat
     * grant otherwise.
     */
    public static void copyKnowledge(ServerPlayer fromPlayer, ServerPlayer toPlayer, long placeId) {
        java.util.Set<KnowledgeFactor> factors = Lyfe.getKnownFactors(fromPlayer, placeId);
        if (!factors.isEmpty()) {
            Lyfe.grantKnowledgeFactors(toPlayer, placeId, factors);
        }
    }
}
