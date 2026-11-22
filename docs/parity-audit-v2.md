# Parity audit v2: Reflib Quests against FTB Quests 26.1.2 (Phase 1 of the 2026-11-22 assignment)

Written from the **source at commit `dc7706c`** (branch `claude/reflib-ftb-quests-parity-00a07b`). Each row was re-read in the code path named in "Evidence": `TaskTracker`, `RewardGranter`, `QuestRules`, `ProgressEngine`, `QuestServer`, `QuestEdits`/`BookEditor` (op lists), `QuestCommand`, `ClientBootstrap`, `QuestBookWidget`, `Definitions`. Comments, README text and the older audits (`parity-audit-2026-11.md`, `phase9-final-audit.md`, `feature-catalogue.md`) were **not** trusted; where they disagree with the code, the code wins and the row says so. The user's spreadsheet is untouched; categories and wording follow it (111 rows: 89 on "Official docs", 22 on "Changelog and wiki").

## Status words
- **Complete**: implemented end to end and seen working in game (a documented capture or the user's own test, cited as `G:<doc>`).
- **Partial**: works, but something FTB does is missing.
- **Missing**: no functional code.
- **Broken**: implemented and seen failing. None known.
- **Unverified**: the code path exists through data, server, network and UI, but nobody has seen it run in game (a unit test may exist).

Nothing was run in game while writing this file. Evidence of "seen in game" is inherited from earlier sessions' documents and is cited, not re-claimed.

Columns: **UI** = can be created/edited from the in-game editor. **Ver.** = verified in game (G), unit test only (U), code only (C).

## Corrections to the old audits (found by reading the code)
| Old claim | What the code says |
|---|---|
| Catalogue: "XP Levels polled in `TaskTracker.tick` case xp" | No `xp` case in `tick`. XP tasks progress only through `submit` (`submitExperience`, the player clicks the task and pays). Same as FTB (XP is consumed by click), so this is fine, but the old evidence was wrong. |
| Catalogue: "Switch to suppress all reward autoclaiming: Not implemented" | Implemented: `QuestServer.autoClaims` reads `suppress_all_autoclaiming`. Not editable in the book settings screen (grep: only the server reads it), so it is file-only. |
| phase9 row 37 "Reward options ... effect of each C" | **`team_reward` and `only_one` are read by nothing in the game code** (grep over `game/`, `model/`): only the UI and `BookEditor` store them. `exclude_from_claim_all` is honoured by `QuestServer.claimAll` only. `ignore_reward_blocking` is honoured by `ProgressEngine.canClaim`. |
| phase9 row 20 "Task options effect C/U" | **`only_from_crafting` is stored but `TaskTracker.pollItem` counts the inventory regardless.** `only_from_task_screen` has no meaning without Task Screen blocks. |
| Advancement task `criterion` | Stored; `onAdvancement` completes on the whole advancement id and never looks at `criterion`. |
| Observation `timer`, `observation_type: entity` | Stored; the tracker accepts only a block seen within 6 blocks, once. `/rquests problems` reports entity observation as inert. |
| Quest windows `min_width` | Used for the chapter value only (`QuestBookWidget:294`); a per-quest `min_width` is not read there. |
| Quest links: "client never draws them" (catalogue) | Drawn: `QuestBookWidget` rebuilds link copies every frame (~l.4112). Stale class comment says otherwise. |

## Tasks
| # | Feature | Status | Evidence (code) | What works | Missing | UI | Ver. |
|---|---|---|---|---|---|---|---|
| T1 | Item | Complete | `TaskTracker.pollItem/submit/countItems`, `ItemSet` | Count from main inventory; consume-on-click; `match_components` exact/default; chapter/book `consume_items` defaults; item filters; **`only_from_crafting` now counted from `ItemCraftedEvent` (`TaskTracker.onCraft`, added after this audit, not yet run in game)** | NBT tag modes beyond exact, hide valid items, Grab Copy | yes (picker, count box) | G (README, `phase7-comparison` 13-15) |
| T2 | Custom | Partial | `SUPPORTED_TASKS`, `QuestEvents` API only | Exists, imports, draws | Nothing in-game completes it except the API | yes | C |
| T3 | XP Levels | Unverified | `TaskTracker.submitExperience` | Click-to-pay levels or points | none known | yes (prompt, default 1) | C (creation G) |
| T4 | Visit Dimension | Unverified | `tick` case `dimension`, `Names.dimensionId` | Matches `player.dimension` | modern dimension ids beyond overworld/nether/end depend on `Names` | yes | C |
| T5 | Stat | Unverified | `tick` case `stat`, `statOf` (25 stats) | Reads the player's stat file | Stats outside the 25 never progress (reported by `/rquests problems`) | yes (list) | C; hover/stat "seen working" in `phase7` pass 3 |
| T6 | Kill Entity | Complete | `onKill` | Entity id match, count | Tag (no tags in 1.12.2) | yes | G (README) |
| T7 | Kill Entity name / NBT filter | Unverified | `onKill` (`custom_name`, `nbt_filter`) | Both implemented | not exercised | yes | C |
| T8 | Location | Unverified | `inLocation` | Box from position+size, `ignore_dimension` | no `Dimension` row validation | yes (six rows) | C |
| T9 | Checkmark | Complete | `check` | | | yes | G (README) |
| T10 | Advancement | Unverified | `onAdvancement` | Whole advancement; **after this audit: also polled in `tick`, so an advancement earned earlier counts, and a named `criterion` is checked alone (not yet run in game)** | | yes | C |
| T11 | Observation | Partial | `tick` case `observation` | Block seen once within 6 blocks | `timer`, entity type, NBT fuzzy match, tags | yes | C |
| T12 | Visit Biome | Unverified | `tick` case `biome` | | | yes | C |
| T13 | Find Structure | Partial | `structureName`, `isInsideStructure` | 8 vanilla 1.12.2 structures | newer structures cannot exist (intentional difference) | yes | C |
| T14 | Stage | Partial | `tick` case `gamestage` (player tag) | Player tag instead of game stage | `team_stage` ignored; 1.12.2 has no game stages (intentional) | yes | G (`phase7` Stage task completed) |
| T15 | Fluid | Unverified | `submitFluid` | Click empties containers into the task | | yes (picker + amount) | C; first (auto-count) version G |
| T16 | Forge Energy | Unverified | `submitEnergy` | Click drains `IEnergyStorage` | file keys not captured from FTB | yes | C |
| T17 | Interaction | Partial | `onInteract`, `ReflibQuestsMod.onRightClickBlock` | Block right click completes (G); entity path exists | entity not seen; tags/state match nothing | yes | G (block) |
| T18 | Item filter by tag | Partial | `ItemSet` ore dictionary | ore/tool filters | 1.12.2 has no tags (intentional) | partly | U |
| T19 | Image (chapter image) | Partial | `QuestBookWidget.imageGeo`, `BookEditor` image props | Draw, move, delete, edit all properties | **no `create_image` op** (QuestEdits has move/delete only); art for `minecraft:backyard`-style names needs supplied assets | edit yes, create no | G (drawn) | **[final: image creation, menu, window, editing-mode-only and the Open a URI click seen in game; Run a Command, Post Custom Event, Show a Docs Page unavailable in 1.12.2 (intentional); Lock Position unit-tested only]**

## Rewards and reward tables
| # | Feature | Status | Evidence | What works | Missing | UI | Ver. |
|---|---|---|---|---|---|---|---|
| R1 | Item | Complete | `RewardGranter` case item | count, `random_bonus`; **`only_one` now honoured (skips the give while the player carries the item, NBT ignored; added after this audit, unit-untested because it needs a live player)** | | yes | G (README) |
| R2 | Choice | Unverified | `grantFromTable` default branch | needs a choice index | | yes | C |
| R3 | Random | Unverified | `grantFromTable` random | weighted, empty weight | | yes | C |
| R4 | Loot | Unverified | `grantFromTable` loot | `lootSize` rolls | | yes | C |
| R5 | All Table | Unverified | `grantFromTable` all_table | | | yes | C |
| R6 | Command | Unverified | `runCommand` | permission level, silent, feedback | | yes | C (window G) |
| R7 | Custom | Unverified | case custom | Granted, no effect | API reaction only | yes | C |
| R8 | XP | Unverified | case xp | | | yes (prompt default 100) | C |
| R9 | XP Levels | Unverified | case xp_levels | | | yes (default 5) | C |
| R10 | Advancement | Unverified | case advancement | all/one criterion | | yes | C |
| R11 | Toast | Partial | case toast | chat message `[Quests] ...` | no toast | yes | C |
| R12 | Stage | Unverified | `RewardGranter.grant` has no `gamestage` case (UNSUPPORTED); menu greyed | | stage as player tag (mirrors the Stage task) | no | C | **[final: Stage reward added (player tag); menu, window rows, icon seen in game; claiming not exercised]**
| R13 | Currency | Missing | none | | | no | - |
| R14 | Command: permission level, feedback | Unverified | `runCommand` | | | yes | C |
| R15 | All Table reward | Unverified | see R5 | | | yes | C |
| R16 | Stage reward (Game Stages) | Unverified | see R12 | | | no | - | **[final: same as R12]**
| R17 | Reward options | Partial | `ProgressEngine.canClaim` (ignore blocking), `QuestServer.claimAll` (exclude), `autoClaims` | auto, exclude-from-claim-all, ignore-blocking, random bonus, feedback; **`team_reward` now makes one claim for the whole team (`ProgressEngine.claim`, `TeamProgress.isClaimed`, test `teamRewardIsClaimedOnceForTheWholeTeam`)**; `only_one` see R1 | `team_reward: false` "all online players receive it" is not done (we grant to the claiming player only) | yes | C |
| R18 | Reward table editor | Unverified | `RewardTablesScreen`, `TableEdits`, table ops | create/rename/delete/weights/loot size | FTB's Accept/Cancel flow | yes | C (opens G) |
| R19 | Table reward titles; Choice/Random/Loot use a table | Unverified | `Ids.tableId`, `grantFromTable` | numeric `table_id` fixed | grant not run in game | yes | U (`IdsTableTest`) |
| R20 | Loot crates | Missing | `ReflibQuestsMod` registers nothing | | items, blocks, entity drops | no | - |
| R21 | Rarity presets | Missing | none | | | no | - |
| R22 | Nested crates | Missing | none | | | no | - |
| R23 | `/give` a crate | Missing | none | | | no | - |
| R24 | Export/import table from a chest | Missing | none | | | no | - |
| R25 | Delete empty tables command | Missing | `QuestCommand` has none | | | no | - |

## Quest settings
| # | Feature | Status | Evidence | Gap | UI | Ver. |
|---|---|---|---|---|---|---|
| Q1 | Title | Complete | `set_title`, lang round trip | | yes | G (`phase7`) |
| Q2 | Subtitle | Unverified | `set_subtitle` | | yes | C |
| Q3 | Description with formatting | Partial | `DescriptionEditorScreen`, `RichText` | hover/click events read; editor for them, colour picker, Text Shadow missing | yes | G hover text | **[final: H (hover), link and quest-link buttons; quest-link description draws like FTB (G); colour picker palette exists; Text Shadow missing]**
| Q4 | Icon | Partial | `set_icon` | item picker not offered for the quest icon | yes | C |
| Q5 | Tags (quest) | Unverified | `Definitions.Quest` has no tags; only `Chapter.tags` | | no | - | **[final: quest tags: model, import/export, Quick Properties row; value loaded from FTB's file seen in game; editing not exercised]**
| Q6 | Disable completion toast | Unverified | `ClientBootstrap` l.190 | | yes | C |
| Q7 | Icon scaling | Complete | grep `icon_scale`: none | | no | - | **[final: Icon Scaling: row, map drawing seen in game against FTB]**
| Q8 | Min opened quest window width | Partial | chapter value used (`QuestBookWidget:294`); quest value not read | | yes | C |
| Q9 | Shape | Complete | 10+ shapes, art from jar | | yes | G |
| Q10 | Size | Complete | `size`, resize drag | | yes | G |
| Q11 | Position X/Y | Complete | `move_quest` | | yes | G |
| Q12 | Hide details until startable | Unverified | `QuestRules.detailsVisible` | | yes | C |
| Q13 | Hide until deps visible | Unverified | `QuestRules.isVisible` | | yes | C |
| Q14 | Hide text until completed | Unverified | `BookEditor`, widget | | yes | C |
| Q15 | Invisible until completed | Unverified | `isVisible` | | yes | C |
| Q16 | Invisible until X tasks | Unverified | `isVisible` | | yes | C |
| Q17 | Dependency list | Unverified | `toggle_dep`, lines G | | yes | G (lines) |
| Q18 | Dependency requirement | Unverified | `DependencyMode`, `dependenciesMet` | | yes | U |
| Q19 | Hide dependency / dependent lines | Unverified | ops exist | | yes | C |
| Q20 | Min required dependencies | Unverified | `dependenciesMet` | | yes | U |
| Q21 | Disable JEI recipe | Unverified | widget l.3304 | | yes | C |
| Q22 | Ignore reward blocking | Unverified | `ProgressEngine.canClaim` | | yes | U |
| Q23 | Optional quest | Unverified | `optional`, chapter completion skips it | | yes | U |
| Q24 | Progression mode | Unverified | `canProgress`, `effectiveMode` | FLEXIBLE semantics are our reading | yes | U |
| Q25 | Repeatable (+cooldown) | Unverified | `repeatable`, `tickRepeats` | | yes | U |
| Q26 | Sequential task completion | Unverified | `requiredNextTask` | | yes | U |
| Q27 | Max completable dependents, exclusive branch, tasks ignore dependencies, guide page | Partial | grep: absent (lang keys exist: `max_completable_dependents`, `misc.exclusive`, `tasks_ignore_dependencies`, `misc.guide_page`) | | no | - | **[final: Max Completable Dependents (unit-tested) and Guide Page (stored; the 'read more' link is not drawn); Exclusive Quest Branch and Tasks Ignore Dependencies have no row in FTB's window either]**
| Q28 | Visual preset (quest/chapter/book) | Partial | none | | no | - | **[final: quest and chapter presets: rows, drawing and inheritance seen in game; the book's Visual Presets editor and Default Visual Preset are missing]**

## Chapters
| # | Feature | Status | Evidence | Gap | UI | Ver. |
|---|---|---|---|---|---|---|
| C1 | Create/order/move chapters and groups | Unverified | `QuestEdits` create/move/delete ops | | yes | U/C; panel structure G |
| C2 | Chapter title | Unverified | `rename_chapter` | | yes | C |
| C3 | Chapter icon | Partial | `set_icon`; picker missing | | yes | C |
| C4 | Chapter settings | Unverified | `ChapterPropertiesScreen` | | yes | C |
| C5 | Subtitle, default size, preset, min width | Partial | subtitle/size/min width exist; **preset missing** | | yes | C | **[final: chapter Edit window rows (default size, min width, preset) seen in game in FTB's order; subtitle list still text]**
| C6 | Autofocus quest | Unverified | `set_chapter_prop autofocus` | | yes | C |

## Styling
| # | Feature | Status | Evidence | Gap |
|---|---|---|---|---|
| S1 | Theme in resource pack | Missing | no loader (grep `theme`: none) | |
| S2 | Custom quest shapes | Partial | fixed list | |
| S3 | Styles by quest id/tag | Missing | none | |
| S4 | Line colour, thickness, animation | Missing | thickness fixed in widget; no style code | |
| S5 | Full-screen quest window | Missing | none | |

## Player
| # | Feature | Status | Evidence | Gap |
|---|---|---|---|---|
| P1 | Open the book | Partial | key `J`, `/rqbook` (`ClientBootstrap:42`) | no book item, no inventory button |
| P2 | Chapter sidebar open/pin | Complete | widget, G (`phase7` panel widths) | |
| P3 | Ctrl+F search | Complete | `QuestSearch`; README | |
| P4 | Key reference | Partial | `KeyReferenceScreen` | text not rewritten to FTB's |
| P5 | Pan/zoom | Complete | G (shared zoom) | |
| P6 | Quest window | Complete | G (`phase7`) | |
| P7 | Click task -> recipe | Unverified | `JeiBridge`, `ValidItemsScreen` (needs JEI) | |
| P8 | Pin, tracker | Complete | README (user) | |
| P9 | Auto-pin | Unverified | toggle in widget | |
| P10 | Claim all/one/choose | Unverified | `QuestServer.handleClaim`, `claimAll` | claim flow not re-run |
| P11 | Completion notice, alerts | Unverified | `QuestToast`, `Ui.alert` | action bar missing |
| P12 | Dependency / dependent dropdowns | Unverified | widget | |
| P13 | Linked quests with jump icon | Unverified | widget l.4112 | |

## Commands
| # | Feature | Status | Evidence | Gap |
|---|---|---|---|---|
| M1 | `/ftbquests` set | Partial | `/rquests`: reload export problems list info check submit claim claimall complete reset lock blockrewards; `/rqbook` | reset-all, complete-all, change_progress, editing_mode, change_team_rewards, delete_empty_reward_tables, import/export chest, generate chapter |
| M2 | generate_chapter_with_all_items | Missing | none | |
| M3 | clear_item_display_cache | Missing | none | |
| M4 | reset-all, complete-all, separate reloads, chapter generation | Missing | `complete`/`reset` take one quest | |

## Editor
| # | Feature | Status | Evidence | Gap |
|---|---|---|---|---|
| E1 | Undo/redo | Unverified | `History`, `HistoryTest`; one resize undo G | |
| E2 | Bezier dependency lines | Unverified | `set_bezier`, handles ~l.1278 | |
| E3 | Visual presets | Partial | none | | **[final: see Q28]**
| E4 | Chapter image options | Partial | lock, text, click type edited; only "Open a URI" action | 5 other click types need an FTB capture | **[final: 7 click types selectable, 3 execute, Lock Position enforced (unit test), dev flag seen in game]**
| E5 | Quest barriers | Missing | no blocks | |
| E6 | Hotkeys, drag-selection | Partial | selection + copy/paste exist; no force-complete/reset hotkeys | |

## Player options and book settings
| # | Feature | Status | Evidence | Gap |
|---|---|---|---|---|
| O1 | Pinned tracker modes + cycling key | Partial | `PreferencesScreen` three modes | only one keybinding (J) |
| O2 | Notifications toast/chat/action bar/none | Partial | no `setOverlayMessage` anywhere | action bar |
| O3 | Fallback locale | Unverified | `Translations` | |
| O4 | Suppress all autoclaiming | Unverified | `QuestServer.autoClaims` | no UI row | **[final: `suppress_all_autoclaiming`: server rule and an Edit File row both exist (the first audit said no row: wrong)]**
| O5 | Book settings stored but inert | Missing | `drop_book_on_death`, `emergency_items_cooldown`, `drop_loot_crates`, `loot_crate_no_drop`, `lock_message`, `hide_excluded_quests`, `verify_on_load` | |
| O6 | Quest rules: repeat cooldown, exclusive branch, hide quests in excluded questlines | Partial | cooldown works; others absent | |
| O7 | Blocks and items: loot crates, task screens | Missing | none registered | |

## Counts (final, rows with an id in the tables above)
| Status | Count |
|---|---|
| Complete | 14 |
| Partial | 26 |
| Missing | 17 |
| Broken | 0 |
| Unverified | 56 |
| Total | 113 |

## Update after the first FTB captures (2026-11-22)
Q7 Icon scaling, Q27 Max Completable Dependents and Guide Page, Q28 Visual preset (quest level): keys captured from FTB's saved file (`icon_scale`, `max_completable_dependents`, `guide_page`, `preset`) and implemented across model, import/export, editor, UI and the map; unit-tested, **not yet seen in ours** -> Unverified. Still Missing: chapter `default_preset`, book-level preset editing, Exclusive Quest Branch and Tasks Ignore Dependencies (FTB shows no row for them), quest Tags (key not captured yet), per-quest min window width.

# Final report (Phase 9, 2026-11-22)

**Basis.** Statuses above were re-read against the code at the end of the session (`git log`: `ea40321` ... `201f83a` on `claude/reflib-ftb-quests-parity-00a07b`). Build and all unit tests pass (`./gradlew build`). "Complete" is only used where the feature was seen working in game; most rows stay **Unverified** because this session verified in game only the features listed below.

## Features newly implemented
- Quest options with keys read from FTB 26.1.2's saved files: **Icon Scaling** (`icon_scale`), **Visual Preset** (`preset`, overrides the quest's shape and size, measured; quests inherit the chapter's), **Max Completable Dependents**, **Guide Page** (stored), **Tags**.
- Chapter keys `preset`, `default_quest_size`, `default_min_width` read and written; Default Visual Preset row.
- **Stage reward** (`gamestage`: `stage`, `remove`), granted as a player tag; menu entry, creation prompt, window rows, controller icon.
- **Per-Team Reward by Type** (`default_reward_team_by_type`) and the reward-level **Per-Team Reward** (one claim for the team).
- **Chapter images**: creation from the map menu (`create_image`, undo, change log), all seven click types selectable, Open a URI / Jump to a Quest / Show Recipes executed, Lock Position enforced.
- Reward table **Hide Tooltip** and **Use Title**; description **H** (hover) button; quest-link click events read and written in FTB's spelling.
- Server rules that were stored but inert: **Only One** (item reward), **Only Detect From Crafting**, advancement **criterion** and earlier advancements.
- Tooling: `tools/window-driver/drive.ps1` (capture and drive the FTB and our windows).

## Features corrected
Per-Team and Only One tooltips use FTB's own wording; Quick Edit gained Visual Preset and hides Resize while a preset is set; Quest Properties and Chapter Edit rows follow FTB's order; the old audits' wrong claims (see the first table of this file) were fixed.

## Original Reflib features preserved
Undo/redo in editors, Z Position, Add Dependency and Bezier entries in the quest menu, search/pin/tracker, legacy importer, Cleanroom-only 1.12.2 adaptations (stage as player tag, fluid and energy hand-in by click, ore-dictionary filters), our dirt-and-stone theme and chevron lines (user decision). Nothing was removed.

## Verified in game this session (ours; FTB measured with the window driver)
Preset overrides shape and size; chapter preset inheritance; Icon Scaling; Quest Properties, Chapter Edit and Stage reward windows and menus; Quick Edit list; image creation, menu, Edit window, editing-mode-only flag and the Open a URI click with FTB's confirmation; quest-link description rendering; the H button's output. Earlier sessions' in-game checks are cited per row (`G:`/`V:`).

## Remaining differences from FTB Quests 26.1.2 (see `docs/phase7-comparison.md`)
Guide-page "Click here to read more..." line; book Visual Presets editor, Default Visual Preset and Emergency Items list; Quest Properties lacks Min Window Width per quest, X, Y, Description rows; image picker is a list, status line does not count images; FTB's map menu is the task-type list ending in Image; Tags shown as text not `[ n items ]`; loot crates, task screens, barriers, theme/styles, full-screen quest window, book item, action-bar notifications, most extra commands, Currency, Exclusive Branch / Tasks Ignore Dependencies (no FTB row either), observation timer and entity observation.

## Known technical limitations (1.12.2)
No tags, game stages, structure/biome/dimension additions of newer versions; Run a Command / Custom Event / Docs Page image clicks need a server-permission design; loot crates and barriers need registered items and blocks with art; FTB's colour wheel not built; chapter-image art such as `minecraft:backyard` needs player-supplied assets.

## Not tested
Multiplayer (two clients); claiming any reward, including the Stage reward; Only One, Only From Crafting, advancement criterion, Per-Team claims (unit or code only); Jump to a Quest and Show Recipes image clicks; Lock Position; undo of image creation; the link and Q editor buttons; the Per-Team Reward by Type rows and the table Hide Tooltip / Use Title rows in the UI; FTB completion toast (not captured).

## Reference book
Built through FTB's own UI and saved in the FTB instance: the four earlier chapters plus `Rewards and Images` and the new `Quest Options` chapter (icon scale, presets, max completable dependents, guide page, tags, a Stage reward, chapter preset keys, a `backyard` image with Run a Command / lock / editing-only, a quest-link description), a loot-crate-enabled table and two per-team-by-type switches. It was loaded into ours several times (same keys, no importer problems seen). Still missing from it: groups beyond one, quest links, descriptions with colours, all task and reward types on one chapter, observation timers.
