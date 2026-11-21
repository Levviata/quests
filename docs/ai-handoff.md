# AI hand-off: Reflib Quests (1:1 FTB Quests parity)

Written 2026-11-21 for the next AI session. Read this, then `docs/comparison-plan.md`, `docs/feature-catalogue.md`, `docs/upstream-quest-format.md`.

## 1. What this is

`reflibquests` is a Cleanroom 1.12.2 quest mod on the library `reflib`, own code only, LGPL-2.1. The user wants it to look and behave **exactly like FTB Quests 26.1.2**. Exactness is the whole point: measure, never guess.

- Worktree (where all work lives): `G:\TRABAJO\referencing lib\.claude\worktrees\archaiclib-architecture-investigation-ea090b`, branch `claude/archaiclib-architecture-investigation-ea090b`.
- Merged to `main` through PRs #1, #2, #3. Last merged commit `3c55078`. Later work is uncommitted (see section 7).
- Build: `./gradlew.bat build --console=plain -q` from the worktree root (tests included). Launch the dev game: `./gradlew.bat :reflibquests:runClient --console=plain` (run it in the background).
- Dev instance: `reflibquests\run\client\`. The dev book is read from `config\reflibquests` in FTB's file format (`data.json5`, `chapter_groups.json5`, `chapters\*.json5`, `lang\`, `reward_tables\`). Mod Director's jar in `mods\` is disabled on purpose (it crashed startup).

## 2. Rules the user set (follow them)

- **Never close or kill the user's game unasked.** Check the process list and free RAM first (`Get-CimInstance Win32_OperatingSystem`); ask the user to close ours before building or relaunching if it is running.
- **No guessing.** Values come from screenshots, captures or files FTB itself wrote. State exact numbers. If unsure, ask or capture.
- **Allowed references:** assets and lang in the supplied jars (`reflibquests\run\client\reflib-supplied\ftb-quests-neoforge-26.1.2.10.jar`, `ftb-library-neoforge-2101.1.39.jar`, `atm-questpics.zip`), docs.feed-the-beast.com, the user's screenshots, and files FTB saved in the 26.1.2 instance. **No decompiling, no reading FTB source.**
- **Work in three passes, never interleaved:** 0) decide what to check, 1) capture everything on both sides, 2) compare it all and write one list, 3) fix the whole list, build once, relaunch. The user was explicit that 123,123,123 is wrong.
- **Be fast.** Create things where they belong; no create-then-move detours.
- Use the best existing widget (`TextEditor`, `Scroller`, `Ui.vanillaButton`, `Ui.drawTooltip`, `SettingsScreen`) before building one.
- Keep replies short. Commit and push only when asked. The user's spreadsheet `reflibquests\Reflib Quests vs FTB Quests comparación de funciones.xlsx` is untracked on purpose; leave it.
- A safety hook refuses writes to a worktree other than the session's own, and `rm -rf` with globs. Use `mv`/rename instead of deleting.

## 3. Tooling for the 26.1.2 game (the "window driver")

The user approved driving their running FTB 26.1.2 client (PolyMC instance `C:\Users\Lev\AppData\Roaming\PolyMC\instances\26.1.2`, GUI scale 3, en_us, no resource packs). Details are in memory `reference-26-1-2-window-driver`. The scripts live in the old session scratchpad; **rebuild them if gone**:

- Capture: `PrintWindow(hwnd, flags=2)` on the window handle gives a PNG of the whole window (2576x1416 when maximized), even behind other windows.
- Drive: bring the window to the front with a verified focus (`ShowWindow(h,3)` + AttachThreadInput + ALT trick + `SetForegroundWindow`, check `GetForegroundWindow()==h`, retry), then real input: `SetCursorPos` + `mouse_event` (left/right, with a drag helper), keys with `SendKeys` (`{ESC}`, `^a`, text).
- Windows: FTB = process `javaw`, title `Minecraft NeoForge* 26.1.2 - Singleplayer`. Ours = process `java`, title `Cleanroom`.
- Pitfalls: `Lp` is a PowerShell alias (don't name a function that). `ShowWindow(h,9)` un-maximizes and breaks coordinates; use 3. In ours, Esc used to close the whole screen with a menu open (fixed). Esc with no menu open closes the quest screen. Clicking the pause-menu button via messages did not work, real input does.
- Do not type into chat (characters get dropped and it posts garbage). The quest book key is unbound in 26.1.2: the user opens the book themselves.
- Scope approved: read-only exploration plus building the showcase through the UI. Never click Accept/Delete/Reset/Complete on the user's real data without being told.
- Measurement: 3 px per GUI px in FTB captures at GUI scale 3. Both windows were maximized at the same size when compared.

## 4. Test data: the showcase book

Built through FTB's own UI (so every key is valid), saved at `...\26.1.2\.minecraft\config\ftbquests\quests\` and copied into our dev `config\reflibquests`. Chapters: `Shapes and Sizes`, `Dependencies`, `Visibility Options`, `Task Types`. Backups of what they replaced: `config\ftbquests\quests.backup-20261121` (user's original 26.1.2 test quests), `quests.ours-export-20261121` (our legacy book exported), and in our dev config `reflibquests.book-backup-20261121`.

Real FTB 26.1.2 keys read from those files are in memory `reference-ftbq-26-1-2-real-keys` (task keys: xp `value`+`points`, stat `stat`+`value`, kill `entity`+`value`, location `dimension`/`ignore_dimension`/`position`/`size`, advancement `advancement`+`criterion`, observation `timer`/`observation_type`/`to_observe`, biome, dimension, custom, checkmark, item; quest `dependencies`, `optional`, `size`, `x`, `y`). Task titles live in lang as `task.<id>.title`; chapter, quest and image titles likewise.

FTB UI facts learned: quick-edit Shape cycles default, circle, diamond, gear, heart, hexagon, none, octagon, pentagon, rsquare, square; tri-state options cycle Default, false, true; Dependency Requirement cycles ALL_COMPLETED, ONE_COMPLETED...; a quest is created where you right-click the map; the quest window dims the map by about 63% of a dark grey.

## 5. Where the code is

`reflibquests/src/main/java/com/levviata/reflibquests/`: `client/QuestBookWidget.java` (the main screen, very large), `client/Ui.java`, `QuestArt.java`, `QuestText.java`, `SettingsScreen.java` and the `*PropertiesScreen`s (FTB-style settings windows with tooltips, ranges, disabled rows, option lists), `TableEdits.java` (client staging of edits), `model/BookEditor.java` (all edit ops, server-validated), `model/QuestRules.java`, `model/ProgressEngine.java`, `game/QuestServer.java` (edits, resync, commands), `game/TaskTracker.java`, `io/UpstreamImporter/Exporter` (FTB file format), `io/LegacyImporter.java`. Tests are under `src/test`.

## 6. What matches FTB already (verified by captures)

Chapter panel structure; right-click menus for quest, chapter, group, task, reward; gear menu (Reset Progress / Complete Instantly with FTB's Yes/No screens); Player Preferences; file settings window; the quest window layout, dim, Edit menu, slot hover box; shadows (offset 3 GUI px, 16% black); dependency chevron lines; hidden-quest eye; locked tooltip text; image edit window rows and tooltips.

## 7. Uncommitted and unverified (do this first)

Built and tests pass, but **not yet seen running**, because the last relaunch was interrupted:

- Task titles read from and written to lang (`task.<id>.title`) in `UpstreamImporter`/`UpstreamExporter`. **No test yet.**
- FTB-style default quest titles and colours per task type (`QuestText.autoTitle`): "XP Levels: 5" (number red while the player is short), "Kill: 100x Zombie", "Mob Kills", "Location", "Visit Dimension: <id>", "Visit Biome: <id>", "Observation: <id>", "Advancement: <name>" (id or name in the FTB colours).
- Task pictures: XP bottle, ender eye, iron sword, compass, grass block for the root advancement, sapling, paper; custom = rainbow (`color_hsb`), observation = landscape (`art`) icon.
- Tooltip lines `[Optional Quest]` and `[Repeatable Quest]`; removed the `[Optional]` subtitle replacement.
- Dependency chevron lines (`dependency.png` from the quests jar, 4 GUI px wide, tint `#7A6B71`), `check_gray` for unfinished checkmark quests, the eye on hidden quests in edit mode, slot check/rainbow icons.
- `docs/comparison-plan.md`.

