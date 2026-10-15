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
 * Per-entrance-quest-instance state that {@code bridge.TinyWyrldsEntranceBridge} needs to track
 * across the real entry/kill/escape events -- a side table rather than new fields on {@link
 * QuestInstance} itself, same "already at Mojang's 16-field {@code RecordCodecBuilder} group-arity
 * limit" reasoning as {@link BuyItemTargets}/{@link PlotUpgradeQuestTargets} (see either's own
 * doc). Keyed by {@link QuestInstance#instanceId()}, which doubles as the entrance's own id (see
 * {@code QuestHooks#registerEntranceHook}) -- an entrance quest's instance id IS the entrance id,
 * by construction, so no separate mapping is needed to go from one to the other.
 *
 * <p>{@code guardianEntityId} starts unset (the dungeon doesn't exist, let alone have a guardian,
 * until the player's first real interaction generates it) and is filled in the moment {@code
 * TinyWyrldsEntranceBridge}'s own interact listener reads {@code WyrldEntranceEntity
 * #lastEnterResult()} right after TinyWyrlds' own (higher-priority) listener produced it.
 */
public final class EntranceQuestLink extends SavedData {

    public static final SavedDataType<EntranceQuestLink> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "entrance_quest_link"),
            EntranceQuestLink::new,
            codec());

    public record Link(String recipeName, UUID guardianEntityId) {
    }

    private final Map<UUID, Link> links;

    EntranceQuestLink() {
        this(new HashMap<>());
    }

    private EntranceQuestLink(Map<UUID, Link> links) {
        this.links = links;
    }

    private record Entry(UUID instanceId, String recipeName, UUID guardianEntityId) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("instance_id").forGetter(Entry::instanceId),
                Codec.STRING.fieldOf("recipe_name").forGetter(Entry::recipeName),
                UUIDUtil.CODEC.optionalFieldOf("guardian_entity_id", Entry.NO_GUARDIAN).forGetter(Entry::guardianEntityId)
        ).apply(i, Entry::new));

        private static final UUID NO_GUARDIAN = new UUID(0L, 0L);
    }

    private static Codec<EntranceQuestLink> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, Link> map = new HashMap<>();
                    for (Entry entry : entries) {
                        UUID guardian = Entry.NO_GUARDIAN.equals(entry.guardianEntityId()) ? null : entry.guardianEntityId();
                        map.put(entry.instanceId(), new Link(entry.recipeName(), guardian));
                    }
                    return new EntranceQuestLink(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.links.size());
                    data.links.forEach((instanceId, link) -> entries.add(new Entry(instanceId, link.recipeName(),
                            link.guardianEntityId() == null ? Entry.NO_GUARDIAN : link.guardianEntityId())));
                    return entries;
                });
    }

    public static EntranceQuestLink get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Link> get(UUID instanceId) {
        return Optional.ofNullable(links.get(instanceId));
    }

    public void create(UUID instanceId, String recipeName) {
        links.put(instanceId, new Link(recipeName, null));
        setDirty();
    }

    public void setGuardian(UUID instanceId, UUID guardianEntityId) {
        Link existing = links.get(instanceId);
        if (existing == null || existing.guardianEntityId() != null) {
            return;
        }
        links.put(instanceId, new Link(existing.recipeName(), guardianEntityId));
        setDirty();
    }

    /** @return the instance id whose tracked guardian matches {@code deadEntityId}, if any. */
    public Optional<UUID> findByGuardian(UUID deadEntityId) {
        for (Map.Entry<UUID, Link> entry : links.entrySet()) {
            if (deadEntityId.equals(entry.getValue().guardianEntityId())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }
}
