# Phase 4: FTB Quests 26.1.2 UI capture (2026-11-21, read-only)

Captured from the user's running client (window 2576x1416, GUI scale 3, edit mode, chapter "Task Types") with the window driver (`drive3.ps1` adds a `wheel x y n` step to the old `drive2.ps1`). Only menus and settings windows were opened. Every settings window was closed with **Cancel**; nothing was accepted, created, deleted, completed or reset. Screenshots: `docs/reference-shots/ftb-*.png`.

## Map right-click on empty space ("Select Task Type"; creates a quest at the clicked cell, or an image)
Item, Custom, XP Levels, Visit Dimension, Stat, Kill Entity, Location, Checkmark, Advancement, Observation, Visit Biome, Find Structure, Stage, Fluid, Interaction, Forge Energy, **Image**. Ours has the same order without **Image**, and with Stat/Stage/Fluid/Interaction/Energy greyed.

## Gear menu (bottom right)
Edit File, Reset Progress, Complete Instantly, Edit Reward Tables, Save on server now `[CTRL + S]`, **Download Quest Files**, **Reload Theme**, **Open Wiki**. Ours lacks the last three.

## Quest right-click (title row `"Mob Kills"`)
Edit, Move, **Resize**, Quick Edit Properties..., Delete, Complete Instantly, Set as Autofocused Quest, Copy ID. (Reset Progress is absent for an untouched quest.)

Quick Edit Properties list, in order (check-mark rows are boolean toggles, gear rows open a cycle/list): Disable Completion Toast, Shape, Visual Preset, Hide Quest Until Dependencies Complete, Hide Quest Until Dependencies Visible, Invisible Until Completed, Hide Details Until Startable, Hide Text Until Quest is Completed, Hide Lock Icon, Dependency Requirement, Hide Dependency Lines, Hide Dependent Lines, Disable JEI Recipe, Repeatable Quest, Optional Quest, Ignore reward blocking, Progression Mode, Sequential Task Completion.

## Quest Edit window (`Mob Kills [Quest]`): sections and rows with the displayed default
- **Quest**: Title `""`, Icon `None`, Tags `[]`, Description `[]`, Subtitle `""`, Disable Completion Toast `False`
- **Quest -> Appearance**: Icon Scaling `1`, Min Opened Quest Window Width `0`, Shape `Default`, Size `1`, Visual Preset `None`, X, Y
- **Quest -> Visibility**: Hide Details Until Startable `Default`, Hide Lock Icon `False`, Hide Quest Until Dependencies Complete `Default`, Hide Quest Until Dependencies Visible `Default`, Hide Text Until Quest is Completed `Default`, Invisible Until Completed `False`, Invisible until X tasks are completed `0` (**greyed while Invisible Until Completed is False**)
- **Quest -> Dependencies**: Dependencies `[]`, Dependency Requirement `All Completed`, Hide Dependency Lines `Default`, Hide Dependent Lines `False`, Max Completable Dependents `0`, Min Required Dependencies `0`
- **Quest -> Misc**: Disable JEI Recipe `Default`, Guide Page `""`, Ignore reward blocking `False`, Optional Quest `False`, Progression Mode `Default`, Quest Repeat Cooldown `0`, Repeatable Quest `Default`, Sequential Task Completion `Default`

Not in ours (grep of `client/*.java`): Icon Scaling, Visual Preset, Max Completable Dependents, Guide Page.

## Chapter right-click and Edit window (`Task Types [Chapter]`)
Menu: Move (up), Move (down, greyed at the last position), Change Group, Edit, Quick Edit Properties..., Delete, Complete Instantly, Copy ID.
- **Chapter**: Title, Icon `None`, Tags `[]`, Subtitle `[]` (a list, not a string), Disable Completion Toast `False`
- **Chapter -> Appearance**: Default Min Opened Quest Window Width `0`, Default Quest Shape `Default`, Default Quest Size `1`, Default Visual Preset `None`
- **Chapter -> Visibility**: Always Invisible, Default 'Hide Dependency Lines', Hide Quest Details Until Startable, Hide Quests Until Dependencies Complete, Hide Quests Until Dependencies Visible, Hide Text Until Quest is Completed (all `False`)
- **Chapter -> Misc**: Autofocus Quest ID `""`, Default 'Progression Mode' `Default`, Default 'Repeatable Quest' `False`, Default Item Task 'Consume Items' `Default`, Sequential Task Completion `False`

