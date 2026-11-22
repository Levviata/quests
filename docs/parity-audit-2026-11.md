# Reflib Quests: source audit and roadmap (2026-11-21)

Phases 1 and 2 of the full-parity assignment. Written from the **source code at commit `6c8058d`** (build and unit tests pass, `./gradlew build` exit 0 on 2026-11-21), not from `feature-catalogue.md`, the README or code comments. Categories and terminology follow the user's spreadsheet (mirrored in `docs/feature-catalogue.md`). The spreadsheet itself is untouched.

## How statuses are assigned

- **Complete**: implemented end to end *and* seen working in game (by the user or in a documented capture). Nothing is marked Complete from code reading alone.
- **Unverified**: the code path exists end to end but nobody has run it in game (or only in a unit test).
- **Partial / Missing / Broken**: as the assignment defines them. No feature is currently known to be Broken; that is only because nothing was re-run this session (see "Not tested this session").
- Evidence that a status came from a *previous* in-game check is cited as `V:` (the hand-off section 6, README "Verified in game", or a user confirmation). This session launched neither game.

The catalogue's counts (62 / 18 / 31) are **out of date**: the code has moved a long way past the spreadsheet (rows below marked "was ..."). Re-counting is at the end.

## Corrections to the catalogue found by reading the code

| Catalogue said | Source says |
|---|---|
| Task menu: Stat, Stage, Fluid, Interaction, Energy | Listed in `QuestBookWidget.taskTypeMenu` as greyed `typeOff(...)`; no tracker, not in `QuestServer.SUPPORTED_TASKS`. Still Missing. |
| Chapter images "no lock, text or click action" | `BookEditor` handles `corner`, `position_locked`, `text_on_image`, `text_inset`, `text_h_align`, `text_v_align`, `click_type`/`click_data`, `dependency`, `tags` (lines ~839-941) and `ImagePropertiesScreen` edits them. What is missing is **creating** an image in a chapter (no `create_image` op; "Add Image" only inserts an image tag in a quest description). |
| Curved (Bezier) dependency lines: Not implemented | `set_bezier` in `QuestEdits`, grab-and-drag handles in `QuestBookWidget` (~l.1278), `History` undo. Implemented, Unverified. |
| Quest links: client never draws them | `QuestBookWidget` rebuilds quest links each frame (~l.3917) and `paste_quests` / "Alt: paste as links" exists. Implemented, Unverified. The class comment at the top of the widget is stale. |
| Selection, copy, paste, drag box: Not implemented | `selectionKey`, `SELECTION`, `paste_quests`, `move_quests`, `delete_quests` exist. Implemented, Unverified. |
| Min opened window width, tags, Grid Scale, Emergency Items cooldown: absent | Present in `BookEditor` (`min_width`, `tags`, `grid_scale`, `emergency_items_cooldown`) and the settings screens. See "stored but inert" below. |
| Autofocus quest: Implemented | Still true (`set_chapter_prop autofocus`); Unverified. |
| Quest icon / chapter icon: no edit op | `set_icon` op and an Icon row in `QuestPropertiesScreen` / `ChapterPropertiesScreen`. Implemented (item/preset choices only; custom item picker not checked). Unverified. |

## Book settings that are stored but do nothing

`BookSettingsScreen` labels these itself "Stored in the book; Reflib Quests does not use it yet". They survive an import and export but have **no behaviour**:

`drop_book_on_death`, `emergency_items_cooldown` (and there are no emergency items), `drop_loot_crates`, `loot_crate_no_drop` boss/monster/passive (and there are no crates), `lock_message`, `hide_excluded_quests`, `show_lock_icons` (read, not checked for effect), `verify_on_load`. Working: `disable_gui` (`ClientBootstrap` l.98), `pause_game` (`QuestScreen` l.39), `detection_delay` (`TaskTracker.detectionDelay`), `default_autoclaim_rewards` (`QuestServer.autoClaims`), `progression_mode`, `fallback_locale`.

## Audit by category

`E` = reachable through the editor UI. `Ser` = round-trips through import and export (`UpstreamImporter`/`UpstreamExporter`). `Net` = server-validated edit (`BookEditor` via `QuestServer`).

### Tasks

