package com.github.cerealklla.myssions.quest;

public enum QuestStatus {
    IN_PROGRESS,
    /** Every non-optional milestone is done and XP/intangible rewards already applied, but Gold/Item rewards are still sitting uncollected in a {@code quest.QuestRewardMenu} -- see {@code QuestEngine#beginRewardPickup}'s own doc. Added 2026-10-11. */
    AWAITING_REWARD_PICKUP,
    COMPLETED,
    FAILED
}