Not in ours: Default Visual Preset. Subtitle is a plain string in ours; FTB shows a list of lines.

## Book "Edit File" window (`Quest Book [Quest Book]`)
- **Quest Book**: Title `""`, Icon `None`, Tags `[]`, Disable GUI, Drop FTB Quests Book on Death, Emergency Items `[]`, Emergency Items Cooldown `300`, Entities Drop Loot Crates, Fallback Locale `"en_us"`, Grid Scale `0.50`, Hide Quests in excluded questlines, Item Auto-Detection Minimum Interval `20`, Lock Message, Progression Mode `Linear`, Quest Screen Pauses SSP Game, Show Icon for Locked Quests, Suppress all Reward Autoclaiming, Visual Presets `[ 3 items ]`, Disable Completion Toast
- **Quest Book -> Defaults**: Default Visual Preset `None`, **Per-Team Reward by Type `< Edit >`**, Quest 'Disable JEI Recipe', Quest Shape `Circle`, Reward 'Auto-claim' `Disabled`, Reward 'Team Reward', Task 'Consume Items'
- **Quest Book -> Entity Loot Crate Drop Empty Weight**: Boss `0`, Monster `600`, Passive `4000`

Not in ours: Per-Team Reward by Type, a Visual Presets editor (the 3 items are `normal`, `goal`, `info` in `data.json5`), Download Quest Files. (Tags exists at book level in ours.)

## Quest window (player/edit view) and its menus
Header: icon, title (`XP Levels: 5`, the 5 red while short), pin, close. Columns **Tasks** (blue) and **Rewards** (orange) with a green "+" each in edit mode and left/right arrows for the dependency/dependent lists; subtitle row `[No Subtitle]`; **Edit ▼** button.
- Reward "+" = **Select Reward Type**: Item, Choice Reward, All Table Reward, Random Reward, Loot Reward, Command, Custom, XP, XP Levels, Advancement, Toast, Stage. Same as ours (Stage greyed in ours).
- Edit ▼: Title `[T]`, Subtitle `[S]`, Description `[D]`, separator, Add Text Line `[L]`, Add Page Break `[P]`, Add Image `[I]`, separator, Quest Properties `[Q]`. Same as ours.
- Task right-click (`"XP Levels: 5"`): Move Left, Move Right, Edit, Quick Edit Properties..., Delete, Complete Instantly, Copy ID.

## Not yet captured (needed before building those features)
Task Edit windows per type (Stat, Fluid, Energy, Stage, Interaction, Item with NBT mode), Reward Edit windows, the Edit Reward Tables window, the Visual Presets list editor, Per-Team Reward by Type, Image creation and the Image Edit window's click-action options, Resize mode, the Change Group dialog, Quick Edit option values, the colour picker, hover/click events in descriptions, loot crates, tooltips in player mode, and a quest in the unlocked, completed and claimable states. Creating things (a quest, an image, a reward table) modifies the user's showcase book, which is their live game; that is Phase 5 and needs their go-ahead.

---

# Phase 4, second pass (2026-11-21): edit-mode captures that changed the user's book

With the user's go-ahead I created, in FTB's own UI, a new chapter **Rewards and Images** (file `chapters/rewards_and_images.json5`) holding one quest, one image and one reward table. A backup of the previous 4-chapter book is at `...\config\ftbquests\quests.backup-showcase-4ch-20261121`. Sample files copied to `docs/reference-book-phase4/`. Saved with Ctrl+S; files on disk confirmed.

## Side effect to be aware of
While looking for the Item task's **Edit** entry I clicked **Grab Copy of Item**. FTB put 1 iron ingot into the player's inventory (the player also got the vanilla advancement "Acquire Hardware") and the task shows as completed. The quest therefore has progress and the chapter shows a claim alert. Nothing else was clicked unintentionally. Reset Progress on that task (right-click) would undo the quest progress, but not the advancement or the ingot.

