# Phase 4 — FTB Quests: investigation and implementation plan

Status: **Q1-Q7 approved by the user on 2026-11-19, with Q2 changed to "architecture-only source reading"; the review is in Section 7. Awaiting final go-ahead for Phase 5. No Phase 5 code written.** Date of investigation: 2026-11-19.
Labels: **[V]** verified from a cited public source, **[C]** conclusion, **[H]** hypothesis / from general knowledge, unverified in this session, **[R]** recommendation.

## 0. How this investigation was done (and its limits)

- **Sources used [V]:** CurseForge pages (Quests, Library, Teams), the official FTB docs site (Quests, Teams, reward tables, chapter settings, styling, navigation), GitHub license metadata and the license text of FTB Library, the CurseForge license pages, and the license of Architectury. Nothing was cloned. **No FTB source code was read.**
- **Why no source:** FTB Library is *All Rights Reserved* [V: [LICENSE.md](https://github.com/FTBTeam/FTB-Library/blob/dev/LICENSE.md)]; FTB Quests and FTB Teams carry no license file in their repositories [V: repo root listings], which under default copyright means all rights reserved [C]; CurseForge shows "All Rights Reserved unless otherwise explicitly stated" for all three [V]. None of these texts has an explicit AI clause (unlike HE-RSAL, Phase 1), but none grants any reuse right either. Reading source to *understand architecture* is a separate decision for you (Q2 below); I did not make it for you.
- **Disclosure:** the FTB docs pages were fetched by automated agents (about 20 pages). FTB's site terms (updated 2023-10-01) reserve text-and-data-mining and prohibit scraping of "the site" [V: [terms](https://feed-the-beast.com/policies/terms-and-conditions), Sections 8-9]. Whether this covers the docs subdomain, and whether a few page reads count, is unclear to me **[C, not legal advice]**. I will fetch no more from that domain without your say-so, and this document uses the docs only for feature-level facts (what a feature does), never their wording or tables of numbers.
- **Not verified:** anything about upstream's internal architecture, serialization format, network protocol and GUI code. Statements about those are marked **[H]** and must not be relied on.

## 1. Upstream at a glance (public facts)

| Item | Finding |
|---|---|
| Latest version | 2101.1.39 for 1.21.1 (NeoForge), 2026-11-16; newest 26.1 build 26.1.2.10 (2026-11-05); newest Forge-loader build seen 2001.4.22 (1.20.1) [V: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/files/all?page=1&pageSize=20&showAlphaFiles=hide)] |
| 1.12.2 | An **official 1.12.2 build exists** (1202.9.0.15, Jan 2021, 207 results under the 1.12.2 filter) [V: same listing with `&version=1.12.2`]. It is also All Rights Reserved. Its dependencies were not checked [not verified]. So "FTB Quests for 1.12.2" already exists in an older feature state; our value is a **modern-feature-set, independently built, Cleanroom-native** quest mod |
| Loader of current versions | multi-loader (NeoForge/Fabric) over Architectury [V] |
| Required deps (current) | Architectury API, FTB Library, FTB Teams; optional FTB XMod Compat [V: [relations](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/relations/dependencies)] |
| Doc state | Docs written for 1.21; they do not describe file storage, SNBT layout, config options or permission levels [V] |

## 2. Dependency analysis (refined prompt 4.2)

| Dependency | Purpose for Quests | Needed by which Quests features | 1.12.2 equivalent / our answer | Needs own referencing impl? | Mandatory? | License |
|---|---|---|---|---|---|---|
| **Architectury API** | multi-loader abstraction | none on our side | **Not needed**: single loader (Cleanroom) | No | n/a | LGPL-3.0 [V: GitHub metadata] |
| **FTB Library** | GUI framework, config library, SNBT system and config, NBT editor, sidebar buttons, utility commands [V: CurseForge description] | Questbook GUI, data files, config | **Reflib** provides GUI toolkit (`gui`), `Snbt` text format, `DataUpgrader`, `NbtBridge`; widgets still missing (Section 5). NBT editor and utility commands: exclude | Only the minimum, inside reflib or the project | Yes upstream; **No** for us | All Rights Reserved [V] |
| **FTB Teams** | team/party model, team commands, team data [V: CurseForge/docs] | team progress, shared completion, reward blocking per team | Reflib `Teams` SPI exists; need a **party-team implementation** (create, invite, join, leave, kick, server teams). Recommend a small separate mod `reflib-teams` (D-Q3) | Yes (minimal) | Optional for us if solo-only is acceptable | All Rights Reserved [V] |
| **FTB XMod Compat** | JEI/REI/KubeJS/Game Stages bridges, tag filters [V: README text via GitHub metadata and docs] | JEI recipe view, stage tasks/rewards, tag item filters | JEI (1.12.2 version exists **[H]**), OreDictionary replaces tags, Game Stages mod for 1.12.2 exists **[H]**; implement as optional integrations via reflib `Integrations` | No (per-feature adapters inside the project) | Optional | All Rights Reserved [V: repo has no license file; CurseForge ARR for sibling mods] |
| **FTB Filter System** | tag filters for item tasks [V: docs mention] | tag-based item tasks | OreDictionary matching; own simple filter | No | Optional | not checked |
| **KubeJS / Game Stages** (optional) | scripting events, stage system | Stage tasks/rewards, events | KubeJS has no practical 1.12.2 path **[H]**; expose a plain Forge event API instead; Game Stages optional **[H]** | No | Optional | n/a |
| **JEI** | recipe display of quest items | per-quest "disable JEI recipe" | JEI 4.x for 1.12.2 (the template already runs it in dev) [V: repo build file] | No | Optional | JEI's own |

**Minimum functionality to support the quest mod [R]:** (a) GUI toolkit incl. text input and a pannable canvas, (b) text data format with versioning and safe file I/O, (c) reliable big-message sync, (d) a team provider (party teams optional), (e) optional JEI adapter.

## 3. Licensing and referencing feasibility (refined prompt 4.3)

Working rule **[R]:** we implement *behaviour* described in public documentation and observable in play, using our own code, names, assets, strings and file schema. We use no FTB code, textures, translations, theme files or branding.

| Class | Items |
|---|---|
| **Independently implementable** | chapters/groups; quest map; quest shapes (our own artwork); tasks and rewards listed in Section 4; reward tables/loot crates (own design); progression and visibility options; editing mode; team-shared progress; resource-pack theming (own format); commands (own naming) |
| **Existing 1.12.2 API** | OreDictionary (instead of tags); Forge Energy capability; vanilla advancements and advancement toasts; loot tables for crate drops (`LootTableManager`) **[H on details]**; stats; JEI; world/biome/structure queries |
| **New compat implementation (ours, in reflib or project)** | pannable/zoomable canvas widget, text field and rich text, tooltip layer, context menu; chunked message transport and join snapshot/delta sync; atomic data-file read/write; party teams |
| **Needs permission or clarification** | **Reading/importing upstream quest files** (compat with real FTB packs): the *format* is a fact, but learning it needs source or sample files whose license/AI terms are unclear (Q3); using upstream textures/lang; any logo/name |
| **Exclude** | FTB branding/names and the `ftbquests` namespace (avoid implying affiliation); NBT editor; FTB Library utility commands; KubeJS integration; upstream translations and artwork; Architectury |

**Naming [R]:** pick a neutral project name and mod id (placeholder `reflibquests`), say "inspired by FTB Quests; not affiliated" in the README, and do not use FTB marks.

## 4. Feature overview (refined prompt 4.4) — suggested v1 defaults

Facts about upstream behaviour are from the public docs [V]; "How on 1.12.2" is our design [R].

| Feature | What it does | How on 1.12.2 | Needs lib work | Limitations |
|---|---|---|---|---|
| **Quest book, chapters, chapter groups** | Book opened by key/item/button; chapters (grouped) hold quests on a pannable map; chapter list on screen edge; search | Our `ReflibScreen` tree; book item + keybind; groups as a tree | **Canvas (pan/zoom), scrollbars, text field, tooltips** | Keybind unbound by default (same as upstream) |
| **Quest editing** | In-game editor: right-click map to add quests, per-object settings; editing mode toggled per player by command | Editor screens built from one **declarative settings description** per object (title, icon, shape, tasks, rewards, deps...). Permission: op/command | Form widgets (checkbox, int/text field, dropdown, item picker) | Largest GUI effort; v1 editor can be text-assisted (edit file + `/reload`) then GUI |
| **Tasks (v1)** | Item (detect/consume), Checkmark, XP levels, Kill Entity, Location, Advancement, Visit Dimension, Stat, Observation, Custom (API) | Server tick/event hooks (inventory scan, `LivingDeathEvent`, `AdvancementEvent`, stat/XP checks); progress stored per team | Event dispatch helper in the project | Item task by OreDict not tag; 1.12.2 items use NBT, no components |
| **Tasks (later)** | Visit Biome, Find Structure, Fluid, Forge Energy, Stage, Image (decorative) | Biome via world query; structure via chunk-generator query **[H]**; FE/fluid via capabilities; Stage via optional Game Stages | none | Structure detection in 1.12.2 is limited/modded-structure dependent |
| **Rewards (v1)** | Item, Choice, Random/All/Loot table, Command, XP, XP levels, Advancement, Toast, Custom | Reward tables as our data; toast via 1.12.2 toast system; claim-all button | none | Loot-crate mob drops need a loot function; defer crates to later |
| **Dependencies and progression** | Required quests with all/one completed or started, min count; hide/invisible options; optional, repeatable, linear/flexible, sequential tasks | Pure model logic (unit-testable); visibility computed per team on server and sent | none | none |
| **Multiplayer sync** | Server authoritative; clients see book and team progress | Snapshot on join + deltas via `ReflibChannel`; version stamps | **Message chunking, protocol handshake, dirty-set helper** (these three are NOT built yet, Section 5) | Packet size cap 30 KB per message until chunking exists |
| **Team progression** | Progress per team; a task by any member counts for all; per-team reward blocking | `Teams` SPI; party implementation in `reflib-teams`; solo default | Party team mod | Server teams, allies out of v1 |
| **Visibility and organization** | Hide until startable/deps visible; invisible-until-complete; ordering; linked quests | Model flags + server-side filtering of what is sent | none | Linked quests later |
| **Customization** | Quest shapes, icons, colors, resource-pack theme file | Our own theme file in a resource pack, our own shapes | Theme loader (project) | Own art needed; upstream art not usable |
| **Integrations** | JEI recipe view, stages, tag filters | `Integrations.register(...)` adapters (JEI, Game Stages) | none | KubeJS excluded |
| **Commands/admin** | open book, change progress, lock/editing mode, reload, reward-table import/export | Own command set mirroring *capabilities* not names | none | permission levels via vanilla op levels |
| **Config** | Server rules, client UI options | Forge `@Config` (no lib wrapper) | none | none |

## 5. Technical plan (refined prompt 4.5)

### 5.1 Project structure [R]
```
reflib/                  (done: core + gui)                          LGPL-2.1
reflib-teams/            small party-team mod implementing TeamProvider   (D-Q3)
reflibquests/            the referencing mod: depends on reflib, optional reflib-teams, optional JEI/GameStages
  model/                 chapters, quests, tasks, rewards, tables, progress   (pure Java, no Minecraft)
  io/                    our SNBT schema, atomic file read/write, upgrader
  server/                task hooks, progress, claim, commands, sync
  net/                   messages
  client/                questbook screens, editor, theme
```
Dependency graph: `reflibquests -> reflib`; `reflibquests -> reflib-teams` (optional); `reflib-teams -> reflib`; `reflibquests -> JEI, GameStages` (optional adapters).

### 5.2 Required reflib additions (need your approval as a lib change, D-Q5)
1. **Message chunking** over `ReflibChannel` (quest data > 30 KB) plus a protocol handshake.
2. **Sync helper**: join snapshot + per-player dirty deltas.
3. **Widgets**: text field, checkbox, dropdown, scrollbar, tooltip layer, item icon, zoomable/pannable canvas, context menu. Rule kept: only what Quests needs.
4. **File I/O helper**: atomic write, backup, load errors reported with line/column (the parser already does).
5. Fix known limitation: `ScrollPanel` nested containers.

### 5.3 Implementation order
M0 lib additions (1-5) with tests → M1 model + progression logic + file schema (pure Java, heavy unit tests) → M2 server: task hooks, progress, claim, commands → M3 sync + minimal read-only book screen → M4 teams (`reflib-teams`) → M5 editor GUI → M6 rewards tables, theme, JEI/Game Stages adapters → M7 polish.

### 5.4 Testing strategy
- Unit tests for model, dependency/visibility rules, progress, serialization round trips, upgrader, chunking (as in Phase 3).
- Headless dedicated-server smoke test per milestone (pattern proven in Phase 3).
- **Multiplayer test**: dedicated server plus a real client connecting (dev client `--server`), for join snapshot, shared team progress, reward claiming, and reconnect. Not yet exercised in Phase 3.
- Manual GUI checklist (you test visually, as in Phase 3).
- Claims of "works" only for what these runs verify.

### 5.5 Risks
1. Large-pack data volume and sync design (chunking, partial updates).
2. GUI size and polish: the biggest cost; the editor especially.
3. Reading upstream format: legal/AI-term ambiguity (Q2, Q3); without it there is no pack compatibility.
4. Cleanroom alpha churn and untested remote multiplayer path.
5. Task detection correctness (inventory scans, edge cases) needs careful tests.
6. Trademark/affiliation confusion if names are reused.

### 5.6 Realistic vs substantial
Realistic early: model, tasks (item, checkmark, XP, kill, advancement, dimension, stat), rewards (item, XP, command, choice, tables), dependencies/visibility, team progress, read-only book, commands, config. Substantial: full in-game editor, themes/shapes art, structure/biome tasks, loot crates with mob drops, upstream-pack import.

## 6. Decisions (all resolved 2026-11-19)

**Resolved:** Q1 neutral name, Q2 **architecture-only source reading** (user's choice; see Section 7 for rules and results), Q3 own schema, Q4 docs use accepted, Q5 reflib additions as step M0, Q6 separate `reflib-teams`, Q7 v1 scope. Original wording kept below for reference.

- **Q1 Name/branding:** neutral name and id (`reflibquests` as placeholder), "inspired by, not affiliated" statement. [R: yes]
- **Q2 Source-reading policy for FTB repos:** (a) **no source reading**: build from public docs and observed behaviour only (recommended, safest); (b) read upstream source for architecture only, no copying, none of it in the repo; (c) ask FTB for written permission first. Decide before any source is opened.
- **Q3 Data compatibility:** (a) own schema, no upstream import (recommended; consistent with Q2a); (b) aim to import upstream quest files, which needs Q2b or sample-file analysis and carries the legal ambiguity above.
- **Q4 Docs use:** confirm you are comfortable with the feature-level use of the FTB docs already fetched, and that I fetch nothing more from that domain.
- **Q5 Reflib additions** (5.2) approved as Phase 5 step M0.
- **Q6 Teams as a separate mod `reflib-teams`** (recommended) vs inside the Quests project vs solo-only v1.
- **Q7 v1 scope:** the "Tasks (v1)" and "Rewards (v1)" sets above; editor via text-then-GUI.

## 7. Architecture-only source review (user decision Q2b)

**Rules followed:** read through the GitHub API into the working context only; nothing cloned or saved; notes below are my own summary of structure and responsibilities (package and class *names* were used to identify features, no code, comments or strings are reproduced); our implementation will not mirror upstream class structure, names, wire format or file schema. Scope: FTB Quests only (package layout, class inventory, signatures of the file/progress/sync classes). FTB Library and FTB Teams source were **not** read; their role is taken from CurseForge and docs.

**What the review verified [V, from source structure]:**
1. **Layout:** a platform-neutral `common` module plus thin Fabric and NeoForge modules, joined by a service interface. The mod has about 65 network message types, a client GUI package with ~50 screens/panels, and a progression-event API for other mods.
2. **Data model:** all quest "objects" (file, chapter group, chapter, quest, quest link, task, reward, reward table, loot crate) share a base type with 64-bit ids, held in an id map; task and reward *types* are registered in registries, so new types plug in.
3. **Two stores:** quest *definitions* live in a config-folder tree (a data file, a chapter-groups file, `chapters/` with one file per chapter, `reward_tables/`, `lang/` for translations) written through a JSON5 helper in the current version; **team progress** is stored separately under the world save, per team. (My earlier assumption that upstream stores definitions as SNBT is therefore wrong for the current version.) Separate code paths serialize for disk and for network.
4. **Progress model:** per team: task progress, started/completed times per quest, per-player reward-claim times, completion counts and repeat cooldowns, locked and reward-blocked flags, plus derived caches for "are dependencies complete/visible". Server-authoritative; saved when marked dirty.
5. **Sync:** on join the server sends the *whole* quest file in one message, the client then requests its team data; afterwards small messages carry edits (create/edit/delete/move, each with a response), started/completed notifications, task-progress updates and team-data changes.
6. **Task detection:** item tasks react to inventory-slot changes on the main inventory (armor deliberately skipped to avoid scan storms), crafted items, a deferred per-tick pass, and a manual "submit" message.
7. **Editing:** server-side edit history with undo/redo, move operations, copy/paste of quests, per-language translation tables, visual presets.
8. **Extras beyond the docs summary:** physical blocks (multi-size task screens plus a configurator item, quest barrier, stage barrier, detector, loot-crate opener), an interaction task, currency reward, auto-claim reward modes, pinned-quest HUD tracker, emergency-items screen, chapter images, quest links, pause-progression and clear-cooldown commands.

**Check against our feature list (Section 4):**

| Finding | Effect on our plan |
|---|---|
| Full-file sync in one message | Confirms the need for **message chunking** (M0). Our design: chunked snapshot **plus** per-object deltas, with version stamps |
| Definitions vs progress stored apart | Adopt the same *principle* (config folder vs world data); our own schema stays (Q3a) |
| Separate disk/net serialization | Matches our `DataNode` + `Snbt` for disk and `ReflibMessage` for wire; keep them separate |
| Registries for task/reward types | Keep `TaskType`/`RewardType` plug-in registries in our project; expose for other mods |
| Progress events API | **Add to v1:** post simple Forge events (quest started/completed, task progress); low cost |
| Edit history with undo/redo | **Add to editor scope (M5)**; not v1 |
| Translation tables | Deferred: v1 stores text in the data file; per-language tables later |
| Blocks (task screen, barriers, detector, crate opener) | **Not in v1.** Barriers and detector are moderate; multi-size task screens (TESR) are substantial. Listed as later |
| Interaction task, currency reward, auto-claim, pin HUD, emergency items, chapter images, quest links, visual presets | Interaction task and auto-claim: v1.1. Others: later. None blocks v1 |
| Armor slots skipped in inventory detection | Design item detection to scan lazily and rate-limit; avoid per-slot scans on every change |
| Platform split (common + loaders) | Not needed: single loader. Keep model code free of Minecraft types anyway (unit-testable) |

**Residual unknowns (not learned, by design):** exact wire formats, exact file schema, GUI layout and behaviours beyond what the docs state. These are ours to design.

## 8. Revision during Phase 5 (user decisions, 2026-11-19)

The user clarified that the goal is a "port" in the ArchaicLib sense that works with the original upstream jars, and decided:

1. **Assets from player-supplied upstream jars.** Reverses D7. reflib gains a loader that reads textures (and text converted from the 1.21 JSON language format to 1.12.2 `.lang`) from the player's own FTB Quests / FTB Library / FTB Teams jars placed in a game-folder directory. We ship no FTB assets; without the jars the mod falls back to its own placeholder art. Upstream *code* is never loaded (it targets 1.21 and cannot run on 1.12.2), so the mod's logic stays our own. Licensing note unchanged: this is a distribution model, not a legal clearance (all three projects are All Rights Reserved).
2. **Import of upstream quest files.** Reverses Q3a. Requires the upstream file schema; the user approved reading source for **schema-level facts only** (layout, field names, types, meaning). Our parser is our own; the schema is documented in our own words in `docs/upstream-quest-format.md`.

Consequences for the plan: new step **M0b** (asset loader) before the model; the model uses string ids so upstream's ids fit; the file codec in M1 reads upstream layout first and can write it back; the earlier "own schema" is replaced by "upstream-compatible schema" wherever the two would differ.
