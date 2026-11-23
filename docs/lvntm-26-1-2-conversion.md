# LvNTM quest book: 1.12.2 pack to FTB Quests 26.1.2

Done 2026-11-23. The user's LvNTM questline (FTB Quests 1.12.2, 1.12.2 Nuclear Tech / HBM) was converted to the file format FTB Quests 26.1.2 saves, with item ids translated to the 26.1.2 Nuclear Tech (`hbm-neoforge-1.0.6-26.1.jar`).

## Result

| | 1.12.2 pack | converted book |
|---|---|---|
| chapters | 24 | 24 (+1 group) |
| quests | 1536 | 1536 |
| tasks | 2169 | 2169 |
| rewards | 1124 quest rewards + table entries | 1483 (incl. table entries) |
| reward tables | 7 | 7 |

Loaded by the real FTB Quests 26.1.2 with no warnings or errors ("Loaded 2 chapter groups, 24 chapters, 1536 quests, 7 reward tables"). Items, fluids, tag filters, descriptions, rewards and chapter groups were seen in the game (screenshots taken through the helper). Not all 1536 quests were opened by hand; the whole book was checked by script instead (below).

## How it is produced (reusable)

```
./gradlew.bat :reflibquests:test --tests "*ConvertLegacyPackTool*" "-Ppack=<old pack folder>" "-Pout=<new empty folder>" "-Preport=<report.md>"
```

1. `LegacyImporter.loadWide` reads the old layout (`file.snbt`, `chapters/<id>/*.snbt`, `reward_tables`). `loadWide` keeps ids in the 16-digit upper-case form FTB Quests 26.1.2 needs: its language keys (`chapter.<16 hex>.title`) do not match shorter ids, which showed every title as "Unnamed" before this was found.
2. `LegacyModernizer` (new) rewrites ids, metadata, fluids, entities, dimensions, tag filters and settings using the table `src/main/resources/ntm/legacy_1_12_2_to_26_1_2.json`, and records every decision.
3. `UpstreamExporter` writes `data.json5`, `chapter_groups.json5`, `chapters/`, `lang/en_us/`, `reward_tables/`.

The table is data, built by `reflibquests/tools/ntm/build_map.py` from files only (nothing decompiled):

- 1.12.2 side: the user's own CraftDumper CSV (`LvNTM/minecraft/craftdumper/item_stacks_2026-07-15_19-18-18.csv`: id, metadata, display name, translation key, model name) and the NTM-CE 1.12.2 `en_us.lang`.
- 26.1.2 side: `assets/hbm/items`, `assets/hbm/lang/en_us.json`, `data/c|hbm|minecraft/tags`, `data/hbm/fluid_property` of the jar.
- Hand decisions with a reason each: `tools/ntm/overrides.json` (items), `tools/ntm/static.json` (vanilla flattening, entities, ore-dictionary names, image paths).

Matching order: same id and name; id derived from the translation key / model name (`item.circuit.pcb` to `circuit_pcb`); material families by name (`Cast Aluminium Plate` to `plate_cast_aluminium`); unique display name; then the override file.

## What the 26.1.2 mod does differently (found, and handled)

- 26.1.2 NTM is a port of the same NTM-CE, so ids mostly match: of the 1,158 distinct `hbm:` ids in the pack (1,604 id-plus-metadata stacks), 1,079 exist unchanged in 26.1.2; metadata variants became separate items (`hbm:circuit@3` to `hbm:circuit_pcb`, `hbm:bolt@30` to `hbm:bolt_steel`).
- Fluid containers carry the fluid in components: `hbm:fluid_tank`, `hbm:fluid_barrel`, `hbm:fluid_icon` with `hbm:fluid` and `hbm:fluid_amount` (tooltip showed "Coolant" for `hbm:coolant`, so the key is right). 428 table entries.
- Foundry scraps carry the material in `hbm:material` (confirmed with `/give`: "Arsenic Scraps").
- Copper: the 26.1.2 mod uses the vanilla copper ingot/block/ore (its tag `c:ingots/copper` lists `minecraft:copper_ingot`).
- Ore-dictionary filters became `tag(c:...)` smart filters (`ingotX` to `c:ingots/x`, `plateTripleX` to `c:plates_cast/x`, ...).
- Chicago Pile, satellites, apples, plants: variants split into separate items.

## Substitutions and gaps (review these)

Full lists with first quest where each occurs: `docs/lvntm-26-1-2-conversion-report.md` (per decision kind) and `docs/lvntm-26-1-2-table-report.md` (every pack stack that was not simply "same"). Name mentions in quest text: `docs/lvntm-26-1-2-text-check.md` (20 renamed items, e.g. Combination Oven is now Combination Furnace).

