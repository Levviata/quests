# AI hand-off: Reflib Quests (1:1 FTB Quests parity)

Rewritten 2026-11-22 (replaces the 2026-11-21 version). Read this, then `docs/progress.md` (phase table), `docs/phase9-final-audit.md` (what is done), `docs/phase7-comparison.md` (every difference found and fixed), `docs/upstream-quest-format.md`.

## 1. What this is

`reflibquests` is a Cleanroom 1.12.2 quest mod on the library `reflib`, own code only, LGPL-2.1. The user wants it to look and behave **exactly like FTB Quests 26.1.2**, keeping our own visual theme (panel texture, map background) and the 1.12.2 behaviour where the user chose it (fluid tasks are handed in by clicking). Exactness is the point: measure, never guess.

- Worktree: `G:\TRABAJO\referencing lib\.claude\worktrees\archaiclib-architecture-investigation-ea090b`, branch `claude/archaiclib-architecture-investigation-ea090b`. Remote `origin` = `Levviata/referencing-lib`.
- Merged to `main` through PRs #1 to #5. PR #5 (merge commit `dc7706c`) holds the whole phase 6-9 work. The only change after it is this file.
- Build and tests: `./gradlew.bat build --console=plain` from the worktree root. Launch the dev game: `./gradlew.bat :reflibquests:runClient --console=plain` (run it in the background). Dev instance: `reflibquests\run\client\`; the dev book is read from `config\reflibquests` in FTB's file format.
- Local `master` is still the initial commit; the real trunk is `origin/main`.

## 2. State of the assignment (9 phases, user message of 2026-11-21)

All nine phases are done at the level documented. Final numbers (`docs/phase9-final-audit.md`, 84 features): **20 Complete, 19 Partial, 12 Missing, 0 Broken, 33 Unverified**. "Complete" means seen working in game; nothing is Complete from code or unit tests alone.

What the next session should do, in order of value:
1. **Close Unverified features by playing them in game** (33 rows): reward grants (choice, random, loot, all table, command, XP, advancement), detection of dimension, biome, location, XP, advancement, stat, fluid and energy tasks, claim and claim-all, chapter/quest editor operations, undo/redo, quest links, selection and paste, Bezier points. Ideally with two clients for multiplayer. Forge Energy needs an item with Forge Energy (none in the dev game).
2. **Missing features** (12 rows): loot crates and blocks, theme/styles, visual presets, icon scaling and other quest options, book item, Stage and Currency rewards, extra commands, Download Quest Files. Each needs an FTB capture of the window and the keys FTB saves first. Do not guess keys.
3. Open small differences: Forge Energy saves as `type: "forge_energy"`, `value` (a guess: FTB's file key was not captured); Fluid is two rows where FTB has one; quest right-click menu has extras (Add Dependency, Bezier) that the user has not decided on; "Edit Reward Table" opens the table list rather than a direct window; timer and criterion are stored but unused; chapter image `minecraft:backyard` cannot render (no such 1.12.2 painting).

## 3. Rules the user set (follow them)

- **Never close or kill the user's game unasked.** When the user says "restart" they are approving a restart of **ours**; stop the background `runClient` task with TaskStop, confirm no window titled `Cleanroom` remains, start it again, then ask the user to open the world (they do that themselves).
- **No guessing.** Values come from screenshots, captures or files FTB itself wrote. Measure, state exact values, ask when unsure. Follow the literal request.
- **Locate targets before clicking:** take a fresh screenshot, read the position, click, verify. Never reuse old coordinates (the user corrected this).
- **Modify the FTB reference book freely** to learn what FTB does, even destructively; save, and read the keys from the saved file. Mention what was changed.
- **Allowed references:** assets and lang in the supplied jars (`reflibquests\run\client\reflib-supplied\...`), docs.feed-the-beast.com, the user's screenshots, files FTB saved in the 26.1.2 instance. **No decompiling, no reading FTB source.**
- Work in passes (decide, capture both sides, compare into one list, fix the list, build once, relaunch). Be fast; keep replies short.
- Use the best existing widget (`TextEditor`, `Scroller`, `SettingsScreen`, `ItemPickerScreen`, `ListPickerScreen`, `NamePrompt`) before building one.
- Nothing is marked Complete without an in-game check. Tell the user plainly what is verified and what is not.
- Commit and push only when asked. Never stage the user's spreadsheet `reflibquests\Reflib Quests vs FTB Quests comparación de funciones.xlsx`; `docs/reference-shots/` (78 MB of captures) is deliberately untracked. Stage with explicit paths, not `git add -A`.
- Use the Write/Edit tools for files; shell heredocs with quotes broke several times. Use `mv` instead of `rm -rf` with globs.

## 4. Driving the two games (window driver)

Script `drive3.ps1` lives in the session scratchpad `C:\Users\Lev\AppData\Local\Temp\claude\37da2ae53352b303\f0ac97a8-4fde-4eee-a610-2aed2dac2ec7\scratchpad` (it may be gone; rebuild it). Usage: `drive3.ps1 -Win ours|ftb -Dir <shots dir> -Steps "step; step; ..."`. Steps: `focus`, `move x y`, `click x y`, `rclick x y`, `mclick x y`, `wheel x y notches`, `drag x1 y1 x2 y2`, `key <SendKeys text>`, `type <text>`, `wait ms`, `shot <name>` (saves `<name>.jpg`), `ctrldown`, `ctrlup`, `rel`.

- **`key` takes SendKeys syntax**: `{ESC}`, `{ENTER}`, `^s` for Ctrl+S. Writing `esc` or `ctrl+s` types those letters (it typed junk into the FTB chat once).
- Windows: FTB = process `javaw`, title `Minecraft NeoForge* 26.1.2 - Singleplayer` (PolyMC instance `C:\Users\Lev\AppData\Roaming\PolyMC\instances\26.1.2`, GUI scale 3). Ours = title `Cleanroom`. If `javaw` is not running the driver fails with "no process named javaw"; for ours it still works.
- Screenshots are JPEGs; coordinates are in the window's own pixel frame **as of the screenshot** (ours changes between about 870x519 and 2576x1416 depending on how the user sized the window). Re-screenshot after any resize.
- Our book key is **J**. In FTB 26.1.2 `o` appeared to open the book (not confirmed; earlier notes say it is unbound).
- Ctrl+S in FTB saves quest files **only from the book map screen**, not from inside a quest window. Closing the book also saves ("Quests saved on server").
- Left-clicking in the world breaks blocks and plants; right-clicking with a spawn egg spawns a mob. Select a harmless item first.
- 1.12.2 has **no `/tag` command**: use `/scoreboard players tag @p add <tag>`. `/rquests list|info|check|submit|claim|claimall|complete|reset` exist for quick state checks.
- Measurement: 3 px per GUI px in FTB captures. `ShowWindow(h,9)` un-maximizes and breaks coordinates; use 3.

## 5. FTB 26.1.2 facts learned (keys read from files FTB saved)

- Format: json5 in `config/ftbquests/quests` (`chapters/`, `lang/en_us/`, `reward_tables/`, `data.json5`, `chapter_groups.json5`).
- Rewards: numeric decimal `table_id` (the table file is keyed by the hex id), `exclude_from_claim_all`, command `feedback_message`.
- Tasks: item = nested `item: {id, count}`; kill: `entity`, `value`, `entityTypeTag`, `custom_name`, `nbt_filter`; stat: `stat`, `value`; xp: `value`, `points`; location: `dimension`, `ignore_dimension`, `position`, `size`; observation: `timer`, `observation_type`, `to_observe`; fluid: `fluid: {fluid, amount}`; **Stage: `type: "gamestage"`, `stage`, `team_stage`**; **Interaction: `type: "interaction"`, `to_interact`, `interaction_type` (block, block_tag, block_state, entity_type, entity_type_tag)**; descriptions use `hover_event` / `click_event` components.
- FTB's task type list order: Item, Custom, XP Levels, Visit Dimension, Stat, Kill Entity, Location, Checkmark, Advancement, Observation, Visit Biome, Find Structure, Stage, Fluid, Interaction, Forge Energy. Forge Energy asks only for a number (default 1).
- Settings windows: Title, Icon (shows "None" when unset), Tags, then the type's own rows plus Optional Task in alphabetical order, Disable Completion Toast last; reward windows: everything after Tags alphabetical. Window titles read `<name> [Task]` / `<name> [Reward]`.

## 6. Where the code is

`reflibquests/src/main/java/com/levviata/reflibquests/`:
- `client/QuestBookWidget.java` (the main screen, very large: layout, menus incl. `taskTypeMenu`/`rewardTypeMenu`, icons `drawTaskIcon`), `TaskRewardScreen.java` (task and reward edit windows, row order in `initGui`), `QuestText.java` (titles, tooltips, task pictures), `ItemPickerScreen`, `ListPickerScreen`, `NamePrompt`, `SettingsScreen`, `ChangeLog.java` (edit log wording), `RewardTablesScreen`.
- `model/BookEditor.java` (all edit ops, server-validated; `TASK_TYPES`, `setTaskProp`), `model/Definitions.java` (`Task.maxProgress`, `itemCount`), `model/QuestRules`, `ProgressEngine`.
- `game/QuestServer.java` (`SUPPORTED_TASKS`), `game/TaskTracker.java` (polling, `onKill`, `onInteract`, `submitFluid`, `submitEnergy`), `RewardGranter`, `QuestEdits`, `ReflibQuestsMod.java` (event hooks).
- `io/UpstreamImporter/Exporter`, `Ids.tableId`, `LegacyImporter`. Tests under `src/test` (`BookEditorTest`, `IdsTableTest`, `RichTextTest`, `ReferenceBookAuditTest` dev tool run with `-Ppack=<folder>`).

## 7. Process notes

- Start by reading `MEMORY.md` (window driver, real keys, parity rules, never close the game, locate before clicking).
- CI: the "build / build" check once failed on `:reobfJar` (Gradle configuration-cache error); it predates this work. Check whether it still fails on PR #5 / `main`.
- The showcase book is built through FTB's UI so every key is valid; `Rewards and Images` chapter holds item, fluid, stat, kill, location, dimension, advancement, observation, biome, structure, custom and xp tasks plus command, advancement, choice, toast, random, loot, all_table, custom and item rewards. Our dev copy additionally has Stage, Interaction and Forge Energy tasks on the Iron Ingot quest and an extra empty test quest at the top left of that chapter (delete if unwanted). The user's FTB copy has Stage and Interaction tasks added on its Iron Ingot quest.

## 8. LvNTM book in 26.1.2 and the NeoForge debug helper (2026-11-23)

- Conversion of the user's LvNTM 1.12.2 pack to FTB Quests 26.1.2 with a 26.1.2 Nuclear Tech id table: `docs/lvntm-26-1-2-conversion.md` (method, substitutions, what was changed on disk). Tool: `ConvertLegacyPackTool` (test source set, run with `-Ppack= -Pout= -Preport=`), `LegacyImporter.loadWide`, `LegacyModernizer`, table `reflibquests/src/main/resources/ntm/legacy_1_12_2_to_26_1_2.json` built by `reflibquests/tools/ntm/build_map.py`. FTB 26.1.2 needs **16-digit hex ids** (language keys); groups must not share an id with a chapter.
- NeoForge helper: `reflibdebug/neoforge/` (plain javac, `python -I build.py`, Java 25, compiled against PolyMC's own libraries), jar `reflibdebug-neoforge-0.2.0.jar` in the 26.1.2 instance `mods`. Client: `reflibdebug/dbg.py neo|lv "cmd" ...` (same protocol as the 1.12.2 helper; aliases neo and lv). In 26.1.2: book key **P** (O is FTB Teams), `/ftbquests reload` re-reads the files while the game runs, `/ftbquests open_book <16-hex id>` jumps to a quest, `hbm:material` is the scraps component. Real FTB screens need `move`, ~300 ms, then `click`.
- Opening a world in the 1.12.2 LvNTM game can stop at Forge's "missing registry entries" prompt; the helper cannot answer it (the client thread is blocked). It needs a click on the window.
