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
| T19 | Image (chapter image) | Partial | `QuestBookWidget.imageGeo`, `BookEditor` image props | Draw, move, delete, edit all properties | **no `create_image` op** (QuestEdits has move/delete only); art for `minecraft:backyard`-style names needs supplied assets | edit yes, create no | G (drawn) |

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
| R12 | Stage | Missing | `RewardGranter.grant` has no `gamestage` case (UNSUPPORTED); menu greyed | | stage as player tag (mirrors the Stage task) | no | C |
| R13 | Currency | Missing | none | | | no | - |
| R14 | Command: permission level, feedback | Unverified | `runCommand` | | | yes | C |
| R15 | All Table reward | Unverified | see R5 | | | yes | C |
| R16 | Stage reward (Game Stages) | Missing | see R12 | | | no | - |
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
| Q3 | Description with formatting | Partial | `DescriptionEditorScreen`, `RichText` | hover/click events read; editor for them, colour picker, Text Shadow missing | yes | G hover text |
| Q4 | Icon | Partial | `set_icon` | item picker not offered for the quest icon | yes | C |
| Q5 | Tags (quest) | Missing | `Definitions.Quest` has no tags; only `Chapter.tags` | | no | - |
| Q6 | Disable completion toast | Unverified | `ClientBootstrap` l.190 | | yes | C |
| Q7 | Icon scaling | Missing | grep `icon_scale`: none | | no | - |
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
| Q27 | Max completable dependents, exclusive branch, tasks ignore dependencies, guide page | Missing | grep: absent (lang keys exist: `max_completable_dependents`, `misc.exclusive`, `tasks_ignore_dependencies`, `misc.guide_page`) | | no | - |
| Q28 | Visual preset (quest/chapter/book) | Missing | none | | no | - |

## Chapters
| # | Feature | Status | Evidence | Gap | UI | Ver. |
|---|---|---|---|---|---|---|
| C1 | Create/order/move chapters and groups | Unverified | `QuestEdits` create/move/delete ops | | yes | U/C; panel structure G |
| C2 | Chapter title | Unverified | `rename_chapter` | | yes | C |
| C3 | Chapter icon | Partial | `set_icon`; picker missing | | yes | C |
| C4 | Chapter settings | Unverified | `ChapterPropertiesScreen` | | yes | C |
| C5 | Subtitle, default size, preset, min width | Partial | subtitle/size/min width exist; **preset missing** | | yes | C |
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
| E3 | Visual presets | Missing | none | |
| E4 | Chapter image options | Partial | lock, text, click type edited; only "Open a URI" action | 5 other click types need an FTB capture |
| E5 | Quest barriers | Missing | no blocks | |
| E6 | Hotkeys, drag-selection | Partial | selection + copy/paste exist; no force-complete/reset hotkeys | |

## Player options and book settings
| # | Feature | Status | Evidence | Gap |
|---|---|---|---|---|
| O1 | Pinned tracker modes + cycling key | Partial | `PreferencesScreen` three modes | only one keybinding (J) |
| O2 | Notifications toast/chat/action bar/none | Partial | no `setOverlayMessage` anywhere | action bar |
| O3 | Fallback locale | Unverified | `Translations` | |
| O4 | Suppress all autoclaiming | Unverified | `QuestServer.autoClaims` | no UI row |
| O5 | Book settings stored but inert | Missing | `drop_book_on_death`, `emergency_items_cooldown`, `drop_loot_crates`, `loot_crate_no_drop`, `lock_message`, `hide_excluded_quests`, `verify_on_load` | |
| O6 | Quest rules: repeat cooldown, exclusive branch, hide quests in excluded questlines | Partial | cooldown works; others absent | |
| O7 | Blocks and items: loot crates, task screens | Missing | none registered | |

## Counts (rows with an id in the tables above)
| Status | Count |
|---|---|
| Complete | 13 |
| Partial | 23 |
| Missing | 24 |
| Broken | 0 |
| Unverified | 53 |
| Total | 113 |

The grouping differs from the spreadsheet's 111 rows (some spreadsheet rows are merged here, for example the 22 quest-setting rows Q12-Q26 are listed one by one but the three Reward-table crate rows are separate), so the two totals are not comparable row by row. Re-count after Phase 8/9.

## Update after the first FTB captures (2026-11-22)
Q7 Icon scaling, Q27 Max Completable Dependents and Guide Page, Q28 Visual preset (quest level): keys captured from FTB's saved file (`icon_scale`, `max_completable_dependents`, `guide_page`, `preset`) and implemented across model, import/export, editor, UI and the map; unit-tested, **not yet seen in ours** -> Unverified. Still Missing: chapter `default_preset`, book-level preset editing, Exclusive Quest Branch and Tasks Ignore Dependencies (FTB shows no row for them), quest Tags (key not captured yet), per-quest min window width.