## Real keys written by FTB (from the saved files)
- Item task: `type: "item"`, `item: { id: "minecraft:iron_ingot", count: 1 }` (no top-level count).
- Fluid task: `type: "fluid"`, `fluid: { fluid: "minecraft:water", amount: 1000 }`.
- Stat task: `type: "stat"`, `stat: "minecraft:mob_kills"`, `value: 1`.
- Command reward: `type: "command"`, `command: "/say Hi, @p!"` (permission level, silent, feedback toast are written only when changed).
- Image: `x, y, width, height, rotation, image: "minecraft:backyard", id` (other keys only when changed).
- Reward table file `reward_tables/<ID>.json5`: `id, order_index, loot_size: 1, hide_tooltip: true, use_title: true, rewards: [ { id, xp: 100, type: "xp" } ]`; the title lives in `lang/en_us/reward_table.json5` as `reward_table.<ID>.title`.
- Chapter titles `chapter.<ID>.title` in `lang/en_us/chapter.json5`.

## Task Edit windows (field order and defaults as displayed)
- **Item**: Title `""`, Icon `None`, Tags `[]`, Consume Items `Default`, Count `1`, Item, Match Data Components `none`, Only Detect From Crafting `Default`, Only From Task Screen `False`, Optional Task `False`, Disable Completion Toast `False`.
- **Fluid**: Title, Icon, Tags, Fluid `1000mB Water Bucket`, Optional Task, Disable Completion Toast.
- **Stat**: Title, Icon, Tags, Optional Task, **Stat Name** (`Mob Kills`, a list), Value `1`, Disable Completion Toast. (Creating a Stat task opens this window straight away; accept is Shift+Enter.)
- **Stage**: Title, Icon, Tags, Optional Task, Stage `""`, **Team Stage** `False`, Disable Completion Toast.
- **Interaction**: Title, Icon, Tags, Match `"minecraft:air"`, Optional Task, Type `block`, Disable Completion Toast.
- **Forge Energy**: a single number prompt, default `1`.
- Creating an **Item** or **Fluid** task first opens a picker (item picker: search box, item grid, a count box with +/- and a tag/NBT button; fluid picker: a grid of the available fluids). Creating a task picks the quest title from the first task ("Iron Ingot") and the quest window opens by itself.
- Item task context menu has two extra entries: **Grab Copy of Item** and **Use Task Icon as Quest Icon**, then Move Left, Move Right, Edit, Quick Edit Properties..., Delete, **Reset Progress** (when it has progress), Copy ID. Other tasks: Move Left, Move Right, Edit, Quick Edit Properties..., Delete, Complete Instantly, Copy ID.

## Reward windows and menus
- Command reward Edit: Title, Icon, Tags, **Auto-claim** `Default`, Command (default `"/say Hi, @p!"`), **Disable Reward Screen Blur**, **Exclude from Claim All**, **Feedback Toast** `""`, Ignore reward blocking, **Per-Team Reward** `Default`, **Run with Permission Level** `0`, **Silent**.
- XP entry in a table: a number prompt, default **100**; the entry shows as `XP: +100` with the XP bottle.
- Reward-table entry types (the "+" inside a table): Item, Command, Custom, XP, XP Levels, Advancement, Toast, Stage (no nested tables).

## Reward tables window
- **Edit Reward Tables**: header `Add` button, list rows (an empty table shows a green row with a dice icon, a first-reward icon once it has one) with a "+" beside the name; Accept / Cancel at the bottom. Add asks for a name in a small prompt.
- Row right-click: Edit, Remove, **Enable Loot Crate**, Copy ID.
- Table editor (click Edit): header with icon, title, a green "+" (add entry) and a gear (settings); entries below; Accept / Cancel.
- Table settings (gear): Title, Icon, Tags, **Empty Weight** `0`, **Hide Tooltip** `True`, **Loot Size** `1`, **Use Title** `True`.

