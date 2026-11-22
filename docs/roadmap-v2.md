# Roadmap v2 (Phase 2): what to build, in what order

Source: `docs/parity-audit-v2.md` (ids T/R/Q/C/S/P/M/E/O refer to its rows). Ordered by what the reference book needs and by dependency, not by spreadsheet order. Rule for every item: **no key, value or option name is invented**. Where FTB's lang file (inside the supplied jar, an allowed source) names a field, the key is the lang suffix (checked against keys FTB really saved: `feedback_message`, `random_bonus`, `team_stage`); where it does not, the item waits for an FTB capture (**C** below).

## Done in this session (compiled; unit-tested only where marked; **none seen in game yet**)
| Item | Change | Test |
|---|---|---|
| R17 `team_reward` | `ProgressEngine.claim` stores a team reward under the team id; `TeamProgress.isClaimed` also checks it | `teamRewardIsClaimedOnceForTheWholeTeam` (unit) |
| R1 `only_one` | `RewardGranter` skips the give while the player carries the item (NBT ignored, FTB text) | game only |
| T10 advancement `criterion` + earlier advancements | `TaskTracker.tick` polls the advancement; named criterion checked alone | game only |
| T1 `only_from_crafting` | `TaskTracker.onCraft` + `ReflibQuestsMod.onCrafted`; `pollItem` skips such tasks | game only |
| UI text | Per-Team Reward and Only One tooltips now FTB's own wording | - |

| P1.2/P1.3/P1.4 (part) | `icon_scale`, `preset`, `max_completable_dependents`, `guide_page` captured from FTB; model, import/export, editor op, Quest Properties rows, map drawing (icon scale; preset shape and size), dependents limit in `QuestRules` | `ExporterTest.questOptionsFtbSavedAreReadAndWrittenBack`, `ProgressTest.maxCompletableDependentsMakesTheOtherBranchesUnavailable` (units); in game: FTB side measured, ours not yet seen |

## P0 foundational
| # | What is missing | Why | Do | Depends on | Test |
|---|---|---|---|---|---|
| P0.1 | The new server rules above never run in game | "Unverified" must not be called Complete | launch ours, one quest per rule in the dev book | none | in-game, then a note in the audit |
| P0.2 | Reference book lacks most features (no descriptions, groups, links, crates, item/structure tasks, images made in FTB) | Phase 5 and every comparison need it | extend the book through FTB's UI (needs the FTB window driver) | **C** | file diff of saved keys, round trip through `UpstreamImporter`/`Exporter` |
| P0.3 | Importer keeps unknown keys (`raw`) but this is tested only on samples | no silent loss (assignment) | re-run the round-trip test on every new book file | P0.2 | `RealPackReportTest`-style test on the new book |

## P1 core parity
| # | Row | What | Needs | Test |
|---|---|---|---|---|
| P1.1 | T19 | `create_image` op and "Image" in the map menu (FTB has it) | FTB capture of the image window keys (**C**) | in game + export |
| P1.2 | Q27/Q28/E3/C5 | Visual presets (book `presets`, quest/chapter `visual_preset`, default preset) | lang names exist (`file.presets`, `file.defaults.default_preset`, `chapter.appearance.default_preset`); saved keys need **C** | import/export round trip, then rendering |
| P1.3 | Q27 | Max Completable Dependents, Exclusive Quest Branch, Tasks Ignore Dependencies, Guide Page | lang gives semantics ("at most this number of dependents can be completed, and uncompleted dependents become unavailable"); keys need **C** | unit tests in `ProgressTest`, then game |
| P1.4 | Q7 | Icon Scaling | lang: "Independent of the overall button size"; key **C** | game |
| P1.5 | Q8 | Per-quest min window width in the quest window | key already stored | game |
| P1.6 | T11 | Observation timer, entity observation, NBT fuzzy match | tooltip text known (`to_observe`); timer unit **C** | unit + game |
| P1.7 | R11 | Toast reward as a real toast | `QuestToast` + a packet | game |
| P1.8 | R12/R16/T14 | Stage reward (+ remove, silent, team) | key names **C**; semantics: player tag in 1.12.2 (intentional) | game |
| P1.9 | R17 | `team_reward: false` gives the reward to all online team members | `QuestServer.claim` loop | two clients |
| P1.10 | T2 | Custom task/reward completion by command or API | decide semantics | unit |

## P2 interface and presentation
Quest Edit rows missing (Icon Scaling, Min Opened Quest Window Width, Visual Preset, Max Completable Dependents, Guide Page, Hide Dependent Lines in Edit), group Quick Edit contents, colour picker (RGB wheel with palettes), description hover/click editor and Text Shadow, chapter-image click-action types (six; keys **C**), reward-table Accept/Cancel, Key Reference rewrite, Download Quest Files / Reload Theme in the gear menu, toast heights, map check icon, "Edit" arrow glyph. Each needs a capture first (hand-off s.8).

## P3 advanced and secondary
Loot crates (items, rarity presets, nesting, `/give`, entity drop weights: lang `reward_table.loot_crate.*` gives field names), Stage barriers / Task Screen blocks (1.12.2 needs registered blocks and textures), Emergency Items, theme file and styles, full-screen quest window, `/rquests` additions (`editing_mode`, `change_progress`, `change_team_rewards`, `change_consumable`, `delete_empty_reward_tables`, `clear_display_cache`, `import_rewards_from_chest`, `export_rewards_to_chest`, `reset-all`, `complete-all`; names from FTB's lang), action-bar notification, book item and inventory button, book settings that are stored but inert (`drop_book_on_death`, `lock_message`, `hide_excluded_quests`, `verify_on_load`).

## Dependency chain
`P0.1` (run what exists) and `P0.2` (build the book through FTB) first; `P0.2 -> P1.1, P1.2, P1.3, P1.4, P1.8` (all need captured keys); `P1.5, P1.6, P1.7, P1.9, P1.10` need no capture and can be done now; P3 crates need P1.2-style capture of the loot-crate window.

## What blocks the in-game phases (3-7) from this session
Phases 3 to 7 need the FTB 26.1.2 window driven live (keys unbound: the user opens the book) and our game relaunched. At the start of this session `javaw`/FTB was not running and no window driver script exists any more (the scratchpad copies are gone), so the driver has to be rebuilt from the hand-off s.3 description before the book can be extended. That has not been done yet.
