package com.github.cerealklla.myssions.quest;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

/**
 * Maps a proof id (the bare string {@link QuestMilestone#proofId()} carries) to the display name
 * + flavor text {@link QuestProofItem} should show -- kept separate from the milestone/quest data
 * itself so authoring a quest's content (see {@code quest.NpcQuests}) only ever names a proof id,
 * never duplicates its presentation.
 */
public final class ProofItemCatalog {

    private record Entry(String displayName, String flavorText) {
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private ProofItemCatalog() {
    }

    public static void register(String proofId, String displayName, String flavorText) {
        ENTRIES.put(proofId, new Entry(displayName, flavorText));
    }

    public static ItemStack create(String proofId) {
        Entry entry = ENTRIES.getOrDefault(proofId, new Entry("Quest Proof", "Bring this back to whoever asked for it."));
        return QuestProofItem.createFor(proofId, entry.displayName(), entry.flavorText());
    }
}