## Images
- Map right-click -> **Image** opens an image picker (a grid of every GUI texture, a search box with a landscape icon button on the left, Accept / Cancel). The created image is named by its resource id (`minecraft:backyard`) and appears where the menu was opened; the quest map lists `Create Image` in the change log.
- Image right-click: Edit, Move, Resize, **Rotate**, Copy ID, Delete.
- Image Edit window, section **Image**: Title `""`, Tags `[]`, **Click Action** `No Action`, **Click Action Data** `""`, **Dependency** (blank), Height, Image, **Image Alpha** `255`, **Image Ordering** `0`, **Image Tint** `#FFFFFF`, **Lock Position** `False`, **Only visible in Editing Mode** `False`, **Rotate and offset from corner** `False`, Rotation `0`, Width, X, Y. Section **Image -> Image Text Properties**: Draw Title Text on Image `False`; Text Horizontal Alignment `Middle`, Text Inset `0`, **Text Shadow** `False`, Text Vertical Alignment `Middle` (all greyed until Draw Title Text is on).
- **Click Action** cycles, in this order: No Action, Open a URI, Jump to a Quest, Run a Command, Post Custom Event, Show Recipes for Item, Show a Docs Page. The Click Action Data tooltip explains each: Open URI = `http://`, `https://` or `file:///`; Jump to a Quest = a quest ID ("Copy Quest ID" menu entry); Run a Command = a command run on the server; Post Custom Event = `namespace:path` for `CustomClickEvent` listeners; Show Recipes for Item = an item ID; Show a Docs Page = `<mod>,<book-id>[,<page-id>[,<anchor>]]`.

## Differences visible from this pass (to carry into Phase 7)
- Ours has no way to create an image in a chapter, no Rotate/Resize on images from the menu check, no Image Ordering/Tint list entries confirmed (`ImagePropertiesScreen` has alpha, order, rotation, corner, lock, text rows; Click Action offers only "No Action" and "Open a URI").
- Ours has no Stat, Fluid, Stage, Interaction and Forge Energy task support (greyed in the menu); FTB writes their keys as listed above.
- Table settings in FTB have Hide Tooltip, Use Title and Empty Weight/Loot Size; ours has Empty Weight and Loot Size (`BookEditor` `empty_weight`, `loot_size`); Hide Tooltip, Use Title and loot crates are Missing.
- Item picker, fluid picker and image picker are different widgets from ours (the ones ours uses were not compared yet).

## Still not captured
Choice/Random/Loot/All Table reward windows with a table, Advancement and Toast reward windows, Location/Dimension/Biome/Structure/Observation/Kill/Advancement task windows, Visual Presets editor, Per-Team Reward by Type, Resize/Rotate interaction, Change Group dialog, quest-state looks (unlocked, completed, claimable), toasts, loot-crate settings.

---

# Phase 4, third pass (2026-11-21): remaining task and reward windows, presets, per-team list

The "Rewards and Images" chapter now also holds six one-task quests (Location, Visit Dimension, Advancement, Observation, Visit Biome, Find Structure; the Location and Visit Dimension quests ended up 0.5 apart and overlap on the map), plus a Kill Entity task, an Advancement reward, a Choice reward and a Toast reward in the first quest. Saved with Ctrl+S ("Quests saved on server"); `docs/reference-book-phase4/rewards_and_images.json5` is the current file.

## Task windows (field order and defaults)
- **Kill Entity**: Title, Icon, Tags, Count `100`, Entity Name `""`, **Entity Type** `Zombie [minecraft:zombie]`, **Entity Type Tag**, **NBT Filter** `""`, Optional Task, Disable Completion Toast. Saved as `type: "kill", entity: "minecraft:zombie", value: 100`.
- **Location**: Title, Icon, Tags, Dimension `minecraft:overworld`, Ignore Dimension `False`, Optional Task, X/Y/Z Position `0`, X Size (W)/Y Size (H)/Z Size (D) `1`, Disable Completion Toast. Saved as `dimension`, `ignore_dimension`, `position: [x,y,z]`, `size: [w,h,d]`.
- **Visit Dimension**: Title, Icon, Tags, Dimension `minecraft:overworld`, Optional Task, Disable Completion Toast. Saved as `dimension`.
- **Advancement**: Title, Icon, Tags, Advancement (a list, default `Minecraft` = `minecraft:story/root`), Criterion `""`, Optional Task, Disable Completion Toast.
- **Observation**: Title, Icon, Tags, **Observe** `"minecraft:air"`, Optional Task, **Timer** `0`, **Type** `block`, Disable Completion Toast. Saved as `observation_type`, `to_observe`, `timer`.
- **Visit Biome**: Title, Icon, Tags, Biome `minecraft:plains`, Optional Task, Disable Completion Toast.
- **Find Structure**: Title, Icon, Tags, Optional Task, Structure `minecraft:mineshaft`, Disable Completion Toast. Saved as `structure`.
- Creating any of these from the map menu opens its window first and creates the quest only on Accept; the shortcut for Accept is Shift+Enter.

