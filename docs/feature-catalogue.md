# Reflib Quests: feature catalogue

Source: `reflibquests/Reflib Quests vs FTB Quests comparación de funciones.xlsx`, a comparison of our Java source against the public FTB Quests docs, changelog and wiki. Status was read from the source only; **"Implemented" means a code path exists, not that it was tested in game.** Companion to `docs/parity-audit.md`, which covers wording, controls and layout differences.

| Status | Count |
|---|---|
| Implemented | 62 |
| Partial | 18 |
| Not implemented | 31 |
| Total | 111 |

## Changes since the comparison was written

The sheet was made before these landed; treat the rows as updated:

- Task menu now offers Item, Checkmark, XP Levels, Kill, Visit Dimension, Visit Biome and Find Structure; Edit Task has an Entity / Dimension / Biome / Structure / Advancement field.
- Reward menu now offers Command, Advancement and Toast with their edit fields (Run with Permission Level, Silent, Description, Criterion).
- Chapter menu has Reset Progress and Complete Instantly for the whole chapter.
- Observation tasks from legacy books keep `match_type`, `match` and `timer` (mapped to our keys on import).

## Tasks

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Item (collect an item) | Implemented | TaskTracker.pollItem counts the main inventory; submit() hands in items for consuming tasks | Task types |
| Custom | Partial | Listed in QuestServer.SUPPORTED_TASKS, but no code advances it and nothing lets other mods set its progress | Task types |
| XP Levels | Implemented | TaskTracker.tick, case "xp" (levels, or points if 'points' is set) | Task types |
| Visit Dimension | Implemented | TaskTracker.tick, case "dimension" via Names.dimensionId | Task types |
| Stat | Not implemented | Only Definitions.Task.maxProgress mentions "stat"; not in SUPPORTED_TASKS, never progresses | Task types |
| Kill Entity | Implemented | TaskTracker.onKill matches the entity id through the id map | Task types |
| Location | Implemented | TaskTracker.inLocation: box from position and size, ignore_dimension | Task types |
| Checkmark | Implemented | TaskTracker.check | Task types |
| Advancement | Implemented | TaskTracker.onAdvancement | Task types |
| Observation | Partial | TaskTracker.tick, case "observation": looks at a block within 6 blocks; no timer, no entities | Task types |
| Visit Biome | Implemented | TaskTracker.tick, case "biome" | Task types |
| Find Structure | Partial | TaskTracker.tick, case "structure": only vanilla 1.12.2 structures (structureName) | Task types |
| Stage | Not implemented | No game-stage code anywhere in the source | Task types |
| Fluid | Not implemented | Only LegacyImporter converts old fluid tasks; no tracker | Task types |
| Forge Energy | Not implemented | "energy" appears only in Task.maxProgress; no tracker | Task types |
| Image (decorative chapter image) | Partial | Chapter images are drawn, moved and deleted (QuestBookWidget.imageGeo, move_image, delete_image); no op creates one in game | Task types |
| Item filter by tag | Partial | ItemSet smart filters: ore(), tag() mapped to ore dictionary names, tool tags | Creating quests |
| Interaction task (right-click a block or entity) | Not implemented | No "interaction" anywhere in the source | Changelog 26.1.2.9 |
| Kill task with entity name and entity tag requirements | Not implemented | TaskTracker.onKill compares the entity id only | Changelog 2101.1.6, 2101.1.11 |

