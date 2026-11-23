# Parity audit: Reflib Quests against FTB Quests

Reference: the language files of FTB Quests 2101.x (`ftb-quests-neoforge-26.1.2.10.jar`, 766 strings) and FTB Library (113 strings),
read from the jar as resources (no code), plus the real FTB Quests 1.12 jar in the dev game (it has far fewer strings), plus the
screenshots and video notes the user supplied. Our side: the string literals and layouts in `reflibquests/.../client`.

"Wording" below is exact text from the FTB language file. Nothing here is a guess about layout or colours: those need a screenshot
(see the last section).

## 1. Wording that differs (cheap, exact fixes)

| Where | Ours | FTB |
|---|---|---|
| Chapter properties | "Default Progression Mode" | "Default 'Progression Mode'" |
| Chapter properties | "Default Repeatable Quest" | "Default 'Repeatable Quest'" |
| Chapter properties | "Default Hide Dependency Lines" | "Default 'Hide Dependency Lines'" |
| Reward properties | "Auto Claim" | "Auto-claim" |
| Auto-claim choices | "No Toast" / "Invisible" (fixed in this pass) | "Enabled, No Toast" / "Enabled, Invisible" |
| Section headings | "Quest → Appearance" etc. | the same: FTB's quest settings window shows "Quest", "Quest → Appearance", "Quest → Visibility", "Quest → Dependencies", "Quest → Misc" (screenshot), so no change needed |
| Delete item in menus | "Delete Quest", "Delete Chapter", "Delete task", "Delete reward" | "Delete" (quest menu in the screenshots) |
| Quest menu | has all of FTB's entries except "Set as Autofocused Quest" and "Edit Bézier Control Points"; extra: "Add Dependency" (kept; the user decided on 2026-11-23 to keep it as an extra feature; it is how dependencies are linked on the map) and a "Really delete this quest?" step | FTB: Edit, Move, Quick Edit Properties..., Delete, Reset Progress, Complete Instantly, Set as Autofocused Quest, Edit Bézier Control Points, Copy ID |
| Chapter menu | "Chapter Properties", "Move Up/Down", "Move to Group...", "Delete Chapter" | FTB strings: "Move", "Quick Edit Properties...", "Reset Progress", "Complete Instantly", "Change Group", "Move Left", "Move Right", "Copy ID" |
| Group menu | "Rename", "Move Up/Down", "Delete Group" | not seen in FTB; its strings: "Change Group", "Create Chapter Group" (video) |
| Add reward menu | "Item (the item you hold)", "10 XP", "1 XP level" | FTB opens "Select Reward Type": Item, Command, Custom, XP, XP Levels, Advancement, Toast, Stage, Choice/Random/Loot/All Table |
| Add task menu | "Checkmark", "Item (the item you hold)", "XP level 5" | FTB opens "Select Task Type" (Visit Dimension, Location, Visit Biome, Find Structure, Advancement, Stat, Checkmark, XP Levels, Item, Fluid, Forge Energy, Custom, Kill, Observation...) |
| Reward table window | "Back", "Add", "Close", our own hints | FTB: "Edit Reward Tables", Accept / Cancel (not Close) |
| Edit-mode subtitle | "[No Subtitle]" | no equivalent string in the language file (video shows it; keep) |
| Key reference | see section 3 | |

Already identical to FTB: all Player Preferences labels (the screen was built from them), "Quick Edit Properties...", "Reset Progress",
"Copy ID", "Select Rewards", "Claim All", "Claim XP", "Select Quest Object", "Add Page Break", "Add Image", "Undo", "Redo",
"Items will be auto-detected", "Click to view valid items", "Click to view recipe(s)", "View Dependencies", "View Dependants",
"No Dependencies", "No Dependants", quest property labels in Quest Properties.

## 2. FTB features with a string in the language file that we lack

Quest menu / editor:
- "Complete Instantly" (we have it), **"Set as Autofocused Quest" / "Clear Autofocused Quest"**, **"Edit Bézier Control Points" / "Clear..."**.
- "Move Left" / "Move Right" (chapters), "Change Group", "Move to another chapter" (quests), "Create Chapter Group".
- Selection: "Add selected as dependencies", "Remove selected from dependencies", "Add as dependency to selected", "Remove as dependency from selected",
  "Change Size for all...", "Add reward to all...", "Clear rewards from all".
- Copy and paste: "Paste Quest", "Paste Quest (no dependencies)", "Paste Quest Link", "Paste Image", "Paste Task", "Edit Linked Quest",
  "Go to Linked Quest", "View Quest Link(s)".
- Pictures: "Adjust Width / Height to Aspect Ratio", resize and rotate handles with their hints, image properties (Alignment,
  Fit to panel width, Aspect Ratio Hint, Image Tint, Image Alpha, Image Ordering, Hover Text, Click Action with six types, Lock Position,
  "Only visible in Editing Mode", Rotate and offset from corner, text on image with shadow, inset, H/V alignment).
