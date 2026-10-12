package com.github.cerealklla.myssions.quest;

import java.util.List;

/**
 * Registers the one static {@link QuestDefinition} behind every road-connection quest instance --
 * see {@link RoadQuestLogic}'s own doc for why this quest type is assigned through a standalone
 * flow rather than {@code IssuerQuestPools}. Every instance's actual target settlement, stake-count
 * thresholds, etc. are per-instance data (see {@code QuestInstance}'s road-quest fields) layered on
 * top of this one shared template.
 */
public final class RoadQuestDefinitions {

    private RoadQuestDefinitions() {
    }

    public static void registerAll() {
        QuestRegistry.register(new QuestDefinition(RoadQuestLogic.DEFINITION_ID, "Connect Settlements by Road",
                "Build a real road connecting the two settlements, end to end -- plant Highway Stakes "
                        + "from just outside one settlement's edge to just outside the other's. Once the road sinks "
                        + "in, the Board pays out -- and anything you didn't end up needing gets bought back.",
                "road_quest_board",
                List.of(
                        QuestMilestone.simple("connect_road", "Connect the two settlements with a road"),
                        QuestMilestone.simple("efficient_road", "Build it efficiently (bonus reward)", true)
                ),
                List.of(QuestReward.gold(20)),
                QuestTimeLimit.withinTicksOfAssignment(6L * 60 * 60 * 20)
        ));
    }
}
