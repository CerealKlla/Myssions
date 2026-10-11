# Myssions

Quest system mod for a modular Minecraft project (Minecraft Java Edition). Part of a suite of
intercommunicating mods that expose public APIs so other mods — the user's own and third parties' —
can integrate.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/myssions-context), not
part of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's
cloned as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a
fresh clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/myssions-context.git context
```

Before searching source for architecture, ownership boundaries, API shape, or "why does this work
this way," check `context/` first:

- `context/design-document.md` — the authoritative design spec, including the user's own full
  ten-point original request verbatim, each annotated with its real implementation status. Start
  here for anything about intended shape or scope.
- `context/decisions.md` — dated log of decisions + pending/cross-mod-interface facts.
- `context/classes/` — one short markdown file per implemented class or tightly-related group.

**Keep this system current as you work** — same discipline as every other mod in this suite: update
design-document.md/decisions.md when a decision is made, update classes/ when a class's public
surface changes, commit+push context/ separately from this repo's own commits.

## Status

Scaffolded 2026-10-10 — standalone mod, mirroring Kyt's own project shape:
- Loader: **NeoForge**, Minecraft **26.1.2**, Java **25**
- Group ID: `com.github.cerealklla.myssions` / Mod ID: `myssions`
- No required sibling-mod dependencies. Optional soft dependencies on Cartographyr (future
  waypoint rendering), Settlemynts (a Mayor as a natural quest Issuer), Lyfe (XP rewards), and
  Yconomics (no real call site yet, kept declared for a future reward kind).
- **Explicit user direction**: "I like the idea that this could be a standalone mod, with API calls
  for other mods to register and assign quests" — `api.Myssions` is the real reason this is its own
  mod rather than a Settlemynts feature.

**First slice built 2026-10-10** (see [context/decisions.md](context/decisions.md),
[context/design-document.md](context/design-document.md) for the full ten-point spec and per-item
status) — of the user's original ten points, this slice covers: the Quest Log UI (hotkey `J`, two
independently-scrollable columns, Failed Quests grouped at the bottom, "Set as Active Quest"),
the Active Quest HUD (top-left, name + struck-through-on-complete milestones), per-issuer completed-
quest tracking, and quest time limits (hour/next-day-boundary/real-world-date, enforced by a
periodic deadline ticker). The full quest data model (definition/instance split, milestones with
optional-flagging, four-kind rewards) and the public `api.Myssions` facade are built. One hand-
authored wiring-test quest (`quest.SampleQuests`, granted via `/myssions givetest`) exercises the
whole pipeline end to end. `./gradlew build` green, boot-smoke-tested clean via standalone
`runServer` ("Myssions common setup" logged, zero exceptions).

**Explicitly NOT built this pass** (real follow-up work, not forgotten — see design-document.md's
own per-item status): the knowledge-scaled waypoint-hint system (item 2's map/circle half), the
real fetch-quest content type including the quest-token item and ten concrete quest variations
(item 7), a time-of-day-conditional NPC quest type (item 8), per-player-visible quest-tagged
entities (item 9), and party-based quest sharing (item 10, deferred until a party system exists at
all). Not yet wired into the real Production deploy scripts (`tools\update-and-restart.ps1`,
`ClientPack\build-mrpack.ps1`) — no real player-facing content exists yet. **Never opened in a real
client** — only boot-smoke-tested; the Quest Log UI's actual layout/scrolling/click-handling is
unverified in practice.

**Item 7 built, 2026-10-10** (see [context/decisions.md](context/decisions.md),
[context/classes/FetchQuestContent.md](context/classes/FetchQuestContent.md)) — the real "go kill N
<enemy>, bring back a token as proof" fetch-quest type. One reusable `quest.QuestProofItem`
(vanilla `item/rabbit_foot` icon, metadata-driven per quest, stack 128), `quest.QuestKillListener`
(tracks kill milestones, grants proof per qualifying kill), `QuestEngine#tryDeliverProof` (turn-in),
and ten real quest definitions (`quest.NpcQuests`) — four from the Lumberjack, four from the Farmer
(Settlemynts' two confirmed real spawned NPC-staffed Tier 1 structure types), two "Mayor" quests
issued via the Town Hall Plot Config Sign since no literal Mayor NPC exists anywhere in the suite
(confirmed by direct investigation before building — "Mayor" is purely a player role). Interaction
is shift+right-click, not plain right-click, since Settlemynts' own
`resident.VillagerShopInteractListener` already claims plain right-click on these NPCs to open their
Shop. Kill-tracking is deliberately not geo-fenced to each quest's own named location — that needs
item 2's still-unbuilt waypoint/knowledge system, flagged as an honest simplification, not an
oversight. `./gradlew build` green, boot-smoke-tested clean with all 8 suite mods together
(Settlemynts-optional bridge listeners registering with no classloading issues). **Never played
through end to end in a live client.**

**Items 8/9 built together, same day** (see
[context/classes/QuestNpcEntity.md](context/classes/QuestNpcEntity.md)) — a real
per-player-visible, day/night-conditional quest NPC. `quest.QuestNpcEntity#broadcastToPlayer` does
double duty: gates visibility to one owning player AND to daytime-only when flagged — a hidden NPC
is genuinely invisible to everyone, owner included, not just hidden client-side. `quest.
QuestAssignmentHooks` lets a quest spawn its own NPC the instant it's accepted. Real demo: "Find Old
Harlen" (a Mayor quest) spawns him next to the player on acceptance, visible only to them, gone at
night, completed by talking to him. Boot-smoke-tested clean (entity/attribute/renderer registration
included). **Never played through in a live client.**

**Item 2 built, same day** (see
[context/classes/QuestWaypoint.md](context/classes/QuestWaypoint.md)) — knowledge-scaled vague-
circle quest hints. **Corrected a wrong assumption first**: Lyfe owns the minimap/map, not
Cartographyr, which only owns the underlying geographic data. Lyfe gained real new capability for
this (`api.Lyfe#setQuestWaypoint`/`#clearQuestWaypoint`, a server-pushed waypoint separate from the
player's own manual pin, plus `#grantKnowledgeFactors`/`#getKnownFactorCount`) — its existing
waypoint system was a simple client-only manual pin with no server/radius concept at all. New
`quest.QuestWaypointTicker` (only registered when Lyfe is loaded) recomputes a deterministically-
seeded circle every second, radius scaled by known-factor count, sharpening to near-exact once the
player gets close (and grants the final factor). "Find Old Harlen" now targets a real point 80-200
blocks away instead of spawning at the player's feet. **Known simplification**: the circle is
always shown for the whole quest, not auto-hidden beyond a knowledge-scaled distance as the
original spec also asked for — flagged, not forgotten. `./gradlew build` green in both repos,
boot-smoke-tested clean with all 8 suite mods together. **Never rendered in a live client.**

Of the user's original ten points, only item 10 (party sharing, explicitly deferred until a party
system exists anywhere in the suite) has no work started at all.

**Scarce, midnight-generated quests, same day** (see
[context/classes/IssuerQuestBoard.md](context/classes/IssuerQuestBoard.md)) — explicit follow-up
once all ten original points existed, prompted by the user asking "is anything actually telling
NPCs to generate quests yet?" (honest answer at the time: no, the 10 quests were a static,
always-available pool). New `quest.IssuerQuestBoard` (persistent, one slot per plot) replaces
"always offer the first quest you don't have" — an NPC/sign now only ever offers whatever's on its
own board slot, nothing if empty, cleared the instant someone accepts so a second player finds
nothing there. `bridge.QuestBoardMidnightTicker` rolls a 50% chance per empty slot once a day.
`QuestEngine` now caps accepted quests at 5 — the "only 1 actively tracked" half needed zero code
changes, `activeQuestId` was already a single value from the first slice. **Explicitly still not**
"NPCs generating quests from their own real state" (a Shop's wishlist, a settlement wanting a road)
— that's the original motivating idea for this whole mod, and it's still real, unbuilt follow-up
work, now clearly separated from this scarcity refinement. `./gradlew build` green, boot-smoke-
tested clean with all 8 suite mods together. **Never live-tested.**

**Quests surfaced inside Settlemynts' own "Press H" chat menu, same day** (see
[context/decisions.md](context/decisions.md)) — explicit user request. New read-only
`quest.QuestGiverPreview`/`QuestTurnIn#preview` plus `api.Myssions#previewQuestGiver`/
`#interactWithQuestGiver`/`#hasPendingBoardOffer` let Settlemynts' own `bridge.MyssionsQuestBridge`
(a new optional soft dependency on this mod, the right direction for a UI integration) show and act
on a quest offer/delivery from its own chat screen, no shift+right-click required. `./gradlew build`
green in both repos, whole-suite boot-smoke-tested clean. **Not yet live-tested or deployed.**