## Reward windows
- **Advancement**: Title, Icon, Tags, Advancement (default `Minecraft`), Auto-claim, **Criterion** `""`, Disable Reward Screen Blur, Exclude from Claim All, Ignore reward blocking, Per-Team Reward.
- **Toast**: Title, Icon, Tags, Auto-claim, **Description** `""`, Disable Reward Screen Blur, Exclude from Claim All, Ignore reward blocking, Per-Team Reward. Saved as `type: "toast", description`.
- **Choice reward** (picked through a "Select Reward Table" panel with a search box, a list of tables and a red X; creating it needs no window): Title, Icon, Tags, Auto-claim, Disable Reward Screen Blur, Exclude from Claim All (shown **True and greyed out**), Ignore reward blocking, Per-Team Reward, Reward Table (read-only). Saved as `type: "choice", exclude_from_claim_all: true, table_id: 8688099075117598374`. **Note: `table_id` is written as a plain decimal number, not a hex string.** It is the same value as the table's hex id `78925413C9C8C6A6` (id in `reward_tables/78925413c9c8c6a6.json5`). Our importer reads `table_id` as a string (`RewardGranter` normalises a string): to verify in Phase 6.
- Reward context menu for a table reward: Move Left, Move Right, Edit, Quick Edit Properties..., **Edit Reward Table**, Delete, Copy ID.
- A reward that is ready to claim shows a red "!" badge on its icon; the quest in the map and the chapter in the sidebar show the same alert.

## Book "Edit File" sub-editors
- **Visual Presets** (click `[ 3 items ]`): a small window with a green "+" top right, columns **Shape** and **Size**, and one row per preset: the preset name in yellow (`goal`, `info`, `normal`), a Shape drop-down (Hexagon, Gear, Square), the size (`2,00`, `1,00`, `1,00`), a pencil (rename) and a trash can ("Remove" tooltip) on hover. Accept / Cancel.
- **Per-Team Reward by Type** (click `< Edit >`): a list of reward types, each with a tri-state toggle button: Advancement, All Table Reward, Choice Reward, Command, **Currency**, Custom, Item, Loot Reward, Random Reward, Stage, Toast, XP, XP Levels. (Currency exists as a reward type in FTB even though the add-menu does not list it.)

## Things that behave in a way worth copying
- Cancelling a window after changing a value asks **"Discard unsaved changes?"** with Yes / No (vanilla buttons).
- Ctrl+S inside the quest screen saves ("Quests saved on server" appears in the chat).
- A task in the quest window gets a green check once complete; the quest title in the window is the first task's title until a title is set.

## Still not captured
Item reward window, XP / XP Levels reward windows, Random / Loot / All Table windows beyond the shared set above, Custom task and reward windows, Checkmark task window, Choice-reward claim screen, quest states in player mode (locked, unlocked, in progress, completed, claimable), completion toast, Resize and Rotate interactions, Change Group dialog, chapter group creation, quest link creation, Loot Crate enable window, colour picker, description editor rows.

---

# Phase 4, final capture pass (2026-11-21)

Added to the "Rewards and Images" chapter: a Checkmark quest with Random, Loot, All Table, Custom and Item (Apple) rewards; a chapter group **Showcase Group** with the chapter moved into it. Saved with Ctrl+S. Current files: `docs/reference-book-phase4/` (`rewards_and_images.json5`, `chapter_groups.json5`, `chapter_group.json5` lang). Screenshots are now JPEGs in `docs/reference-shots/` (the C: drive filled up with full-size PNGs; see the note at the end).

## Real keys written by FTB (new)
- Random / Loot / All Table / Choice reward: `type: "random" | "loot" | "all_table" | "choice"`, `exclude_from_claim_all: true`, `table_id: <decimal number>` (the same number for all four here: `8688099075117598374` = hex `78925413C9C8C6A6`). So **table rewards always write `exclude_from_claim_all: true`** and `table_id` as a number.
- Custom reward: `{ id, type: "custom" }` only.
- Item reward: `type: "item", item: { id: "minecraft:apple", count: 1 }`.
- Checkmark task: `{ id, type: "checkmark" }`; the quest title typed at creation (`Checkmark Quest`) is the task title, stored in lang as `task.<id>.title`.
- Chapter group: `chapter_groups.json5` = `{ chapter_groups: [ { id: "6AA6190A57CC21C9" } ] }`; the chapter file gets `group: "6AA6190A57CC21C9"`; the name is `chapter_group.<ID>.title` in `lang/en_us/chapter_group.json5`.

