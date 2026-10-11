package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

import com.github.cerealklla.myssions.MyssionsMod;

/**
 * One hand-authored wiring-test quest, registered at common setup (both physical sides -- see
 * {@code MyssionsMod#commonSetup}) purely to exercise the Quest Log UI/engine end to end before any
 * real content type (milestone triggers, the quest-token item, waypoint hints) exists. NOT one of
 * the ten real fetch-quest variations from the user's own spec -- those are explicit follow-up
 * work, this is scaffolding only, flagged as such so it doesn't get mistaken for real content later.
 */
public final class SampleQuests {

    public static final Identifier WIRING_TEST_ID = Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "wiring_test");

    private SampleQuests() {
    }

    public static void registerAll() {
        QuestRegistry.register(new QuestDefinition(
                WIRING_TEST_ID,
                "A Simple Task",
                "Someone in town could use a hand clearing out a few pests. Bring back proof of three kills, and report back once you're done.",
                "test_issuer",
                List.of(
                        new QuestMilestone("kill_three", "Kill 3 Zombies", false, 3),
                        new QuestMilestone("report_back", "Report back", false, 1),
                        new QuestMilestone("optional_bonus", "(Optional) Kill 5 more for good measure", true, 5)
                ),
                List.of(
                        QuestReward.gold(10),
                        QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "bread"), 4),
                        QuestReward.intangible("A little good will around town")
                ),
                QuestTimeLimit.NONE
        ));
    }
}
