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
 * The mutual-exclusion state machine for inter-settlement road-connection quests (added 2026-10-11
 * -- see design-document.md's quest taxonomy, category 5, and context/decisions.md's same-date
 * entry for the full design). Keyed by an <b>unordered pair</b> of Cartographyr settlement
 * {@code geo.EntityId#value()}s, deliberately NOT Settlemynts settlement-core UUIDs -- Cartographyr
 * is the one identity every settlement already has, player-founded or natural, so this registry
 * needs no Settlemynts dependency at all to exist. A pair can be in exactly one of four states at
 * any time:
 * <ul>
 *   <li>Absent entirely ({@link #get} returns empty) -- no posting, no connection, either direction
 *   is free to post.</li>
 *   <li>{@link Entry#isPosted()} -- one side posted, waiting for a player to accept. The OTHER
 *   direction can never be posted while this exists (one entry per unordered pair, structurally).</li>
 *   <li>{@link Entry#isInProgress()} -- a player accepted and is currently building the road.</li>
 *   <li>{@link Entry#isConnected()} -- terminal. Once set, {@link #post} always refuses -- "once two
 *   towns are connected no more quests for that road will be [postable]."</li>
 * </ul>
 */
public final class RoadQuestRegistry extends SavedData {

    public static final SavedDataType<RoadQuestRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "road_quest_registry"),
            RoadQuestRegistry::new,
            codec());

    public enum Kind { POSTED, IN_PROGRESS, CONNECTED }

    public record PlaceIdPair(long a, long b) {
        public static PlaceIdPair of(long x, long y) {
            return x <= y ? new PlaceIdPair(x, y) : new PlaceIdPair(y, x);
        }

        public static final Codec<PlaceIdPair> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("a").forGetter(PlaceIdPair::a),
                Codec.LONG.fieldOf("b").forGetter(PlaceIdPair::b)
        ).apply(i, PlaceIdPair::of));
    }

    /**
     * @param postingSettlementPlaceId which of the pair actually posted (the direction) -- only meaningful while {@link #isPosted()}.
     * @param postingPlotId the Town Hall plot that posted it (empty for an NPC-auto-posted offer with no real plot concept).
     * @param posterPlayerId the Mayor who posted it, for the knowledge-transfer-on-accept behavior -- empty for an NPC posting (flat 1/3-quality grant instead, see {@code NpcQuests}).
     */
    public record Entry(Kind kind, long postingSettlementPlaceId, Optional<UUID> postingPlotId, Optional<UUID> posterPlayerId,
                         int minStakes, int optimalStakeCap, Optional<UUID> instanceId, Optional<UUID> acceptedByPlayerId,
                         int stakeCountUsed) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Entry::kind),
                Codec.LONG.fieldOf("posting_settlement_place_id").forGetter(Entry::postingSettlementPlaceId),
                UUIDUtil.CODEC.optionalFieldOf("posting_plot_id").forGetter(Entry::postingPlotId),
                UUIDUtil.CODEC.optionalFieldOf("poster_player_id").forGetter(Entry::posterPlayerId),
                Codec.INT.optionalFieldOf("min_stakes", 0).forGetter(Entry::minStakes),
                Codec.INT.optionalFieldOf("optimal_stake_cap", 0).forGetter(Entry::optimalStakeCap),
                UUIDUtil.CODEC.optionalFieldOf("instance_id").forGetter(Entry::instanceId),
                UUIDUtil.CODEC.optionalFieldOf("accepted_by_player_id").forGetter(Entry::acceptedByPlayerId),
                Codec.INT.optionalFieldOf("stake_count_used", 0).forGetter(Entry::stakeCountUsed)
        ).apply(i, Entry::new));

        public static Entry posted(long postingSettlementPlaceId, Optional<UUID> postingPlotId, Optional<UUID> posterPlayerId,
                                    int minStakes, int optimalStakeCap) {
            return new Entry(Kind.POSTED, postingSettlementPlaceId, postingPlotId, posterPlayerId, minStakes, optimalStakeCap,
                    Optional.empty(), Optional.empty(), 0);
        }

        public Entry withAccepted(UUID instanceId, UUID playerId) {
            return new Entry(Kind.IN_PROGRESS, postingSettlementPlaceId, postingPlotId, posterPlayerId, minStakes, optimalStakeCap,
                    Optional.of(instanceId), Optional.of(playerId), 0);
        }

        public Entry withConnected(int finalStakeCount) {
            return new Entry(Kind.CONNECTED, postingSettlementPlaceId, postingPlotId, posterPlayerId, minStakes, optimalStakeCap,
                    instanceId, acceptedByPlayerId, finalStakeCount);
        }

        public boolean isPosted() {
            return kind == Kind.POSTED;
        }

        public boolean isInProgress() {
            return kind == Kind.IN_PROGRESS;
        }

        public boolean isConnected() {
            return kind == Kind.CONNECTED;
        }
    }

    private record StoredEntry(PlaceIdPair pair, Entry entry) {
        static final Codec<StoredEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                PlaceIdPair.CODEC.fieldOf("pair").forGetter(StoredEntry::pair),
                Entry.CODEC.fieldOf("entry").forGetter(StoredEntry::entry)
        ).apply(i, StoredEntry::new));
    }

    private final Map<PlaceIdPair, Entry> entries;

    RoadQuestRegistry() {
        this(new HashMap<>());
    }

    private RoadQuestRegistry(Map<PlaceIdPair, Entry> entries) {
        this.entries = entries;
    }

    private static Codec<RoadQuestRegistry> codec() {
        return Codec.list(StoredEntry.CODEC).xmap(
                stored -> {
                    Map<PlaceIdPair, Entry> map = new HashMap<>();
                    for (StoredEntry entry : stored) {
                        map.put(entry.pair(), entry.entry());
                    }
                    return new RoadQuestRegistry(map);
                },
                data -> {
                    List<StoredEntry> stored = new ArrayList<>(data.entries.size());
                    data.entries.forEach((pair, entry) -> stored.add(new StoredEntry(pair, entry)));
                    return stored;
                });
    }

    public static RoadQuestRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Entry> get(long placeIdA, long placeIdB) {
        return Optional.ofNullable(entries.get(PlaceIdPair.of(placeIdA, placeIdB)));
    }

    /** True if this placeId has a {@code CONNECTED} entry with ANY other settlement -- used to gate escort quests (Phase B). */
    public List<Long> connectedNeighborsOf(long placeId) {
        List<Long> neighbors = new ArrayList<>();
        for (Map.Entry<PlaceIdPair, Entry> e : entries.entrySet()) {
            if (!e.getValue().isConnected()) {
                continue;
            }
            PlaceIdPair pair = e.getKey();
            if (pair.a() == placeId) {
                neighbors.add(pair.b());
            } else if (pair.b() == placeId) {
                neighbors.add(pair.a());
            }
        }
        return neighbors;
    }

    /** Posts a new offer for this pair -- fails (returns false, no-op) if the pair already has ANY entry (posted, in-progress, or connected). "At no point can two quests exist for A -> B [or B -> A]." */
    public boolean post(long postingSettlementPlaceId, long targetSettlementPlaceId, Optional<UUID> postingPlotId,
                         Optional<UUID> posterPlayerId, int minStakes, int optimalStakeCap) {
        PlaceIdPair pair = PlaceIdPair.of(postingSettlementPlaceId, targetSettlementPlaceId);
        if (entries.containsKey(pair)) {
            return false;
        }
        entries.put(pair, Entry.posted(postingSettlementPlaceId, postingPlotId, posterPlayerId, minStakes, optimalStakeCap));
        setDirty();
        return true;
    }

    public boolean accept(long placeIdA, long placeIdB, UUID instanceId, UUID playerId) {
        PlaceIdPair pair = PlaceIdPair.of(placeIdA, placeIdB);
        Entry existing = entries.get(pair);
        if (existing == null || !existing.isPosted()) {
            return false;
        }
        entries.put(pair, existing.withAccepted(instanceId, playerId));
        setDirty();
        return true;
    }

    public void markConnected(long placeIdA, long placeIdB, int finalStakeCount) {
        PlaceIdPair pair = PlaceIdPair.of(placeIdA, placeIdB);
        Entry existing = entries.get(pair);
        if (existing != null) {
            entries.put(pair, existing.withConnected(finalStakeCount));
            setDirty();
        }
    }

    /** Reverts a pair back to fully open (no entry at all) -- called on the in-progress instance failing/expiring/being abandoned, so either side is free to post again. */
    public void reopen(long placeIdA, long placeIdB) {
        if (entries.remove(PlaceIdPair.of(placeIdA, placeIdB)) != null) {
            setDirty();
        }
    }

    /**
     * Reverts an {@code IN_PROGRESS} pair back to the SAME {@code POSTED} offer it was before being
     * accepted (same posting side, plot, poster, and stake thresholds -- just clearing {@code
     * instanceId}/{@code acceptedByPlayerId}/{@code stakeCountUsed}), rather than deleting the entry
     * outright like {@link #reopen} does. Added 2026-10-13, explicit user spec for the Establish
     * Highway quest: "if the player abandons the quest or it is failed the quest will automatically
     * be made available again at the Town Hall NPC, and won't re-charge the mayor" -- {@link #reopen}
     * alone would clear the entry entirely, requiring a fresh (paid) post to get a new offer. A no-op
     * if the pair has no entry at all.
     */
    public void reopenAsPosted(long placeIdA, long placeIdB) {
        PlaceIdPair pair = PlaceIdPair.of(placeIdA, placeIdB);
        Entry existing = entries.get(pair);
        if (existing == null) {
            return;
        }
        entries.put(pair, Entry.posted(existing.postingSettlementPlaceId(), existing.postingPlotId(),
                existing.posterPlayerId(), existing.minStakes(), existing.optimalStakeCap()));
        setDirty();
    }
}