| Feature | Status | Evidence | Gap |
|---|---|---|---|
| Item | Complete | `TaskTracker.pollItem`, `submit`; `V:` README item/consume test by the user | Item NBT mode / ignore damage / "hide valid items" / grab-copy absent. Only-from-crafting key exists in UI: whether `TaskTracker` honours it is **not checked** (Unverified) |
| Custom | Partial | In `SUPPORTED_TASKS`, E (menu), Ser. No code advances it; only the `api.QuestEvents` hook other mods may use | nothing in-game can complete it except via API |
| XP Levels / points | Unverified | `TaskTracker.tick` case `xp` | |
| Visit Dimension | Unverified | `case "dimension"` | README: not exercised in game |
| Kill Entity | Complete | `TaskTracker.onKill`; `V:` README | entity-name / entity-tag filters Missing |
| Location | Unverified | `TaskTracker.inLocation` (box, `ignore_dimension`) | |
| Checkmark | Complete | `TaskTracker.check`; `V:` README | |
| Advancement | Unverified | `onAdvancement` | README: not exercised |
| Observation | Partial | `case "observation"` block within 6 blocks, seeing it once counts | `timer` ignored, no entity observation |
| Visit Biome | Unverified | `case "biome"`; `SUPPORTED_TASKS` check rejects biomes missing in 1.12.2 | |
| Find Structure | Partial | `structureName` map of 1.12.2 structures only | newer structures cannot exist in 1.12.2 (Intentional difference) |
| Stat | Missing | no tracker; `QuestText.task` prints "(not supported yet)" | needs vanilla `StatList` mapping |
| Stage | Missing | no game-stage code | needs an integration decision |
| Fluid | Missing | only `LegacyImporter` converts | needs fluid handler detection, UI |
| Forge Energy | Missing | only `maxProgress` key read | |
| Interaction | Missing | none | |
| Item filter by tag | Partial | `ItemSet` maps `ore()` / tool tags to ore dictionary | 1.12.2 has no tags (Intentional difference) |
| Task screen / barrier blocks | Missing | `ReflibQuestsMod` registers no blocks or items | |

### Rewards and reward tables

| Feature | Status | Evidence | Gap |
|---|---|---|---|
| Item, XP, XP Levels, Command (perm level, silent, feedback), Advancement (+criterion), Custom | Unverified (Item `V:` Complete) | `RewardGranter` cases | |
| Choice, Random, Loot, All Table | Unverified | `grantFromTable` | README: all_table not exercised |
| Toast reward | Partial | `RewardGranter` `toast` sends a **chat message**, not a toast | |
| Stage reward, Currency reward | Missing | no code | |
| Per-reward options: team reward, exclude from claim-all, ignore blocking, disable screen blur, only_one | Unverified | `BookEditor` l.1527-1545 stores; `QuestBookWidget` reads `team_reward`; whether `QuestServer` honours each flag is **not checked** | |
| Reward tables editor | Unverified | `RewardTablesScreen`, `TableEdits`, `BookEditor` table ops; FTB uses Accept/Cancel (`parity-audit.md` s.1) | |
| Loot crates (item, rarity, nested, /give, drop weights) | Missing | no item registered | |
| Export/import table from chest; delete empty tables command | Missing | none | |

### Quest settings

All of the following are E, Net and Ser; status **Unverified** unless noted, because only the visual items in hand-off section 6 were captured:

Title, Subtitle, Description (`DescriptionEditorScreen`, `RichText`), Icon (preset choices), Shape (10 shapes; art requires the supplied jar, else squares), Size, X/Y, Disable completion toast, Hide details until startable, Hide until deps visible / complete, Hide text until complete, Hide lock icon, Invisible, Invisible until N tasks, Dependencies, Dependency requirement, Min required deps, Hide dependency / dependent lines, Disable JEI recipe, Ignore reward blocking, Optional, Repeatable (+cooldown), Progression mode, Sequential tasks, Autofocus.

Missing: icon scaling, visual presets (`data.json5 presets` is imported as data only; `visual_preset` has no code), max completable dependents, exclusive branch, tasks-ignore-dependencies, guide page, colour picker, description click/hover events (JSON components), Text Shadow.

Min opened window width: `min_width` is stored on chapters and quests (`BookEditor`); whether the quest window uses it was not found in `QuestBookWidget` -> **Partial**.

### Chapters

Create / order / move / group / rename / delete: Unverified (E, Net, Ser; panel structure `V:` hand-off s.6). Chapter settings: Unverified. Chapter icon: Unverified. Chapter subtitle, default size, default preset: `subtitle` op exists (`BookEditor` l.204), default size `quest_size` l.229: Unverified; default preset Missing. Chapter images: Partial (see above). Chapter images' click actions beyond `open_uri`: UI offers only "No Action / Open a URI" plus a passthrough of an unknown type; FTB has six types, keys not yet captured.

### Styling, commands, editor, player

| Feature | Status | Evidence / gap |
|---|---|---|
| Theme in resource pack; styles by ID or tag; line colour / thickness / animation; full-screen quest window | Missing | no loader |
| Custom shapes | Partial | fixed list of 10 |
| Commands | Partial | `/rquests`: list info check submit claim claimall complete reset reload lock blockrewards problems export; `/rqbook`. Missing: reset-all, complete-all, editing_mode, change_progress, generate chapter, clear display cache, delete empty tables, import/export chest |
| Undo / redo | Unverified | `History` + `HistoryTest` unit tests (198 lines) |
| Bezier lines | Unverified | see corrections |
| Visual presets | Missing | |
| Selection / copy / paste / quest links | Unverified | see corrections |
| Hotkeys (force-complete, reset, save) | Partial | `parity-audit.md` s.3 |
| Open book key / item / inventory button | Partial | key `J` and `/rqbook` only; no book item |
| Search, pin, tracker, chapter panel, claim, toast, alerts, dependency dropdowns, key reference, player preferences | Search/pin/tracker/claim: Complete (`V:` README, user); others Unverified (layout `V:` hand-off s.6) |
| Notifications: toast / chat / none | Partial | no action bar |
| Emergency items, suppress-auto-claim switch | Missing | |
| Team progress | Complete | `V:` README, two real clients |

