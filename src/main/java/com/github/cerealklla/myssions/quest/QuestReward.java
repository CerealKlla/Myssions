package com.github.cerealklla.myssions.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A single reward line on a {@link QuestDefinition}, shown in the Quest Log's details pane and
 * applied once a quest completes (see {@code api.Myssions#reportMilestoneProgress}'s completion
 * path). Three kinds, each a real, mechanically-applied reward:
 *
 * <ul>
 *   <li>{@link #gold} -- applied via {@code bridge.YconomicsGoldBridge} if Yconomics is loaded,
 *   otherwise silently skipped (not an error -- a reward the economy mod can't apply just doesn't
 *   apply, same "optional dependency, no crash" shape as every other soft integration here).</li>
 *   <li>{@link #item} -- always applies (no soft dependency needed), added directly to the
 *   player's inventory (or dropped at their feet if full).</li>
 *   <li>{@link #xp} -- applied via {@code bridge.LyfeXpBridge} if Lyfe is loaded, same
 *   skip-if-absent shape as gold.</li>
 * </ul>
 *
 * <p><b>A fourth kind, {@code INTANGIBLE}, existed from 2026-10-10 through 2026-10-12</b> -- a
 * plain flavor/description line with no mechanical effect, shown in the Rewards list right next to
 * real rewards. Removed outright, not just stopped being added: repeated explicit user correction
 * ("this quest has no actual reward listed," repeated after the content-expansion pass that added
 * 110 quests reintroduced it everywhere, including as the *only* listed reward on many quests) made
 * clear this was never acceptable as a "reward" at all, however it reads in isolation -- a listed
 * reward the player can see has to be something they actually receive. The capability itself was
 * the problem (it kept getting reused by later content passes that didn't know not to), so the
 * `Kind`, the field, and the factory method are gone, not just emptied out of quest definitions --
 * there's nothing left for a future pass to accidentally pick back up. Flavor/narrative color
 * belongs in a quest's description text, which already exists independently of its reward list.
 */
public record QuestReward(Kind kind, int amount, Identifier itemId, String skillId) {

    public enum Kind { GOLD, ITEM, XP }

    private static final Identifier AIR = Identifier.fromNamespaceAndPath("minecraft", "air");

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(QuestReward::kind),
            Codec.INT.optionalFieldOf("amount", 0).forGetter(QuestReward::amount),
            Identifier.CODEC.optionalFieldOf("item_id", AIR).forGetter(QuestReward::itemId),
            Codec.STRING.optionalFieldOf("skill_id", "").forGetter(QuestReward::skillId)
    ).apply(instance, QuestReward::new));

    public static QuestReward gold(int amount) {
        return new QuestReward(Kind.GOLD, amount, AIR, "");
    }

    public static QuestReward item(Identifier itemId, int amount) {
        return new QuestReward(Kind.ITEM, amount, itemId, "");
    }

    /** {@code skillId} must match a real Lyfe {@code skill.SkillId} value (e.g. {@code "lumberjack"}) -- applied via {@code bridge.LyfeXpBridge} if Lyfe is loaded, skipped otherwise. */
    public static QuestReward xp(String skillId, int amount) {
        return new QuestReward(Kind.XP, amount, AIR, skillId);
    }

    /** One line of human-readable text for the Quest Log's rewards list. */
    public String displayLine() {
        return switch (kind) {
            case GOLD -> amount + " Gold Nugget" + (amount == 1 ? "" : "s");
            case ITEM -> amount + "x " + itemDisplayName();
            case XP -> amount + " " + skillId + " XP";
        };
    }

    private String itemDisplayName() {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR));
        return stack.getHoverName().getString();
    }
}
