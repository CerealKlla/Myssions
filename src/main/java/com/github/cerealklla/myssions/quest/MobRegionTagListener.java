package com.github.cerealklla.myssions.quest;

import com.github.cerealklla.myssions.bridge.CartographyrRegionBridge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Tags every freshly-joined hostile mob with the real Cartographyr Region/Settlement it spawned in
 * -- added 2026-10-12, the user's own proposed restructuring: "When a mob spawns in the world, add
 * a metadata tag to that mob for the Cartographyr Region it was spawned in... when a player kills a
 * mob, if they have a quest for killing in a region we can check that value instead of having to do
 * geometry checks." See {@code QuestKillListener#isWithinQuestArea} for the consuming half.
 *
 * <p>Deliberately scoped to {@link Enemy} only (not every entity) -- the only kind a kill-quest
 * milestone ever targets, and the only kind worth paying a per-spawn region lookup for. Tags once
 * and never again (a persistent-data presence check) so an already-tagged mob re-joining a reloaded
 * chunk doesn't redo the lookup. Silently no-ops with no tag at all if Cartographyr isn't loaded, or
 * if the spawn point isn't inside any real Region/Settlement (open wilderness) -- {@code
 * QuestKillListener}'s own distance-based geofence is still the fallback for an untagged mob.
 */
public final class MobRegionTagListener {

    public static final String TAG_KEY = "myssions_spawn_place_id";

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getEntity() instanceof Entity entity)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || entity.getPersistentData().contains(TAG_KEY)) {
            return;
        }
        if (!CartographyrRegionBridge.isLoaded()) {
            return;
        }
        CartographyrRegionBridge.findRegionOrSettlementPlaceIdAt(level, entity.blockPosition())
                .ifPresent(placeId -> entity.getPersistentData().putLong(TAG_KEY, placeId));
    }
}