## Rewards

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Item | Implemented | RewardGranter case "item" (count, random_bonus) | Rewards |
| Choice | Implemented | RewardGranter.grantFromTable, default branch | Rewards |
| All Table | Implemented | RewardGranter.grantFromTable, "all_table" | Rewards |
| Random | Implemented | RewardGranter "random": weighted pick with empty weight | Rewards |
| Loot | Implemented | RewardGranter "loot": one roll per table lootSize, a roll may give nothing | Rewards |
| Command | Implemented | RewardGranter.runCommand: permission_level 0-4, silent, feedback_message | Rewards |
| Custom | Implemented | RewardGranter case "custom": no effect, other mods react to the claim | Rewards |
| XP | Implemented | RewardGranter case "xp" | Rewards |
| XP Levels | Implemented | RewardGranter case "xp_levels" | Rewards |
| Advancement | Implemented | RewardGranter: grants all remaining criteria or one named criterion | Rewards |
| Toast | Partial | RewardGranter case "toast" sends a chat message, not a toast | Rewards |
| Stage | Not implemented | No game-stage code anywhere in the source | Rewards |
| All Table reward | Implemented | RewardGranter.grantFromTable, "all_table" | Changelog 2101.1.2 |
| Currency reward | Not implemented | No currency code | Changelog 2101.1.15 |
| Command reward: permission level and feedback message | Implemented | RewardGranter.runCommand (permission_level, feedback_message, silent) | Changelog 2101.1.6, 2101.1.1 |
| Stage reward (Game Stages) | Not implemented | No game-stage code | Wiki (9minecraft) |

## Reward tables

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Edit Reward Tables screen | Implemented | RewardTablesScreen: create, rename, delete, entry weights, empty weight, loot size | Reward tables |
| Loot crates (table turned into an item) | Not implemented | ReflibQuestsMod registers no items or blocks | Reward tables |
| Rarity presets (common to legendary) | Not implemented | No preset code | Reward tables |
| Nested crates | Not implemented | No crate code | Reward tables |
| Give a crate with /give | Not implemented | No crate item exists | Reward tables |
| Export and import a table from a chest | Not implemented | Chest appears only as an icon (RewardsScreen, RewardTablesScreen) | Commands |
| Delete empty tables (command) | Not implemented | No such subcommand in QuestCommand | Commands |
| Reward titles in tables; Choice, Random and Loot can use a table | Implemented | Reward 'Title' property (TaskRewardScreen); RewardGranter.grantFromTable | Changelog 2101.1.2, 2111.1.3 |

## Quest settings

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Title | Implemented | set_title; QuestPropertiesScreen | Quest settings |
| Subtitle | Implemented | set_subtitle; QuestPropertiesScreen | Quest settings |
| Description with formatting | Implemented | DescriptionEditorScreen, RichText | Quest settings |
| Icon | Partial | Definitions.Quest.icon is read from files; no edit op or property screen entry | Quest settings |
| Tags | Not implemented | No tags field in Definitions.Quest | Quest settings |
| Disable completion toast | Implemented | BookEditor case "disable_toast" | Quest settings |
| Icon scaling | Not implemented | No such property | Quest settings |
| Min opened quest window width | Not implemented | No such property | Quest settings |
| Shape | Implemented | 10 shapes in QuestPropertiesScreen; shape textures come only from a supplied FTB jar (QuestArt), plain squares otherwise | Quest settings |
| Size | Implemented | BookEditor case "size" | Quest settings |
| Position X / Y | Implemented | QuestEdits case "move_quest" | Quest settings |
| Hide details until startable | Implemented | QuestRules.detailsVisible | Quest settings |
| Hide until dependencies are visible | Implemented | QuestRules.isVisible | Quest settings |
| Hide text until quest is completed | Implemented | BookEditor case "hide_text_until_complete" | Quest settings |
| Invisible until completed | Implemented | QuestRules.isVisible (q.invisible) | Quest settings |
| Invisible until X tasks are completed | Implemented | QuestRules.isVisible (invisibleUntilTasks) | Quest settings |
| Dependency list | Implemented | QuestEdits case "toggle_dep" | Quest settings |
| Dependency requirement (all/one, completed/started) | Implemented | Definitions.DependencyMode; QuestRules.dependenciesMet | Quest settings |
| Hide dependency lines / dependent lines | Implemented | BookEditor cases "hide_dep_lines", "hide_dependent_lines" | Quest settings |
| Min required dependencies | Implemented | QuestRules.dependenciesMet (minRequiredDependencies) | Quest settings |
| Disable JEI recipe | Implemented | BookEditor case "disable_jei"; checked in QuestBookWidget | Quest settings |
| Ignore reward blocking | Implemented | BookEditor case "ignore_reward_blocking" | Quest settings |
| Optional quest | Implemented | BookEditor case "optional" | Quest settings |
| Progression mode (linear / flexible) | Implemented | QuestRules.canProgress, Definitions.ProgressionMode (flexible semantics noted in code as to be confirmed) | Quest settings |
| Repeatable quest | Implemented | QuestRules.repeatable | Quest settings |
| Sequential task completion | Implemented | QuestRules.requiredNextTask | Quest settings |

