# Phase 6: importing the FTB Quests 26.1.2 reference book into Reflib Quests

Date 2026-11-21. Method: a copy of the live 26.1.2 quest folder (`...\26.1.2\.minecraft\config\ftbquests\quests`, 5 chapters, 1 chapter group, 1 reward table, 40 quests) was read with the project's real importer `UpstreamImporter` through two developer tools run by Gradle (no game was launched, nothing in the user's game or book was touched):

- `RealPackReportTest` (existing): counts, round trip.
- `ReferenceBookAuditTest` (new, `reflibquests/src/test/.../io/`): per-chapter, per-task and per-reward dump of what survives, and whether a table reward finds its table. Run with `./gradlew.bat :reflibquests:test --tests '*ReferenceBookAuditTest*' -Ppack=<folder> -i`.

This is an **import** check (data model and parsing). Nothing here was seen in the Reflib Quests window yet; the in-game side is Phase 7.

## Result in numbers
Imported in 46 ms, **0 importer problems**: chapters 5, groups 1, quests 40, tasks 43, rewards 10, reward tables 1, translation keys 32, images 1, quest links 0. Task types: checkmark 22, item 1, fluid 1, stat 2, kill 2, location 2, dimension 2, advancement 2, observation 2, biome 2, structure 1, custom 2, xp 2. Reward types: command, advancement, choice, toast, random, loot, all_table, custom, item (1 each). Shapes: default 33, diamond 1, gear 1, hexagon 2, octagon 1, pentagon 1, rsquare 1.
**Round trip**: export to a temporary folder, re-import: 0 problems, book identical, translations identical. So nothing is lost by a save and reload of what the importer kept.

## What survives (checked against the list in the assignment)
| Item | Result |
|---|---|
| Every chapter, quest, task, reward imports | Yes (counts above equal the files) |
| Task and reward definitions | Yes, type plus every key kept in `params` exactly as read (`Task.params`, `Reward.params`) |
| Dependencies | Yes (ids kept, e.g. `67FE2454219D8918 -> 659F2D1934A18863`; the "Dependencies" chapter links are present) |
| Position, size, shape | Yes (`Quest.x/y/size/shape`) |
| Visibility keys | Read into the model (`Tristate` fields); the whole quest compound is also kept in `Quest.raw`, so unknown keys are preserved |
| Chapter group | Yes (`chapter_groups.json5`, chapter `group` key kept in `Chapter.raw`) |
| Image | Yes, all 7 keys kept in `ChapterImage.raw` (x, y, width, height, rotation, image, id) |
| Titles, subtitles, task titles, reward table title | Yes from lang (`chapter.`, `quest.`, `task.`, `reward_table.`, `chapter_group.` keys) |
| Book settings | Yes (21 keys incl. `presets`, `grid_scale`, `default_quest_shape`); `presets` is kept as data only (no behaviour) |
| Unsupported data discarded? | No: `raw` fields keep it; the importer logs no problem for it, see items 3 and 4 |

## Incompatibilities found (each with the layer at fault)
1. **Table rewards cannot find their table.** FTB 26.1.2 writes `table_id` as a **decimal number** (`8688099075117598374`); the importer keeps it as a long (`DataNode.Lng`), and both `RewardGranter` (line ~102) and `BookEditor` read it with `getString("table_id", "")`, which returns the default for a number. Result for choice, random, loot and all_table rewards: `tableFound=false` for all 4 in the book, so they cannot give anything. The table file itself is keyed by the hex id (`78925413C9C8C6A6` = 8688099075117598374). **Layer: importer (id normalisation) and data model (two spellings of the same id).** Priority P0.
2. **Item task count is ignored.** 26.1.2 stores `item: { id, count }`; `Task.maxProgress` reads a top-level `count`, so every item task needs 1 item whatever the book says. **Layer: data model.** (Legacy and older books use the top-level form, so both must be accepted.)
3. **Fluid and Stat tasks import but are inert.** They are not in `QuestServer.SUPPORTED_TASKS`; the importer reports 0 problems, only the server's `/rquests problems` lists them at run time. **Layer: missing feature implementation** (and the importer should say so).
4. **Description hover events are dropped.** The 26.1.2 description is a list of JSON text-component strings using the 1.21.5 form: `{ "text": ..., "hover_event": {...}, "action": "show_text" }`. `RichText` renders the text (`Showcase description line`) but has no hover support and only reads the older `clickEvent` form, not `click_event`. **Layer: client rendering / `RichText`.** The editor side (Quest, Hover buttons, colour picker) is also missing.
5. **Reward and task windows that FTB has and ours lacks** are not an import problem but explain why imported keys cannot be edited: Fluid (`fluid: { fluid, amount }`), Stat (`stat`), Interaction, Stage, Forge Energy, Currency; reward Per-Team, Exclude from Claim All, Disable Reward Screen Blur, Feedback Toast, Run with Permission Level, Only One, Random Bonus keys are kept raw; whether the server honours each is Phase 1's open "Unverified".
6. **Book `presets`** (normal, goal, info), `visual_preset` on quests, chapter `default_visual_preset`: stored, no behaviour.
7. **Reward `auto-claim` key name** is not yet captured from FTB (the windows show "Auto-claim `Default`" but nothing in the saved file used it); our model reads `auto`. Needs a capture with the value changed.
8. **Not checked yet (needs the game)**: rendering of each shape and size, the check/lock/alert states, the dependency line colours (teal for unlocked in FTB), the quest window layout (rewards wrap to a second row when more than three), the chapter group in the side panel, the image, tooltips and toasts. Those are Phase 7.

## Not covered by the book (the importer was not exercised on)
Quest links (none created), `dep_control_pts` Bezier points, chapter `subtitle` lists, loot crates, `emergency_items`, per-type `Per-Team Reward by Type`, several task/reward windows listed as "still not captured" in `phase4-ftb-ui-capture.md`.

## Next
Phase 7 (compare in game) needs ours running with this book loaded: the dev instance reads `reflibquests\run\client\config\reflibquests`. The importer path is proven; the in-game comparison has not been done. Per the rules, no fix has been made yet: items 1-4 go into the consolidated difference list as P0/P1 (table id, item count) and P1/P2 (hover events, inert tasks).
