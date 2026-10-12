package com.github.cerealklla.myssions.quest;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One step of a {@link QuestDefinition} -- a plain description plus an optional flag and a
 * required count (e.g. "Kill 5 Zombies" has {@code requiredCount == 5}; most milestones are
 * {@code 1}). Four real trigger types are modeled directly (added 2026-10-10, escort added
 * 2026-10-11):
 *
 * <ul>
 *   <li>{@link #killTargetEntityType()} present -- {@code quest.QuestKillListener} increments this
 *   milestone on a matching {@code LivingDeathEvent} killed by the player, and grants one
 *   {@code quest.QuestProofItem} (tagged {@link #proofId()}) per qualifying kill, up to
 *   {@code requiredCount} -- so by the time the kill count is done, the player is already holding
 *   exactly enough proof to turn in. Item 7.</li>
 *   <li>{@link #isDeliverMilestone()} true (with {@link #proofId()} present) -- completed by
 *   physically handing that many proof items back to the quest's issuer (an NPC or a sign
 *   interaction, see {@code quest.QuestTurnIn}), not by any automatic event. Item 7.</li>
 *   <li>{@link #isTalkMilestone()} true -- completed simply by interacting with the one {@code
 *   quest.QuestNpcEntity} spawned for this quest (design doc items 8/9 -- a named, possibly
 *   day/night-conditional, per-player-visible NPC), see that entity's own doc.</li>
 *   <li>{@link #isEscortMilestone()} true -- completed by {@code quest.QuestEscortTicker} when this
 *   instance's {@code quest.entity.CaravanEntity} reaches the final waypoint of its route alive (the
 *   caravan-raid quest style) -- not by any kill/deliver/talk event.</li>
 * </ul>
 *
 * A milestone with none of these is a plain manual one, completed only via a direct {@code
 * api.Myssions#reportMilestoneProgress}/{@code #completeMilestone} call. {@link #proofId()} is
 * shared by both a kill milestone (what it grants) and the matching deliver milestone (what it
 * consumes) within the same quest -- deliberately a bare string, not a whole {@link QuestReward},
 * since it names a *kind* of proof item, not an amount to grant as a reward.
 *
 * <p>{@link #bonusGoldPerProof()} (added 2026-10-11, explicit user spec: "Let's make the 'return #
 * items as proof' optional on most quests related to killing mobs, that pays extra per returned
 * proof item (up to the max)") -- only meaningful on a deliver milestone, 0 for every other kind.
 * Unlike a required deliver milestone (all-or-nothing, {@code QuestEngine#tryDeliverProof} only ever
 * succeeds with the full {@link #requiredCount()} in hand), an *optional* deliver milestone with a
 * nonzero bonus accepts a PARTIAL turn-in -- however many proof items the player actually holds, up
 * to {@link #requiredCount()} -- and {@code QuestEngine#beginRewardPickup} grants
 * {@code bonusGoldPerProof * (however many were actually delivered)} extra Gold on top of the
 * quest's normal flat rewards. The base quest (its required kill milestone) completes with or
 * without any delivery at all, since the milestone itself is optional.
 *
 * <p>{@link #nightOnly()} (added 2026-10-11, forward-looking -- no current quest sets it true):
 * when true on a kill milestone, {@code quest.QuestKillListener} only counts a kill toward it if it
 * happened while {@code level.isDarkOutside()} (same call {@code quest.QuestNpcEntity}'s own
 * day/night visibility gating already uses) -- a real, enforced "this only happens at night" instead
 * of flavor text that isn't actually gated.
 *
 * <p>{@link #isBuyMilestone()} (added 2026-10-11) marks a deliver milestone whose target item is
 * resolved per-instance (see {@code QuestInstance#buyItemId()}) from a settlement's real shop
 * listings, rather than a fixed {@link #proofId()}-keyed synthetic proof item -- see {@code
 * NpcQuests#aUsefulErrand} and {@code QuestEngine#tryDeliverProof}'s own doc for how turn-in differs
 * for this kind.
 *
 * <p>{@link #isGatherMilestone()} (added 2026-10-11, the taxonomy's "location-agnostic/
 * quantity-scaled" shape -- "bring me 12 Wheat, I don't care where it came from") is the static
 * sibling of a buy milestone: also a deliver milestone whose "proof" is a real {@code ItemStack},
 * but the target item is fixed per-*definition*, not resolved per-instance, so no side table is
 * needed. Deliberately reuses {@link #proofId()} to carry the target item's own {@code
 * Identifier#toString()} (parsed back via {@code Identifier#tryParse}) rather than adding yet
 * another field -- same "reuse a field/token instead of adding one" precedent {@code
 * NpcQuests#aUsefulErrand} already set for {@code {location}}. {@link #bonusGoldPerProof()} is
 * reused too, as a flat gold-per-unit reward multiplier (see {@code QuestEngine#addGatherReward}) --
 * its name describes the deliver-bonus use; for a gather milestone it's simply "gold per unit
 * delivered," read directly off the (already-static) {@link #requiredCount()}.
 *
 * <p>{@link #isPlotUpgradeMilestone()} (added 2026-10-12) marks a deliver milestone for the "help
 * upgrade this plot" quest type -- a third sibling of buy/gather, for the taxonomy's "bring the
 * plot's own currently-missing Tier-upgrade materials" shape. {@link #proofId()} holds a plain
 * resource key (`"wood"` or `"stone"`, never a real item/tag id) naming which of the two generic
 * resources this milestone targets; its real required amount is resolved per-instance (the plot's
 * live shortfall at acceptance time, via {@code bridge.SettlemyntsPlotUpgradeBridge}) and packed
 * onto {@code QuestInstance#locationName()} the same way a buy milestone packs its gold reward there
 * -- see {@code QuestText}'s own doc. {@link #requiredCount()} on the static milestone itself is a
 * meaningless placeholder (0) for this kind, exactly like a buy milestone's own static count.
 */
public record QuestMilestone(String id, String description, boolean optional, int requiredCount,
                              Optional<Identifier> killTargetEntityType, Optional<String> proofId,
                              boolean isDeliverMilestone, boolean isTalkMilestone, boolean isEscortMilestone,
                              int bonusGoldPerProof, boolean nightOnly, boolean isBuyMilestone, boolean isGatherMilestone,
                              boolean isPlotUpgradeMilestone) {

    public static final Codec<QuestMilestone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestMilestone::id),
            Codec.STRING.fieldOf("description").forGetter(QuestMilestone::description),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(QuestMilestone::optional),
            Codec.INT.optionalFieldOf("required_count", 1).forGetter(QuestMilestone::requiredCount),
            Identifier.CODEC.optionalFieldOf("kill_target_entity_type").forGetter(QuestMilestone::killTargetEntityType),
            Codec.STRING.optionalFieldOf("proof_id").forGetter(QuestMilestone::proofId),
            Codec.BOOL.optionalFieldOf("is_deliver_milestone", false).forGetter(QuestMilestone::isDeliverMilestone),
            Codec.BOOL.optionalFieldOf("is_talk_milestone", false).forGetter(QuestMilestone::isTalkMilestone),
            Codec.BOOL.optionalFieldOf("is_escort_milestone", false).forGetter(QuestMilestone::isEscortMilestone),
            Codec.INT.optionalFieldOf("bonus_gold_per_proof", 0).forGetter(QuestMilestone::bonusGoldPerProof),
            Codec.BOOL.optionalFieldOf("night_only", false).forGetter(QuestMilestone::nightOnly),
            Codec.BOOL.optionalFieldOf("is_buy_milestone", false).forGetter(QuestMilestone::isBuyMilestone),
            Codec.BOOL.optionalFieldOf("is_gather_milestone", false).forGetter(QuestMilestone::isGatherMilestone),
            Codec.BOOL.optionalFieldOf("is_plot_upgrade_milestone", false).forGetter(QuestMilestone::isPlotUpgradeMilestone)
    ).apply(instance, QuestMilestone::new));

    public static QuestMilestone simple(String id, String description) {
        return new QuestMilestone(id, description, false, 1, Optional.empty(), Optional.empty(), false, false, false, 0, false, false, false, false);
    }

    public static QuestMilestone simple(String id, String description, boolean optional) {
        return new QuestMilestone(id, description, optional, 1, Optional.empty(), Optional.empty(), false, false, false, 0, false, false, false, false);
    }

    public static QuestMilestone kill(String id, String description, Identifier entityType, int count, String proofId) {
        return new QuestMilestone(id, description, false, count, Optional.of(entityType), Optional.of(proofId), false, false, false, 0, false, false, false, false);
    }

    /** Like {@link #kill(String, String, Identifier, int, String)}, but only counts a kill at night -- see this class's own doc. */
    public static QuestMilestone kill(String id, String description, Identifier entityType, int count, String proofId, boolean nightOnly) {
        return new QuestMilestone(id, description, false, count, Optional.of(entityType), Optional.of(proofId), false, false, false, 0, nightOnly, false, false, false);
    }

    public static QuestMilestone deliver(String id, String description, String proofId, int count) {
        return new QuestMilestone(id, description, false, count, Optional.empty(), Optional.of(proofId), true, false, false, 0, false, false, false, false);
    }

    /** An optional deliver milestone that pays {@code bonusGoldPerProof} Gold per item actually turned in (up to {@code maxCount}) -- see this class's own doc for how this differs from the required {@link #deliver} above. */
    public static QuestMilestone optionalDeliverWithBonus(String id, String description, String proofId, int maxCount, int bonusGoldPerProof) {
        return new QuestMilestone(id, description, true, maxCount, Optional.empty(), Optional.of(proofId), true, false, false, bonusGoldPerProof, false, false, false, false);
    }

    public static QuestMilestone talk(String id, String description) {
        return new QuestMilestone(id, description, false, 1, Optional.empty(), Optional.empty(), false, true, false, 0, false, false, false, false);
    }

    public static QuestMilestone escort(String id, String description) {
        return new QuestMilestone(id, description, false, 1, Optional.empty(), Optional.empty(), false, false, true, 0, false, false, false, false);
    }

    /** See this class's own doc for {@link #isBuyMilestone()} -- the real target item is resolved per-instance, not stored here. */
    public static QuestMilestone buyItem(String id, String description, int count) {
        return new QuestMilestone(id, description, false, count, Optional.empty(), Optional.empty(), true, false, false, 0, false, true, false, false);
    }

    /** See this class's own doc for {@link #isGatherMilestone()} -- {@code itemId} is fixed for every instance of this quest definition; {@code goldPerUnit} sizes the turn-in reward (see {@code QuestEngine#addGatherReward}). */
    public static QuestMilestone gather(String id, String description, Identifier itemId, int count, int goldPerUnit) {
        return new QuestMilestone(id, description, false, count, Optional.empty(), Optional.of(itemId.toString()), true, false, false, goldPerUnit, false, false, true, false);
    }

    /** See this class's own doc for {@link #isPlotUpgradeMilestone()} -- {@code resourceKey} is {@code "wood"} or {@code "stone"}; the real required amount is resolved per-instance, not stored here. */
    public static QuestMilestone plotUpgradeResource(String id, String description, String resourceKey) {
        return new QuestMilestone(id, description, false, 0, Optional.empty(), Optional.of(resourceKey), true, false, false, 0, false, false, false, true);
    }
}