## Chapters

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Create, order and move chapters and chapter groups | Implemented | QuestEdits: create_chapter, move_chapter, create_group, move_group, set_chapter_group, delete_* | Chapters |
| Chapter title | Implemented | QuestEdits case "rename_chapter" | Chapters |
| Chapter icon | Partial | Definitions.Chapter.icon read from files; no edit op | Chapters |
| Chapter settings (default shape, visibility, progression, repeatable, sequential, hide lines, always invisible) | Implemented | ChapterPropertiesScreen | Chapters |
| Chapter subtitle, default size, preset, min width | Partial | Chapter.subtitle exists in the model; no editor for it; the others do not exist | Chapters |
| Autofocus quest | Implemented | Quest menu 'Set as Autofocused Quest' (set_chapter_prop autofocus) | Chapters |

## Styling

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Theme file in a resource pack | Not implemented | No theme loader in the source | Styling |
| Custom quest shapes | Partial | Fixed list of 10 shapes (BookEditor.SHAPES); textures only from a supplied FTB jar | Styling |
| Styles by quest ID or tag | Not implemented | No style code | Styling |
| Dependency line colour, thickness, animation | Not implemented | No style code | Styling |
| Quest windows open full screen | Not implemented | No such option | Styling |

## Player

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Open the book: key, book item, inventory icon | Partial | KeyBinding J and /rqbook (ClientBootstrap); no item or inventory button registered | Opening the book |
| Chapter sidebar: auto-open at left edge, pin open | Implemented | QuestBookWidget; PreferencesScreen 'Chapter Panel Pinned Open' | Navigating |
| Search with Ctrl+F | Implemented | QuestSearch; QuestBookWidget | Navigating |
| Key reference panel | Implemented | KeyReferenceScreen | Navigating |
| Drag to pan, wheel to zoom | Implemented | QuestBookWidget; KeyReferenceScreen lists it | Navigating |
| Quest window with description, tasks and rewards | Implemented | QuestBookWidget detail panel | Navigating |
| Click a task to see its recipe or valid items | Implemented | JeiBridge, ValidItemsScreen (recipe view needs JEI) | Navigating |
| Pin quests and on-screen tracker | Implemented | PinStore, PreferencesScreen tracker options | Navigating |
| Auto-pin all quests | Implemented | Auto-pin' toggle in QuestBookWidget | Tips and tricks |
| Claim all, claim one, choose a reward | Implemented | QuestServer claim handling; /rquests claim, claimall | Navigating |
| Completion notice and exclamation marks | Implemented | QuestToast; Ui.alert on quests, chapters and Claim All | Navigating |
| Dependency and dependent dropdowns | Implemented | QuestBookWidget arrow_left / arrow_right | Tips and tricks |
| Linked quests with jump icon | Partial | QuestLink exists in the model, importer and exporter; the client never draws links | Tips and tricks |

## Commands

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| /ftbquests command set | Partial | /rquests: list, info, check, submit, claim, claimall, complete, reset, reload, lock, blockrewards, problems, export; /rqbook. No reset-all / complete-all, no editing_mode command | Commands |
| generate_chapter_with_all_items_in_game | Not implemented | No such subcommand | Commands |
| clear_item_display_cache | Not implemented | No such subcommand | Commands |
| reset-all and complete-all, separate reloads, chapter generation | Not implemented | /rquests complete and reset take one quest; one reload only | Changelog 2101.1.6, 2101.1.20 |