## Windows and menus
- **Item reward**: Title, Icon, Tags, Auto-claim, Count `1`, Disable Reward Screen Blur, Exclude from Claim All, Ignore reward blocking, Item, **Only One** `False`, Per-Team Reward, **Random Bonus** `0`.
- **Custom reward**: Title, Icon, Tags, Auto-claim, Disable Reward Screen Blur, Exclude from Claim All, Ignore reward blocking, Per-Team Reward.
- **Random / Loot / All Table reward**: the same window as Choice (Reward Table read-only; Exclude from Claim All greyed).
- Reward context menu (Item): Move Left, Move Right, Edit, Quick Edit Properties..., Delete, Copy ID.
- **Chapter group** menu: Move up, Move down (greyed at the end), Edit, Quick Edit Properties..., Delete, Complete Instantly, Copy ID. A group row shows a triangle, the name and its own green "+" (adds a chapter inside the group); "Change Group" on a chapter opens a **Change Group** panel: a search box, then `Quest Book` (no group) and each group name.
- Chapter menu also has **Reset Progress** (only when there is progress).
- Creating a **Checkmark** task asks only for a name in a small prompt.
- A quest window with more than three rewards **wraps the rewards onto a second row** and grows taller; the "+" button stays after the last icon.
- Reward icons for table rewards show the first entry's icon (an XP bottle here).

## Player mode
- The pencil button (bottom right) toggles editing: a tooltip "Editing Mode for '<name>#<id>' Disabled" appears at the bottom right; the "+" next to the book title, the undo/redo and gear buttons disappear.
- The quest window shows Tasks and Rewards only (no subtitle or Edit button when there is no text). A completed task has a green check; unfinished tasks have none; unclaimed rewards carry a red "!" badge; a table reward shows its table icon.
- **Task tooltips** (hover): Item `Iron Ingot` (plain name when done); Fluid `1 B of Water Bucket` then `0 mB / 1 B [0%]` in green; Stat `Mob Kills`; Kill `Kill: 100x Zombie` then `0 / 100 [0%]` in green.
- **Reward tooltips**: Command `Command: /say Hi, @p!` (command text in red); table reward its table title (`Showcase Table`); Toast `Toast`.
- Map: completed quest = green ring with a green check at the top right (Visit Dimension became complete by itself because the player is in the overworld); a quest with claimable rewards = green ring with a red "!"; incomplete quests have a plain grey circle; the quest map icon is the first task's icon; Task Types / Rewards and Images chapter icons in the sidebar are taken from a quest icon and change as the quests are re-sorted.

## Still not captured
Custom and Checkmark **task** windows (Checkmark only asks a name), XP and XP Levels reward windows, quest links (copy/paste with Alt), Resize/Rotate, the claim screen and the completion toast (nothing was claimed or completed on purpose), loot-crate enable window, description editor rows, the colour picker, a locked quest in player mode (all showcase quests in this chapter are unlocked).

## Note on disk space
The captures filled the C: drive (0 bytes free). I deleted only my own full-size PNG screenshots from this session's scratchpad (about 200 MB; the documented ones are in `docs/reference-shots/`) and switched the driver to JPEG. Another 653 MB of older screenshots sit in the previous session's scratchpad (`...\claude\a5f0abfc2acd3740\...\scratchpad\shots`); I left them alone, you may want to clear them.

---

# Phase 4, extra pass (2026-11-21, after the user cleared disk space)

Disk is fine again (about 6 GB free). New JPEG captures are in `docs/reference-shots/` (the ones named below). The book gained: a Custom-task quest, an XP Levels 5 task quest (with a description and a dependency on the Checkmark quest). Saved with Ctrl+S; `docs/reference-book-phase4/rewards_and_images.json5` and `lang_rewards_and_images.json5` are current.

