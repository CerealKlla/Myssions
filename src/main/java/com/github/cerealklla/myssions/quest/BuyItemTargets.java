package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The resolved real item (and the gold reward sized around its real cost) a specific "buy an item"
 * quest instance (see {@code QuestMilestone#isBuyMilestone()}, {@code NpcQuests#aUsefulErrand})
 * actually targets -- a side table rather than new fields on {@link QuestInstance} itself, since
 * that record is already at Mojang's 16-field {@code RecordCodecBuilder} group-arity limit (its own
 * doc already notes this constraint shaped {@code EscortData}/{@code RoadQuestData} into nested
 * records; a small separate {@code SavedData} is lower-risk than restructuring that already-working
 * record further just for two more occasional, single-value fields). Keyed by {@link
 * QuestInstance#instanceId()}, so it naturally stays correct across the same "two independent
 * instances never collide" guarantee every other per-instance data already has.
 *
 * <p>{@code goldReward} (added 2026-10-11, explicit user spec: "the reward needs to match the cost
 * of actually buying the item, + say, 15%? that way there is an actual reward") is computed once,
 * at acceptance time, from the real vendor's listed {@code pricePerUnit} -- see {@code
 * NpcQuests#registerUsefulErrandHook} for the 1.15x math -- and applied as a bonus Gold Nugget stack
 * on turn-in by {@code QuestEngine#addBuyItemReward}, same shape as the existing proof-delivery bonus
 * gold.
 *
 * <p>{@code MinecraftServer}-scoped like every other save-wide Myssions registry here (e.g. {@code
 * IssuerQuestBoard}). Entries are never explicitly cleaned up on quest completion/failure -- a
 * small amount of permanently-orphaned data per finished instance, the same "accepted minor debris,
 * not solved with new cleanup code" tradeoff this suite already makes elsewhere (e.g. Settlemynts'
 * Highway Stake cancellation).
 */
public final class BuyItemTargets extends SavedData {

    public static final SavedDataType<BuyItemTargets> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "buy_item_targets"),
            BuyItemTargets::new,
            codec());

    public record Target(Identifier itemId, int goldReward) {
    }

    private final Map<UUID, Target> targets;

    BuyItemTargets() {
        this(new HashMap<>());
    }

    private BuyItemTargets(Map<UUID, Target> targets) {
        this.targets = targets;
    }

    private record Entry(UUID instanceId, Identifier itemId, int goldReward) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("instance_id").forGetter(Entry::instanceId),
                Identifier.CODEC.fieldOf("item_id").forGetter(Entry::itemId),
                Codec.INT.optionalFieldOf("gold_reward", 0).forGetter(Entry::goldReward)
        ).apply(i, Entry::new));
    }

    private static Codec<BuyItemTargets> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, Target> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.instanceId(), new Target(entry.itemId(), entry.goldReward()));
                    }
                    return new BuyItemTargets(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.targets.size());
                    data.targets.forEach((instanceId, target) -> entries.add(new Entry(instanceId, target.itemId(), target.goldReward())));
                    return entries;
                });
    }

    public static BuyItemTargets get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Target> get(UUID instanceId) {
        return Optional.ofNullable(targets.get(instanceId));
    }

    public void set(UUID instanceId, Identifier itemId, int goldReward) {
        targets.put(instanceId, new Target(itemId, goldReward));
        setDirty();
    }
}
