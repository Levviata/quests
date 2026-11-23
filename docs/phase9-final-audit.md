# Audit of Reflib Quests against FTB Quests 26.1.2 (updated 2026-11-23 from the code)

Method: every status below comes from reading the code of this repository. **Comments and documentation were ignored**; the code is the only reference. Where this file and an older document disagree, this file wins.

Statuses:

- **Complete**: works end to end **and was seen working in game**. Nothing is Complete from reading code or a unit test alone.
- **Partial**: works, but part of what FTB Quests 26.1.2 does is missing, or the 1.12.2 version intentionally differs (named in the row).
- **Missing**: no implementation.
- **Broken**: implemented and seen failing. None found.
- **Unverified**: the code path exists end to end, but nobody has seen it work in game.

Evidence tags: `K` = seen working in game by the user (`K earlier` = in an earlier session's checks, kept as they were), `C` = what the code does.

New in this update: the **Fluid task** is Complete, confirmed by the user in game with Nuclear Tech Community Edition's containers. Rows whose code changed since they were seen working keep their status and name the changed part as C. A section at the end lists what was added after the first audit.


### Tasks

| # | Feature | Status | Evidence |
|---|---|---|---|
| 1 | Item task (collect / consume) | Complete | K earlier: works in game. Matching also reads the item's damage and NBT (NBT must be contained; `Exact` makes it equal); those parts are only C |
| 2 | Custom task | Partial | C: created, shown and counted like any task, but nothing completes it: no command, no script hook and no event for it. Only Java code can call `QuestServer.setTaskProgress`. `QuestEvents` only reports progress, it does not drive tasks |
| 3 | XP Levels task | Unverified | C: completes when the player clicks the task; levels (or points) are taken from the player (`submitExperience`) |
| 4 | Visit Dimension | Unverified | C: polled each detection tick against the player's dimension; the Dimension row cycles through every registered dimension type, modded ones as `modded:<name>` |
| 5 | Stat task | Unverified | C: reads the player's statistic every detection tick; 1.12.2 ids are mapped for 25 statistics, any other never progresses |
| 6 | Kill Entity | Complete | K earlier: works in game |
| 7 | Kill Entity: name and NBT filters | Unverified | C: a kill counts only if the custom name equals the filter and the victim's data contains the NBT filter; a filter that does not parse matches nothing |
| 8 | Kill Entity: entity type tag | Partial | C: a tag is stored but, without an entity id, nothing can match (1.12.2 has no entity type tags; intentional difference) |
| 9 | Location task | Unverified | C: polled; position box in the stored dimension (or any with Ignore Dimension) |
| 10 | Checkmark | Complete | K earlier: works in game |
| 11 | Advancement task | Unverified | C: also detects an advancement earned before the quest started; a named criterion is checked on its own, otherwise the whole advancement |
| 12 | Observation task | Partial | C: counts once when the player's look ray (6 blocks) hits the named block; the timer and entity observation are not used |
| 13 | Visit Biome | Unverified | C: polled against the biome at the player's position |
| 14 | Find Structure | Partial | C: only the vanilla 1.12.2 structures are known (intentional difference) |
| 15 | Stage task | Complete | K earlier: completed in game after `/scoreboard players tag @p add <stage>`. 1.12.2 has no game stages, a player tag is used; Team Stage is kept in the file and ignored |
| 16 | Fluid task (click to submit, empties containers) | Complete | K: the user confirmed in game that it works correctly with Nuclear Tech Community Edition's containers. C: clicking empties the carried containers one at a time until the task is satisfied |
| 17 | Interaction task | Partial | C: block interaction event; tag types match nothing; the block state is read as its block |
| 18 | Forge Energy task | Unverified | C: completes when the player clicks the task and carries an energy item (`submitEnergy`) |
| 19 | Item filter by tag / ore dictionary | Partial | C: ore dictionary and item sets instead of tags (intentional difference) |
| 20 | Task options: Optional Task, Disable Completion Toast, Title, Tags | Unverified | C: stored and read; the effect of each was not seen in game |
| 21 | Task type list in FTB's order and wording | Complete | K earlier: compared in game. The map right-click menu now uses the same list |
| 22 | Task edit windows in FTB's row order and titles | Complete | K earlier: compared for Kill, Location, Stat, Fluid, Item, Stage, Interaction. The Fluid window changed afterwards (one Fluid row, a Match Data Components row) and was not compared again |
| 23 | Task Screen block, barrier blocks | Missing | C: no block or item is registered by the mod |

### Rewards and reward tables

| # | Feature | Status | Evidence |
|---|---|---|---|
| 24 | Item reward | Complete | K earlier: works in game. Item rows, Random Bonus and Only One are read by the granter; the Item row and NBT are only C |
| 25 | Choice reward | Unverified | C: grants the chosen entry of its table |
| 26 | Random reward | Unverified | C: weighted pick, with the table's empty weight |
| 27 | Loot reward | Unverified | C: rolls the table `Loot Size` times |
| 28 | All Table reward | Unverified | C: grants every entry |
| 29 | Command reward (level, silent, feedback) | Unverified | C: runs the command for the player |
| 30 | Custom reward | Partial | C: counts as granted and does nothing; no event is posted for it. The original's behaviour was not compared |
| 31 | XP reward | Unverified | C: adds experience points |
| 32 | XP Levels reward | Unverified | C: adds levels |
| 33 | Advancement reward | Unverified | C: grants the named criterion, or all remaining ones |
| 34 | Toast reward | Partial | C: sends a chat message, not a toast |
| 35 | Stage reward | Unverified | C: adds or removes a player tag (`Remove` row); the stage is a player tag, as for the Stage task |
| 36 | Currency reward | Missing | C: not implemented; the type is not offered and the Per-Team list keeps only a stored key for it |
| 37 | Reward options (Auto-claim, Per-Team, Exclude from Claim All, Random Bonus, Only One, Feedback Toast) | Unverified | C: stored and read by the server; the effect of each was not seen in game |
| 38 | Reward edit windows in FTB's order and titles | Complete | K earlier: compared for command, table reward and item. Table entries now open them with a bold, coloured title; not compared again |
| 39 | Table rewards find their table (numeric `table_id`) | Unverified | C: `Ids.tableId` reads the id; a grant was not run in game |
| 40 | Reward table editor | Unverified | C: list with right-click menu (Edit, Remove, Enable/Disable Loot Crate, Copy ID); table window with right-click menu (Edit, Set Weight, Remove), weights, an 8-type add menu whose choice opens the reward's options first and adds it on Accept; properties window with Title, Icon, Tags, Empty Weight, Hide Tooltip, Loot Size, Use Title and the loot crate rows |
| 41 | Loot crates, rarity presets, nested crates, `/give` crates | Partial | C: only the crate's data can be edited (colour, glow, id, item name, entity drop weights, enable/disable). No crate item exists, nothing drops and nothing opens; rarity presets, nesting and `/give` are not implemented |
| 42 | Export/import table from chest, delete-empty-tables command | Missing | C: not implemented |

### Quest settings and editor

| # | Feature | Status | Evidence |
|---|---|---|---|
| 43 | Title, subtitle, description (rich text, hover and click events) | Unverified | C: the description editor has bold, italic, underline, strike, a 16-colour palette, links (quest and URL), a hover prompt, JSON conversion, images and page breaks; hover and click events are drawn in the quest window |
| 44 | Quest icon (Item Icon / Image Icon menu; "Use Task Icon as Quest Icon") | Partial | C: the Icon row opens a menu with Item Icon and Image Icon, each with its selector; item icons keep damage and NBT; Clear is the empty cell. The Entity Face Icon is left out on purpose (user decision) |
| 45 | Shape, size, position | Complete | K earlier: compared side by side |
| 46 | Visibility options (hide details/text, invisible, until N tasks, hide lines) | Unverified | C: rules in `QuestRules`; in edit mode an eye marks a quest players cannot see, and the quest window shows an eye for hidden text |
| 47 | Dependencies, requirement, min required | Unverified | C: requirement, min required and max completable dependents are evaluated in `QuestRules` |
| 48 | Progression, repeatable, sequential, optional | Unverified | C: stored and evaluated |
| 49 | Icon scaling, visual presets, max completable dependents, guide page | Unverified | C: icon scaling and presets change the drawn node; the guide page draws a "Click here to read more..." line (clicking does nothing, there is no guide book). Exclusive branch: not implemented |
| 50 | Min window width | Partial | C: the chapter's minimum width widens the quest window; there is no per-quest value |
| 51 | Quest window layout (tasks/rewards rows, balanced icon rows, Edit menu) | Complete | K earlier: compared with FTB. The window now also widens for a long title and the whole screen is dimmed behind it (C) |
| 52 | Quest right-click menu | Partial | C: FTB's entries plus extras (Add Dependency, Bezier). Edit opens the properties window; the quest window is a separate click |
| 53 | Bezier dependency control points | Unverified | C |
| 54 | Selection, copy/paste, quest links | Unverified | C |
| 55 | Undo / redo | Unverified | C: server-side history with tests; the user saw an undo of a resize earlier |
| 56 | Colour picker, Text Shadow | Partial | C: a fixed 16-colour palette in the description editor; no colour wheel picker, no Text Shadow |

### Chapters and map

| # | Feature | Status | Evidence |
|---|---|---|---|
| 57 | Chapter panel (groups, width from widest name, badges, completed mark) | Complete | K earlier: measured against FTB |
| 58 | Chapter create / move / group / rename / delete | Unverified | C |
| 59 | Chapter settings, subtitle list, default size | Unverified | C |
| 60 | Chapter icon | Partial | C: same Item Icon / Image Icon menu as quests (no Entity Face); without an icon the sidebar cycles its quests' icons every 2 s |
| 61 | Shared zoom, view recentred, Esc order | Complete | K earlier |
| 62 | Chapter images: draw, move, delete, edit, create | Partial | C: any picture of the game (every texture of the game jar, mods, resource packs and supplied jars, plus the painting names) can be placed from the map's Image entry; ids are full texture paths, not FTB's sprite names |
| 63 | Chapter image click actions | Partial | C: Open a URI, Jump to a Quest and Show Recipes for Item work; Run a Command, Post Custom Event and Show a Docs Page only report that they belong to the newer game |
| 64 | Autofocus quest | Unverified | C |
| 65 | Map right-click: task types list, Image; a new quest opens the task's options and is created only on Accept | Unverified | C |
| 66 | Icons cycle (quests through their tasks, chapters through their quests, reward tables through their rewards, 2 s each) | Unverified | C |
| 67 | Untitled quest takes its first task's title (own title or name); "Unnamed" without tasks | Unverified | C |

### Player, commands, styling, settings

| # | Feature | Status | Evidence |
|---|---|---|---|
| 68 | Open book (key J, `/rqbook`) | Complete | K earlier |
| 69 | Book item, inventory button | Missing | C: not implemented |
| 70 | Search, pin, tracker | Complete | K earlier |
| 71 | Claim, claim all, completion toast, alerts | Unverified | C: claim messages exist; not re-run since the first checks |
| 72 | Key reference screen | Partial | C: present, not written to FTB's text |
| 73 | Info/Wiki button (opens docs after confirmation) | Complete | K earlier |
| 74 | Gear menu: Download Quest Files, Reload Theme | Missing | C: the menu has Edit File, Reset Progress, Complete Instantly, Edit Reward Tables and Save on Server now; the other two entries do not exist |
| 75 | Player preferences, notifications (toast/chat/none) | Partial | C: no action bar |
| 76 | Pinned tracker mode key | Partial | C |
| 77 | Team progress, multiplayer sync | Complete | K earlier: two real clients; not re-run since |
| 78 | `/rquests` command set | Partial | C: reload, export, problems, list, info, check, submit, claim, claimall, complete, reset, lock, blockrewards; no reset-all, complete-all, change_progress, editing_mode, chapter generation |
| 79 | Theme in resource pack, styles by id/tag, line colour/animation, full-screen quest window | Missing | C: not implemented; the pictures come from the supplied FTB jars |
| 80 | Book settings stored but inert (drop book on death, emergency items, loot crates drop, lock message, hide excluded quests, verify on load) | Missing | C: stored and exported, nothing reads them in the game or the client |
| 81 | Book settings that work (detection delay, pause game, autoclaim default, progression mode, fallback locale, disable GUI) | Unverified | C: read by the server or client |
| 82 | Suppress-all-autoclaim switch | Unverified | C: read in `QuestServer` |
| 83 | Reference book import (all 5 chapters, 40 quests, 43 tasks, 10 rewards) | Complete | K earlier: 0 importer problems, round trip identical, book shown in game |
| 84 | Upstream files: unknown keys kept (`raw`) and re-exported | Complete | K earlier: round trip, book loads |
| 85 | Reward table, image and group keys of 26.1.2 files | Complete | K earlier |
| 86 | Cosmetics left as ours on purpose (panel texture, map background) | Partial | user decision: keep our theme |

### Added since the first audit

| # | Feature | Status | Evidence |
|---|---|---|---|
| 87 | One selection window for items, images and fluids (list modes, search, scroll bar, amount box, widens for long titles, Accept/Cancel tooltips) | Unverified | C: replaces the old pickers. Items: All Items and Inventory modes; images: every texture of the game; fluids: every registered fluid. The user used it in game and sent corrections, but the final state was not confirmed |
| 88 | NBT on items: name tag button, quick string editor | Unverified | C: stored with picked items, task and reward items, table entries and icons; a stack must contain a task item's NBT (or equal it with Exact); invalid text is refused. The right-click interactive NBT editor is Missing |
| 89 | Item damage kept over the id map (diorite is not stone) | Unverified | C: an explicit damage on an id this game has wins over the id map's meta |
| 90 | Fluid NBT matching and Match Data Components on fluid tasks | Unverified | C: a container's fluid is skipped unless its data matches; the row mirrors the item task's. The original's fluid task was not compared |
| 91 | Settings-style windows sized from FTB measurements, draggable scroll bar, width for the longest value | Unverified | C |
| 92 | Quest window: wider for long titles, whole screen dimmed behind it, hidden-text eye, guide-page line | Unverified | C |

## Counts (92 features)

Computed from the Status column of this table.

| Status | Count |
|---|---|
| Complete | 20 |
| Partial | 21 |
| Missing | 7 |
| Broken | 0 |
| Unverified | 44 |
| Total | 92 |

## What is not done, and what to do about it
1. **Unverified**: reward grants, most task detection (dimension, biome, location, XP, advancement, stat, energy), claim flows, the editor operations and everything added since the first audit. Closing them needs a play-through in game, ideally with two clients.
2. **Missing**: task screen and barrier blocks, Currency reward, chest import/export of tables, the book item, Download Quest Files and Reload Theme, the theme and styles, the book settings that are stored but inert, and the interactive NBT editor. Each needs an FTB capture first (do not guess keys).
3. **Custom task and reward**: nothing completes a custom task and a custom reward does nothing. Scripting support (GroovyScript first) is planned for later: our own events plus a way for a script to complete a custom task or set its progress.
4. **Intentional differences** (inside Partial): no tags in 1.12.2 (entity type tag, item tags), Stage as a player tag, structures only vanilla, no Entity Face icon, picture ids are texture paths.