## New real keys
- XP Levels task: `type: "xp", value: 5, points: false` (number prompt on creation, default `1`).
- Custom task: `{ id, type: "custom" }`; window: Title, Icon, Tags, Optional Task, Disable Completion Toast.
- Dependency: `dependencies: [ "<quest id>" ]` on the dependent quest (here `659F2D1934A18863`).
- Quest description is stored in the lang file under the quest's key as a **list whose entries are JSON strings** (one string per description line): `[ { "text": "Showcase description line", "hover_event": {"value":{"translate":"Hover text"},"action":"show_text"} } ]` (1.21.5+ text-component form: `hover_event`, `action: "show_text"`, `value: {translate: ...}`). The change log shows `Update Translation Quest quest description '...'`.

## Description editor (Edit ▼ -> Description)
Full-width editor titled **Quest Description Editor**. Toolbar, left to right: Accept (green check), **B**, *I*, <u>U</u>, strike-through, colour palette, link, **Q** (Insert Quest Link, `Alt+Q`), **H** (hover tooltip), eraser (clear formatting), page break, image, `{}` (raw JSON), and a green two-arrow button (switch between rich text and raw JSON view). Close (red X). Q and H are greyed until text is selected. Esc or the X on an edited text asks **"Discard Unsaved Changes?"** (vanilla Yes / No).
- **Colour**: the palette button opens a 4x4 grid of the 16 chat colours (black, dark red, dark gray, red / dark blue, purple, blue, pink / dark green, gold, green, yellow / dark aqua, gray, aqua, white) and a rainbow button that opens the **RGB picker**: a brightness slider, a colour wheel, `RGB #ffffff` hex field, a palette drop-down (`Chat Colors`, `Dye Colors`, `Nord Theme`, `Reds`, `Greens`, `Blues`, `Recent Colors`) with a swatch grid, a preview box, accept and cancel.
- **H (hover)**: a small prompt "Hovered tooltip"; applying turns the editor into the raw view showing the exact JSON above.

## Quest windows and states
- **Locked quest** (dependency unfinished): the map icon is faded; hover tooltip is the title then `[Locked: uncompleted dependencies...]` (text cut off in the capture); the quest window shows a **red left arrow** (dependencies), the rewards box says `No rewards` when there are none, and the description is shown. The dependency line is **teal/cyan** in player mode, grey-brown in edit mode, and yellow/striped while one of its quests is open.
- Completing a Checkmark task (click it) shows the **completion toast** at the top right: a dark panel, yellow `Quest completed!` and the quest title below, with the task or quest icon on the left; several toasts stack downward. Advancement quests completed by a claimed Advancement reward gave `Quest completed! / Advancement: Minecraft` toasts.
- A claimed reward shows a green check on its icon and the red "!" disappears; the tooltip then reads `Advancement: Minecraft` with `Minecraft` in yellow.
- **Choice Reward screen** (clicking an unclaimed choice reward): a small panel titled `Choice Reward` with a red X, listing the table's entries as buttons (`XP: +100` with its bottle icon).
- Dependency picker: in the quest Edit window `Dependencies` opens a list editor (green "+", Accept/Cancel); "+" opens **Select Quest Object**: a search box, a red X, and a list with chapters first (orange) and quests below (green when done, yellow-white title colours), each with its icon.

## Side effects this pass
Clicking the second reward icon in the first quest (the Advancement reward) **claimed it**; the vanilla advancement was already earned, but two Advancement-task quests completed. The Checkmark quest was completed by clicking its task. XP quest is now unlocked. Nothing else claimed (the choice screen was closed with its X).

## Still not captured
Quest links, Resize / Rotate interactions, XP and XP Levels reward windows, loot crate window, a quest whose hide-until-visible/complete rules hide it, the search screen, and the key reference/info screens.