## Counts

No exact totals yet. Several rows above group many features (for example one row for all quest settings), so a count made now would be invented. Final counts of Complete / Partial / Missing / Broken / Unverified are produced in Phase 9 from a per-feature table. What can be said today: very few rows are Complete (item, kill, checkmark, team progress, search/pin/tracker/claim), most editor options are Unverified, the Missing set is the one listed in the Tasks, Rewards, Styling, Commands and P3 sections, and nothing is known Broken only because nothing was re-run.

## Phase 2: priorities and roadmap

Ordering is by what the **reference book needs** and by dependency, not by spreadsheet order. Test method for every item: unit test where logic is pure, then in-game check in the showcase chapter named, then export and re-import.

### P0 foundational (the book must load, run and save truthfully)

| # | Item | Why | Needs | Depends on | Test |
|---|---|---|---|---|---|
| P0.1 | Re-verify the hand-off s.7 items in game (titles in lang, task pictures, chevrons, eye, tooltips) | uncommitted-then-committed work never seen running | launch ours (user closes it first) | none | capture the four chapters |
| P0.2 | Importer round-trip report for a real 26.1.2 book | Phase 6 asks for every property accounted for; `UpstreamImporter` has only hand-written-sample tests | a "preserve unknown keys" check; a diff tool (import, export, compare to source) | reference book | unit test on the real showcase files plus the new book |
| P0.3 | Unknown/unsupported properties preserved, not dropped | the assignment forbids silent loss; `BookEditor` keeps `raw()` for images but task/quest handling per key is not audited | audit each `Definitions` type for a raw-map fallback | P0.2 | round-trip equality test |
| P0.4 | Tasks that cannot progress must say so in the UI | greyed in the add menu but imported ones render "(not supported yet)" | already partly done (`/rquests problems`) | none | import book with stat/fluid |

### P1 core parity (features the reference book exercises)

| # | Item | Needs | Depends on |
|---|---|---|---|
| P1.1 | Stat task (`stat`, `value`) | map 26.1.2 `minecraft:mob_kills` style ids to 1.12.2 `StatList`; tracker; edit screen | P0.2 |
| P1.2 | Chapter-level image creation (`create_image` op, editor entry point, Add Image in chapter menu) | new BookEditor op, History, UI | none |
| P1.3 | Visual presets (book `presets`, quest `visual_preset`, chapter default) | model fields, resolution order, editor rows | P0.3 |
| P1.4 | Min opened window width and icon scaling used by the quest window | read in `QuestBookWidget` | none |
| P1.5 | Observation timer, observation entities | tracker | none |
| P1.6 | Toast reward as a real toast | `QuestToast` reuse | none |
| P1.7 | Task/reward option flags honoured: only_from_crafting, match_components, consume_items per task, team_reward, exclude_from_claim_all | check `TaskTracker`/`QuestServer`; unit tests | P0.1 |
| P1.8 | Stage task/reward | decide integration (GameStages 1.12.2 mod) or leave Intentional | user decision |
| P1.9 | Fluid and Forge Energy tasks | capability detection, UI | none |

### P2 interface and presentation

Map check icon exactness; "Edit" arrow glyph; eye in the quest window; toast height for long names; group Quick Edit contents; quick-edit list (hand-off s.8 lists the exact FTB entries); colour picker (RGB wheel and palettes); description click/hover events; Text Shadow; image click-action types; Accept/Cancel on reward table windows; Key Reference rewrite (`parity-audit.md` s.3). Each needs an FTB capture first (hand-off s.8-9).

### P3 advanced and secondary

Loot crates and their three drop weights, Emergency Items, theme file, quest barriers, task screen blocks, commands (reset-all, complete-all, change_progress, editing_mode, chapter generation, delete empty tables), action-bar notifications, book item and inventory button, Interaction task, entity-name/tag kill filters, Currency reward, Exclusive branch, Max completable dependents, Lock message, Hide excluded quests, Drop book on death.

### Dependency chain

`P0.2 -> P0.3 -> P1.3, P1.1`; `P1.2` independent; `P1.7` before P2 task windows; `P1.8` blocked on a decision; Phase 5 (reference book growth) needs FTB's UI per feature, so **P1/P2 items cannot start before their Phase 4 capture**: do not guess keys.

## Not tested this session

No game was launched or driven in this session (FTB 26.1.2 was already running as `javaw`; ours was not). Everything marked `V:` is inherited evidence. The build and the unit tests were run and pass.
