# Phase 9: final audit of Reflib Quests against FTB Quests 26.1.2 (2026-11-22)

Method: the per-feature table below replaces the old estimate in `docs/parity-audit-2026-11.md` ("no exact totals yet"). Rows follow that file and `docs/feature-catalogue.md`, updated with everything done in Phases 6-8. Statuses use the assignment's words:

- **Complete**: works end to end **and was seen working in game** (this session's checks, or earlier checks cited as `V:` in `docs/parity-audit-2026-11.md` and the README). Nothing is Complete from reading code or from a unit test alone.
- **Partial**: works, but part of what FTB 26.1.2 does is missing, or the 1.12.2 version intentionally differs (the difference is named).
- **Missing**: no implementation.
- **Broken**: implemented and seen failing. **None found.**
- **Unverified**: the code path exists end to end, but I did not see it work in game (it may have a unit test).

Evidence this session: `./gradlew build` passes (unit tests included); ours and FTB 26.1.2 were run side by side with the same showcase book and compared (`docs/phase7-comparison.md`, items 1-16); the FTB task/reward windows, menus and saved file keys were captured and ours checked against them. Not done: multiplayer (two clients) was not re-run this session; no reward was claimed in ours this session; no task other than Stage and Interaction was completed in ours this session.

## Per-feature table

Legend for the evidence column: `G` = seen in game this session, `V` = seen earlier (README/hand-off), `U` = unit test only, `C` = code only.

### Tasks
| # | Feature | Status | Evidence / gap |
|---|---|---|---|
| 1 | Item task (collect / consume) | Complete | V README; G window with Item row, picker and Count box (count 4 made a "4" badge) |
| 2 | Custom task | Partial | G menu/window/colour-wheel icon; nothing completes it except the `QuestEvents` API |
| 3 | XP Levels task | Unverified | G creation prompt (default 1), title; completion C |
| 4 | Visit Dimension | Unverified | G window; detection C |
| 5 | Stat task | Unverified | G creation, Stat Name list, window; progress C (1.12.2 stat ids mapped for 25 stats; others never progress) |
| 6 | Kill Entity | Complete | V README; G window rows (Entity Name, Entity Type list, NBT Filter) |
| 7 | Kill Entity: name and NBT filters | Unverified | C + G window; matching not exercised |
| 8 | Kill Entity: entity type tag | Partial | stored, never matches: 1.12.2 has no entity type tags (intentional difference) |
| 9 | Location task | Unverified | G window with the six number rows; detection C |
| 10 | Checkmark | Complete | V README |
| 11 | Advancement task | Unverified | G window; `criterion` stored but the task completes on the whole advancement |
| 12 | Observation task | Partial | looks at a block once; `timer` and entity observation ignored |
| 13 | Visit Biome | Unverified | C |
| 14 | Find Structure | Partial | only the vanilla 1.12.2 structures exist (intentional difference) |
| 15 | Stage task | Complete | G: task completed after `/scoreboard players tag @p add myStage`. Difference: 1.12.2 has no game stages, a player tag is used; Team Stage kept in the file, ignored |
| 16 | Fluid task (click to submit, empties containers) | Unverified | user chose the 1.12.2 behaviour; G creation and window; hand-in C |
| 17 | Interaction task | Partial | G: block interaction completed. Entity type not exercised; tag types match nothing; block state read as its block |
| 18 | Forge Energy task | Unverified | G creation prompt, tooltip; hand-in C (no energy item in the dev game) |
| 19 | Item filter by tag / ore dictionary | Partial | 1.12.2 has ore dictionary instead of tags (intentional difference) |
| 20 | Task options: Optional Task, Disable Completion Toast, Title, Tags | Unverified | G rows in every window; effect of each C/U |
| 21 | Task type list in FTB's order and wording | Complete | G, compared with FTB's list |
| 22 | Task edit windows in FTB's row order and titles | Complete | G for Kill, Location, Stat, Fluid, Item, Stage, Interaction compared with FTB |
| 23 | Task Screen block, barrier blocks | Missing | no blocks or items registered |

### Rewards and reward tables
| # | Feature | Status | Evidence / gap |
|---|---|---|---|
| 24 | Item reward | Complete | V README; picker used for creation |
| 25 | Choice reward | Unverified | G window (table row); claim C |
| 26 | Random reward | Unverified | C |
| 27 | Loot reward | Unverified | C |
| 28 | All Table reward | Unverified | C (README: not exercised) |
| 29 | Command reward (level, silent, feedback) | Unverified | G window; run C |
| 30 | Custom reward | Unverified | C |
| 31 | XP reward | Unverified | G prompt (default 100); grant C |
| 32 | XP Levels reward | Unverified | G prompt (default 5); grant C |
| 33 | Advancement reward | Unverified | C |
| 34 | Toast reward | Partial | shows a chat message, not a toast |
| 35 | Stage reward | Missing | |
| 36 | Currency reward | Missing | |
| 37 | Reward options (Auto-claim, Per-Team, Exclude from Claim All, Random Bonus, Only One, Feedback Toast) | Unverified | G rows and ordering; effect of each C |
| 38 | Reward edit windows in FTB's order and titles | Complete | G, compared with FTB (command, table reward, item) |
| 39 | Table rewards find their table (numeric `table_id`) | Unverified | fixed (`Ids.tableId`, unit test); grant not run in game |
| 40 | Reward table editor | Unverified | G opened ("Edit Reward Table"); editing C |
| 41 | Loot crates, rarity presets, nested crates, `/give` crates | Missing | no items registered |
| 42 | Export/import table from chest, delete-empty-tables command | Missing | |

### Quest settings and editor
| # | Feature | Status | Evidence / gap |
|---|---|---|---|
| 43 | Title, subtitle, description (rich text, hover and click events) | Partial | G hover text; editor for hover/click events missing |
| 44 | Quest icon (None / Held Item / Clear; "Use Task Icon as Quest Icon") | Partial | G; item picker not offered for the quest icon |
| 45 | Shape, size, position | Complete | G side by side (shapes, resize by drag, unselect after move) |
| 46 | Visibility options (hide details/text, invisible, until N tasks, hide lines) | Unverified | G eye icon; rules C/U |
| 47 | Dependencies, requirement, min required | Unverified | G lines (chevron/thin); rules U |
| 48 | Progression, repeatable, sequential, optional | Unverified | U |
| 49 | Icon scaling, visual presets, max completable dependents, guide page, exclusive branch | Missing | |
| 50 | Min window width | Partial | stored, not used by the quest window |
| 51 | Quest window layout (tasks/rewards rows, balanced icon rows, Edit menu) | Complete | G, compared with FTB |
| 52 | Quest right-click menu | Partial | FTB entries present plus extras (Add Dependency, Bezier) kept; user to decide |
| 53 | Bezier dependency control points | Unverified | C |
| 54 | Selection, copy/paste, quest links | Unverified | C |
| 55 | Undo / redo | Unverified | U (`HistoryTest`); G undo of a resize |
| 56 | Colour picker, Text Shadow, description JSON components editor | Missing | colour wheel for Custom icon only |

### Chapters and map
| # | Feature | Status | Evidence / gap |
|---|---|---|---|
| 57 | Chapter panel (groups, width from widest name, badges, completed mark) | Complete | G, measured against FTB |
| 58 | Chapter create / move / group / rename / delete | Unverified | U/C |
| 59 | Chapter settings, subtitle list, default size | Unverified | C |
| 60 | Chapter icon | Partial | edit via held item; picker missing |
| 61 | Shared zoom, view recentred, Esc order | Complete | G |
| 62 | Chapter images: draw, move, delete, edit | Partial | G drawn; `minecraft:backyard` painting does not exist in 1.12.2 (needs player-supplied assets); creating an image in a chapter missing |
| 63 | Chapter image click actions | Partial | only "Open a URI"; FTB has six types |
| 64 | Autofocus quest | Unverified | C |

### Player, commands, styling, settings
| # | Feature | Status | Evidence / gap |
|---|---|---|---|
| 65 | Open book (key J, `/rqbook`) | Complete | G |
| 66 | Book item, inventory button | Missing | |
| 67 | Search, pin, tracker | Complete | V README |
| 68 | Claim, claim all, completion toast, alerts | Unverified | V README for claim; not re-run this session |
| 69 | Key reference screen | Partial | not rewritten to FTB's text |
| 70 | Info/Wiki button (opens docs after confirmation) | Complete | G |
| 71 | Gear menu: Download Quest Files, Reload Theme | Missing | |
| 72 | Player preferences, notifications (toast/chat/none) | Partial | no action bar |
| 73 | Pinned tracker mode key | Partial | |
| 74 | Team progress, multiplayer sync | Complete | V README (two real clients; not re-run this session) |
| 75 | `/rquests` command set | Partial | no reset-all, complete-all, change_progress, editing_mode, chapter generation |
| 76 | Theme in resource pack, styles by id/tag, line colour/animation, full-screen quest window | Missing | |
| 77 | Book settings stored but inert (drop book on death, emergency items, loot crates, lock message, hide excluded quests, verify on load) | Missing | stored and exported only |
| 78 | Book settings that work (detection delay, pause game, autoclaim default, progression mode, fallback locale, disable GUI) | Unverified | C |
| 79 | Suppress-all-autoclaim switch | Missing | |
| 80 | Reference book import (all 5 chapters, 40 quests, 43 tasks, 10 rewards) | Complete | `docs/phase6-import-report.md` (0 importer problems, round trip identical) and the same book loaded and shown in game |
| 81 | Upstream files: unknown keys kept (`raw`) and re-exported | Complete | U round trip; G book loads |
| 82 | Reward table, image and group keys of 26.1.2 files | Complete | U + G |
| 83 | Item and fluid pickers (search, count box) | Complete | G |
| 84 | Cosmetics left as ours on purpose (panel texture, map background) | Partial | user decision: keep our theme |

## Counts (84 features)

Computed from the Status column of this table.

| Status | Count |
|---|---|
| Complete | 20 |
| Partial | 19 |
| Missing | 12 |
| Broken | 0 |
| Unverified | 33 |
| Total | 84 |

(The previous catalogue counted 111 rows in different groupings: 62 Implemented / 18 Partial / 31 Not implemented. The two cannot be compared row by row.)

## What is not done, and what to do about it
1. **Unverified (33)**: most are reward grants, detection of the simpler tasks (dimension, biome, location, XP, advancement, stat, fluid, energy), claim flows and the editor operations. Closing them needs a play-through of each in game, ideally with two clients; no code change is expected.
2. **Missing (12)**: loot crates and blocks, theme and styles, visual presets, icon scaling and the other quest options, book item, Stage/Currency rewards, extra commands, Download Quest Files. Each needs an FTB capture first (rule: do not guess keys).
3. **Intentional differences** (inside Partial): no tags in 1.12.2 (kill entity type tag, item tags), Stage as a player tag, fluid hand-in by click, structures only vanilla, chapter-image art needs player-supplied assets.
4. **Repository state**: all work since commit `6c8058d` (Phases 6-8 and this audit) is **uncommitted** in the worktree; nothing was committed or pushed.
