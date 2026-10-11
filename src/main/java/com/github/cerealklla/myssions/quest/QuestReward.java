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
 * path). Four kinds, matching the user's own spec ("Gold, Items, or ... intangible things like XP,
 * Kyngdom Standing ... or even things like new Plot Type options"):
 *
 * <ul>
 *   <li>{@link #gold} -- applied via {@code bridge.YconomicsGoldBridge} if Yconomics is loaded,
 *   otherwise silently skipped (not an error -- a reward the economy mod can't apply just doesn't
 *   apply, same "optional dependency, no crash" shape as every other soft integration here).</li>
 *   <li>{@link #item} -- always applies (no soft dependency needed), added directly to the
 *   player's inventory (or dropped at their feet if full).</li>
 *   <li>{@link #xp} -- applied via {@code bridge.LyfeXpBridge} if Lyfe is loaded, same
 *   skip-if-absent shape as gold.</li>
 *   <li>{@link #intangible} -- a plain flavor/description line with no mechanical effect yet
 *   (Kyngdom Standing, new Plot Type unlocks, etc. don't exist as real systems anywhere in the
 *   suite yet) -- deliberately just descriptive text for now, flagged so a future pass can wire a
 *   real effect in without changing this shape.</li>
 * </ul>
 */
public record QuestReward(Kind kind, int amount, Identifier itemId, String intangibleDescription, String skillId) {

    public enum Kind { GOLD, ITEM, XP, INTANGIBLE }

    private static final Identifier AIR = Identifier.fromNamespaceAndPath("minecraft", "air");

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(QuestReward::kind),
            Codec.INT.optionalFieldOf("amount", 0).forGetter(QuestReward::amount),
            Identifier.CODEC.optionalFieldOf("item_id", AIR).forGetter(QuestReward::itemId),
            Codec.STRING.optionalFieldOf("intangible_description", "").forGetter(QuestReward::intangibleDescription),
            Codec.STRING.optionalFieldOf("skill_id", "").forGetter(QuestReward::skillId)
    ).apply(instance, QuestReward::new));

    public static QuestReward gold(int amount) {
        return new QuestReward(Kind.GOLD, amount, AIR, "", "");
    }

    public static QuestReward item(Identifier itemId, int amount) {
        return new QuestReward(Kind.ITEM, amount, itemId, "", "");
    }

    /** {@code skillId} must match a real Lyfe {@code skill.SkillId} value (e.g. {@code "lumberjack"}) -- applied via {@code bridge.LyfeXpBridge} if Lyfe is loaded, skipped otherwise. */
    public static QuestReward xp(String skillId, int amount) {
        return new QuestReward(Kind.XP, amount, AIR, "", skillId);
    }

    public static QuestReward intangible(String description) {
        return new QuestReward(Kind.INTANGIBLE, 0, AIR, description, "");
    }

    /** One line of human-readable text for the Quest Log's rewards list. */
    public String displayLine() {
        return switch (kind) {
            case GOLD -> amount + " Gold Nugget" + (amount == 1 ? "" : "s");
            case ITEM -> amount + "x " + itemDisplayName();
            case XP -> amount + " " + skillId + " XP";
            case INTANGIBLE -> intangibleDescription;
        };
    }

    private String itemDisplayName() {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR));
        return stack.getHoverName().getString();
    }
}