## Editor

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Undo and redo | Implemented | game/History (MAX = 300 steps); Ctrl+Z / Ctrl+Y; toolbar buttons | Changelog 26.1.2.3 |
| Curved (Bezier) dependency lines | Not implemented | No Bezier code | Changelog 26.1.2.3 |
| Visual presets | Not implemented | No preset code | Changelog 26.1.2.3 |
| Chapter images (position lock, text on image, click actions) | Partial | Images drawn with width, height, rotation, alpha, order; move and delete in editor; no lock, text or click action found | Changelog 26.1.2.3 |
| Quest barriers | Not implemented | No blocks registered | Changelog 2101.1.14, 2101.1.15 |
| Hotkeys for force-complete, reset, save; Alt-drag selection | Partial | Complete and Reset in the context menu, 'Save on Server now' button; no such hotkeys; no drag selection; keys found: Ctrl+F, Ctrl+P, Ctrl+Z, Ctrl+Y | Changelog 26.1.2.8, 26.1.2.3 |

## Player options

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Pinned tracker: visibility modes and a key to cycle them | Partial | PreferencesScreen has three visibility modes and an exclude-flexible option; the only keybinding is Open (J) | Changelog 26.1.2.7 |
| Notifications: toast, chat, action bar or none | Partial | PreferencesScreen offers Toast, Chat, None; no action bar | Changelog 2101.1.13 |
| Fallback locale per book, with client override | Implemented | PreferencesScreen 'Fallback Locale', 'Locale Override'; QuestBook; Translations | Changelog 2101.1.13 |

## Book settings

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Switch to suppress all reward autoclaiming | Not implemented | QuestServer.autoClaims reads default_autoclaim_rewards and per-reward mode only | Changelog 26.1.2.3 |

## Quest rules

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Repeat cooldown, exclusive branching, hide quests in excluded questlines | Partial | Repeat cooldown is honoured (ProgressEngine) but has no editor; the other two are not in the source | Changelog 2101.1.7, 2101.1.18, 2101.1.10 |

## Visibility

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Visibility modes and quest shapes | Implemented | QuestRules.isVisible; 10 shapes (textures need a supplied FTB jar) | Wiki (9minecraft) |

## Blocks and items

| Feature | Status | Evidence in code | Source |
|---|---|---|---|
| Loot crates | Not implemented | ReflibQuestsMod registers no items | Wiki (9minecraft) |
| Task Screen blocks and configurator | Not implemented | ReflibQuestsMod registers no blocks | Wiki (9minecraft) |

## Not implemented, grouped

- **Tasks:** Stat; Stage; Fluid; Forge Energy; Interaction task (right-click a block or entity); Kill task with entity name and entity tag requirements
- **Rewards:** Stage; Currency reward; Stage reward (Game Stages)
- **Reward tables:** Loot crates (table turned into an item); Rarity presets (common to legendary); Nested crates; Give a crate with /give; Export and import a table from a chest; Delete empty tables (command)
- **Quest settings:** Tags; Icon scaling; Min opened quest window width
- **Styling:** Theme file in a resource pack; Styles by quest ID or tag; Dependency line colour, thickness, animation; Quest windows open full screen
- **Commands:** generate_chapter_with_all_items_in_game; clear_item_display_cache; reset-all and complete-all, separate reloads, chapter generation
- **Editor:** Curved (Bezier) dependency lines; Visual presets; Quest barriers
- **Book settings:** Switch to suppress all reward autoclaiming
- **Blocks and items:** Loot crates; Task Screen blocks and configurator

## Note

The header comment of `QuestBookWidget` still says chapter images are not shown, but the code draws them; the comment is out of date.