- Tasks: "Use Task Icon as Quest Icon", "Convert line to Json", "Grab Copy of Item", "Hide Valid Items", "Only Detect From Crafting",
  NBT Mode, Ignore Damage, "Consume Items" per task, task types Fluid, Forge Energy, Stat, Observation (timer), Interaction, Stage.
- Rewards: "Per-Team Reward", "Exclude from Claim All", "Random Bonus", "Disable Reward Screen Blur", Command "Run with Permission Level",
  "Silent", "Feedback Toast"; Advancement + Criterion; Toast description; Stage.
- Book settings ("Edit File"): Emergency Items (+ Cooldown), Defaults (Per-Team Reward, Task Consume Items, Reward Auto-claim, Quest Shape,
  Disable JEI, Visual Preset), Entities Drop Loot Crates, Loot Crate Drop Empty Weight, Disable GUI, Full Screen Quest View, **Grid Scale**,
  Lock Message, Progression Mode, "Quest Screen Pauses SSP Game", "Item Auto-Detection Minimum Interval", "Drop FTB Quests Book on Death",
  "Hide Quests in excluded questlines", Fallback Locale, "Suppress all Reward Autoclaiming", Visual Presets.
- Chapter properties: Subtitle, Group, Default Quest Size, Default Visual Preset, Default Min Opened Quest Window Width,
  Hide Text Until Quest is Completed, Autofocus Quest ID, Default Item Task 'Consume Items'.
- Quest properties: Icon Scaling, Visual Preset, Min Opened Quest Window Width, Hide Lock Icon, Hide Text Until Quest is Completed,
  Exclusive Quest Branch, Tasks Ignore Dependencies, Max Completable Dependents, Guide Page, Quest Repeat Cooldown.
- Reward tables: "Hide Tooltip", "Use Title", "Enable / Disable Loot Crate" (with ID, Item Name, Color, Glow, three drop weights), "Set Weight",
  "...and %d more", "Nothing".
- Misc: "Reload Theme", "Open Wiki", "Download Quest Files", "Export Spreadsheet", "Change Progress" (Reset / Complete, each with "+Dependencies"),
  "Reset progress for your team?" confirmation, "Discard Unsaved Changes?", "Looping dependencies!", "Times completed", "Requires" / "Required by".

## 3. Controls: our Key Reference is out of date

Ours says "Left Mouse: Drag = Move a quest", "Right Mouse = Select a quest", "Right Mouse on a task or reward = Delete it". Those are wrong now.
FTB's reference (from the language file) is:

General: zoom out / in / reset, next / previous chapter, recenter, search, extended quest info, move up / down / left / right,
Left Mouse = View Quest Details, Mouse Wheel = scroll up/down, Shift + Wheel = scroll left/right, Ctrl + Wheel = zoom,
Backspace = go to last-viewed quest, Player Preferences.

Editor: Ctrl + Left Mouse = Toggle Selection; Left Alt + Left Mouse = Properties Editor; Right Alt + Left Mouse = Copy Quest;
Middle Mouse = Move Selected Quest(s); Right Mouse = Context Menu; Middle Mouse drag = Select Quests in Drag Box;
Ctrl + Middle drag = Toggle Selection in Box; Select All / Select None; Delete; Force-delete; Toggle Crosshairs; Copy; Paste;
Shift = Paste Quest (no dependencies); Alt = Paste as Quest Link; Force-complete Hovered; Force-reset Hovered; Save Quests Now.
Info lines: Ctrl + A select all, Ctrl + D / Esc deselect all, Ctrl + Arrow keys move selected.

Quest view panel: Edit Title, Edit Subtitle, Edit Description, Insert Page Break, Insert Line, Insert Image, Edit Quest Properties.
Keybind names: "Open Quests", "Pinned Quest Tracker Visibility", "Open Reward Table Editor", "Reload Theme", "Save Quests Now".

What we do differently today: Alt + Left Mouse (properties), Right Alt + Left Mouse (copy), Ctrl + Left Mouse (selection), drag box, Shift+Wheel,
Backspace history, Ctrl + A / D, Ctrl + arrow keys, most of the editor key list are not implemented.

## 4. Behaviour known to differ

- Selection (Ctrl click, drag box, move many, bulk edits), copy and paste: not implemented.
- Quest links (second placement of a quest) not drawn.
- Curved (Bézier) dependency lines and their editor not implemented.
- Autofocus quest not implemented.
- Task tooltip: FTB puts the count in the name ("32x Iron Ingot"), a progress line, and "Click to view recipe(s)". Ours now does the same, unverified.
- FTB's Select Rewards window and reward table windows use Accept / Cancel; ours apply and close.
- Backspace as "go back to the previous quest", Shift + Wheel horizontal scroll: not implemented.
- The history panel and status strip follow the screenshots; their exact sizes and colours were estimated, not measured against FTB.

## 5. What cannot be audited without a reference screenshot from FTB

Pixel layout and colours: quest window size and padding, section header height, scroll bar, list rows, button sizes, tooltip padding,
menu width and row height, toast layout, chapter panel width. For any of these send an FTB screenshot taken at the same GUI scale as ours
and the measurements will be taken from it.