Next step: relaunch ours (ask the user to close it first), open the book, re-capture the four chapters on both sides, and confirm each item above. Commit when the user says so.

## 8. Known remaining differences (from the last comparison)

- Map check icon: edges and fill differ slightly from FTB's; the exact FTB rendering is unresolved.
- The "Edit ▼" arrow glyph in the quest window; the eye FTB draws in the quest window when its text is hidden.
- The Visit Dimension quest in Task Types was never captured (off screen).
- Quick-edit lists to compare: FTB's quest quick edit has Disable Completion Toast, Shape, Visual Preset, Hide Quest Until Dependencies Complete/Visible, Invisible Until Completed, Hide Details Until Startable, Hide Text Until Quest is Completed, Hide Lock Icon, Dependency Requirement, Hide Dependency Lines, Hide Dependent Lines, Disable JEI Recipe, Repeatable Quest, Optional Quest, Ignore reward blocking, Progression Mode, Sequential Task Completion. Compare with ours.
- FTB's quest Edit window also has Icon Scaling, Min Opened Quest Window Width, Visual Preset, X/Y, Max Completable Dependents, Min Required Dependencies, Hide Dependent Lines, Description; ours lacks some.
- Group "Quick Edit Properties" content in FTB is unknown (ours only has "Hold in Chapter...").
- Toast sizes (FTB toasts grow taller for long names; vanilla slots are 32 px).

## 9. Features not built yet (do after the visuals match, as the user asked)

Stat/Stage/Fluid/Forge Energy/Interaction tasks, Stage reward, loot crates, Emergency Items list, Visual Presets and Default Visual Preset, Per-Team Reward by Type, colour picker (RGB wheel with palettes), description click/hover events (JSON components), Text Shadow and non-URI click actions for images (keys unknown: capture them in FTB first), item task via the item picker and structure task in the showcase, rewards and images chapters in the showcase, quest links keys.

## 10. Process notes for the next session

- Start by reading `MEMORY.md` entries named above (window driver, real keys, FTBQ parity, never close the game).
- CI: the "build / build" check failed on `:reobfJar` (Gradle configuration-cache error). It predates this work; look at it separately.
- Tell the user plainly what is verified in the game and what is not.