**Six on-site quests retrofitted with a real location/waypoint, same day** (see
[context/decisions.md](context/decisions.md)) — real live-test feedback: a Farmer quest milestone
just said "near the fields," meaningless once left and forgotten, when the player already stood at
that exact plot to accept it. New `quest.QuestText#resolve` substitutes a `{location}` token with a
per-instance `QuestInstance#locationName()`; `NpcQuests#registerOnSiteHook` (the four Farmer quests
plus two of the four Lumberjack quests) resolves the real plot location via new
`bridge.SettlemyntsPlotLocationBridge` -> Settlemynts' new `api.Settlemynts#findPlotLocation`, sets
a real hint, and grants full knowledge immediately (not a rough bearing). The other four quests
(genuinely distant/vague wilderness or road threats) are deliberately untouched.
`quest.QuestAssignmentHooks` widened to also carry the issuing `plotId`. `./gradlew build` green in
both repos, whole-suite boot-smoke-tested clean. **Not yet live-tested or deployed.**

**Real per-instance UUID identity; "Complete Quest" menu, same day** (see
[context/decisions.md](context/decisions.md)) — two more real corrections from live testing. Quest
instances now carry a hidden, random `instanceId` (a `UUID`, never shown in any UI) instead of being
keyed by their bare definition id, so two separate acceptances of the same quest definition (e.g.
from two different NPCs) no longer collide — every per-instance API/payload now takes this id.
`quest.QuestTurnIn` rebuilt around three real operations (`getMenu`/`acceptBoardOffer`/
`completeQuest`) instead of one ambiguous `interact`, backing a real "pick your active quest by name,
then Complete Quest" flow on Settlemynts' own chat menu (see its own CLAUDE.md). `./gradlew build`
green in both repos, whole-suite boot-smoke-tested clean. **Not yet live-tested or deployed.**

See [context/classes/](context/classes/) for per-class reference.