## Session 2026-11-22 (driver `tools/window-driver/drive.ps1`, FTB window 870x519, edit mode on)
Created in the user's FTB book: chapter `Quest Options` with one Checkmark quest (`Options Test`). Screens in `tools/window-driver/shots/` (`m1` map menu, `e1` Edit menu, `q1`-`q4` Quest Properties).
- **Quest window Edit menu** (with shortcut keys shown): Title [T], Subtitle [S], Description [D], Add Text Line [L], Add Page Break [P], Add Image [I], Quest Properties [Q]. Ours has no per-entry shortcut keys (difference).
- **Quest Properties rows, in order.** Section *Quest*: Title, Icon (None), Tags (list), Description (list), Subtitle, Disable Completion Toast. *Quest -> Appearance*: Icon Scaling (1), Min Opened Quest Window Width (0), Shape (Default), Size (1), Visual Preset (None), X, Y. *Quest -> Visibility*: Hide Details Until Startable, Hide Lock Icon, Hide Quest Until Dependencies Complete, Hide Quest Until Dependencies Visible, Hide Text Until Quest is Completed, Invisible Until Completed, Invisible until X tasks are completed (greyed while Invisible Until Completed is false). *Quest -> Dependencies*: Dependencies, Dependency Requirement (All Completed), Hide Dependency Lines, Hide Dependent Lines, Max Completable Dependents (0), Min Required Dependencies (0). *Quest -> Misc*: Disable JEI Recipe, Guide Page, Ignore reward blocking, Optional Quest, Progression Mode, Quest Repeat Cooldown (0), Repeatable Quest, Sequential Task Completion.
- Not in the window although the lang file names them: Exclusive Quest Branch, Tasks Ignore Dependencies.
- Visual Preset cycles on click: None -> normal -> (the book's `presets` names). Icon Scaling is a decimal box (shows `1,50`, locale comma). Quests have **Tags** (ours: only chapters).
- Quest Edit/Quest Properties differences found in ours to fix: Quest Tags, Icon Scaling, Min Opened Quest Window Width per quest, Visual Preset, Max Completable Dependents, Guide Page.
- FTB writes quest edits to the config folder by itself within about a minute (no need to leave the world).
- **Keys FTB saved** (`chapters/quest_options.json5`): `icon_scale: 1.5`, `preset: "normal"`, `max_completable_dependents: 3`, `guide_page: "guide_x"`, `hide_details_until_startable: false` (a Default tri-state is absent, False is written). `size: 1.0` is always written. A task title goes to lang as `task.<id>.title`.
- **Preset rule (measured)**: with Visual Preset `goal` (book preset hexagon, size 2.0) the quest is drawn as the preset's hexagon at the preset's size even when the quest itself has Shape Circle and Size 3 (`d2`, `e2` shots). So a preset overrides the quest's own shape and size. Preset `normal` (square, 1.0) drew a square instead of the book's default circle (`b1`). Icon Scaling 1.5 draws the icon larger than the hexagon.
- Open the book in FTB with key **P** (the user bound it).

### Stage reward and quest tags (FTB 26.1.2, captured and saved 2026-11-22)
Window rows of a Stage reward (maximized window, `g2`): Title, Icon, Tags, Auto-claim (default `Enabled, Invisible`), Disable Reward Screen Blur, Exclude from Claim All, Ignore reward blocking (greyed, True), Per-Team Reward, Remove, Stage. Creating one asks for the stage name in a small box headed `Stage`. The reward list has no Currency entry.
Keys FTB saved: quest `tags: [ "showcase_tag" ]`; reward `{ type: "gamestage", team_reward: false, auto: "invisible", ignore_reward_blocking: true, stage: "rewardStage", remove: true }` (Per-Team Reward clicked once = `false`; Remove True = `remove: true`). Tags are entered in a `Tags` list window with a green `+` ([Ins]) and Accept/Cancel.
Ours (compiled, unit test `tagsAndStageRewardFtbSavedAreKept`, not seen in game): Stage reward type enabled in the menu with the same prompt, saved with the same keys and defaults, granted as a player tag (added, or removed when `remove`); Stage and Remove rows in the reward window; quest Tags row (comma separated) in Quest Properties.

### Chapter Edit window (FTB 26.1.2, `tools/window-driver/shots/k2.png`, 2026-11-22)
Title `<chapter> [Chapter]`. Section *Chapter*: Title, Icon, Tags (list), Subtitle (list), Disable Completion Toast. *Chapter -> Appearance*: Default Min Opened Quest Window Width, Default Quest Shape, Default Quest Size, Default Visual Preset. *Chapter -> Visibility*: Always Invisible, Default 'Hide Dependency Lines', Hide Quest Details Until Startable, Hide Quests Until Dependencies Complete, Hide Quests Until Dependencies Visible, Hide Text Until Quest is Completed. *Chapter -> Misc*: Autofocus Quest ID, Default 'Progression Mode', Default 'Repeatable Quest', Default Item Task 'Consume Items', Sequential Task Completion. Chapter right-click menu: `"<title>"`, Move (up), Move (down), Change Group, Edit, Quick Edit Properties..., Delete, Complete Instantly, Copy ID.
