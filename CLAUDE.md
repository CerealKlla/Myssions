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

See [context/classes/](context/classes/) for per-class reference.