- **game-checked (15)**: 14 satellite metas and `item_secret@2`, read from the 1.12.2 game with `/give` (names in the table notes).
- **inferred (27)**: order of variants taken from the quest structure or assumed, not read from a file: Chicago Pile rods 0-8, apple ingot/block order, Mustard Willow stages, dead plant, exhaust pipe, universal duct, guide book. Worth a glance.
- **substitute / substitute-weak (78 uses)**: closest real item, e.g. Size 15 MIRV warhead to `warhead_mirv`, thermonuclear to nuclear, First Fritz Cola to Fritz Cola, mob pictures to spawn eggs, Lung Diagnostic to Digamma Diagnostic, Spinny Light to Lantern, Xen Capsule to Artificial Xen Crystal, per-caliber ammo filters to the `hbm:ammo` tag.
- **ore-dictionary filters without a tag**: 21 of them became `or(item(...))` filters: members taken from the 1.12.2 dump (heat boxes, iron anvils, used cloth), prefix families (bobblehead_*, drillbit_*), and the bedrock-ore processing stages by naming pattern (`inferred`: Dissolved read as solvent, Cleaned as rad, Seared as arc; read off item ids, not tested). The estradiol container accepts any universal fluid tank (weak).
- **unresolved (8 tasks)**: Higher/Lower Weight Primary Fraction, Separated Ore, deco block, and similar: manual check-off tasks keeping the quest title.
- **Refined Storage (55 stacks)**: mapped to Refined Storage 2.x ids and checked against `refinedstorage-neoforge-3.2.1.jar`, the version now in the 26.1.2 instance (all mapped ids exist in its `assets/refinedstorage/items`). Metadata flattened (Grid 0-3, Storage/Disk/Part 0-3 to `1k..64k_*`, Processor 3-5, Upgrade 0-9, controller, cores), renames (Crafter to autocrafter, Crafting Monitor to autocrafting_monitor), substitutions (Disk Manipulator to disk_interface, Fluid Grid to grid, 64k Fluid Part to 64b_fluid_storage_part, Reader/Writer to relay, weak). Seen in game: the Refined Storage chapter shows real icons and names (Grid, Network Transmitter). Not checked: the fortune upgrade order (metas 7-9 assumed I-III) and that each task item matches the intended RS item.
- Chapter images: click links became `click_action: {type: "open_uri", data: url}` (keys read from a file FTB saved after I set one in its editor); hover text is the image title in the language file. Click and hover were not tried in game.
- Chapter `7079924d` and the `.py` helpers in `chapters/` are not converted (FTB Quests 1.12.2 ignores them too).
- Chapter parent/child nesting became a group named "Checklists" that also contains the Checklists chapter.
- Fluid icons have no colour tint in the quest map (the tint comes from custom model data whose key was not read).
- Reward and task `title`s, descriptions with `§` colour codes and `&` formats were kept as written; not every description was checked in game.
- Command rewards `/ftbquests change_progress complete @p <id>` were rewritten to `/ftbquests change_progress @p complete <id>`, the syntax of the 26.x command; not run in game.

## Checks run

- `validate` script over the written files: every `minecraft:` and `hbm:` item id exists in the 26.1.2 client jar / NTM jar; every `tag(...)` exists; only the 57 Refined Storage ids are not found.
- In the 26.1.2 game: book loads, titles, icons, fluid tooltips, tag filter task, rewards, descriptions, groups.

## What was changed on the machine

- `C:\Users\Lev\AppData\Roaming\PolyMC\instances\26.1.2\.minecraft\config\ftbquests.backup-20261123-before-lvntm` is a full copy of the old `config/ftbquests` (the showcase book).
- `config\ftbquests\quests` now holds the converted book; the showcase book is also in `config\ftbquests\quests.replaced-by-lvntm-20261123`.
- `mods\reflibdebug-neoforge-0.2.0.jar` added (the debug helper; delete it when done).
- `resourcepacks\lvimages_pack` added and **enabled in the running game** (so `options.txt` now lists `file/lvimages_pack`): images 1-9 from `LvNTM\minecraft
esourcepacks\Levviata's Images` (the `lvimage` mod jar only holds transparent 32x32 placeholders), plus `ntm_icon.png`, `wojaksona.png`, `gas_centrifuge.png` from your three "Barrier / Structure Void / Command Block Conditional" packs. Chapter images use `lvimages:textures/...` for these so vanilla barrier/structure void textures stay untouched. Verified in game: Automation (YouTube logo) and LvNTM (NTM icon, Wojaksona) images render.
- FTB Quests re-saved its in-memory (older) book over the folder at one point; always copy the new book, then `/ftbquests reload`, and avoid resource reloads in between.
- A `hbm:scraps` item (and test `/give`s) went into the 26.1.2 "New World" inventory.
- In the 1.12.2 LvNTM game (see the report text) the world "Sandbox 4" was opened to read item names: Forge's "missing registry entries" prompt appeared and the world loaded after it, which removes those entries; Forge made `saves\Sandbox 4-20261123-044308.zip` first. The test `/give`s were cleared again, but "Quest completed" toasts fired for satellite quests in that world.

## Not done

- Refined Storage ids not translated (mod absent); lvimages resource pack not enabled; image click links.
- Quests were not played through (task completion, reward claiming) in 26.1.2.
- Text about mechanics (recipes, GUIs, power values) in descriptions was not rewritten.
