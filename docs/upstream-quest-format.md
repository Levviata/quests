# Upstream quest-file format notes (for the importer)

Written 2026-11-19 in our own words from a schema-level reading of the upstream quest mod's serialization code, as approved by the user (see `docs/phase4-ftb-quests-plan.md`, Section 8). Only facts needed for interoperability are recorded: file layout, field names, value types, defaults and meaning. No upstream code, comments or messages are reproduced, and our parser/writer is independent. Version noted: the format version constant read was **13** (current latest). Older packs use an older text syntax (see 7).

Legend: `T` = value type; `def` = default when absent; `tri` = three-state (absent = "use the parent default", otherwise true/false).

## 1. Layout of a quest folder (`config/<mod>/quests/` upstream)

```
data.<ext>              book-wide settings + "version"
chapter_groups.<ext>    { chapter_groups: [ { id, ...group data } ] }   (default group is implicit, not listed)
chapters/<name>.<ext>   one file per chapter, contains its quests, links and images
reward_tables/<name>.<ext>
lang/<locale>/*.<ext>   translation tables (titles, subtitles, descriptions)
```
`<ext>` is `json5` in the latest version. Files are read as JSON5 (comments, unquoted keys, trailing commas allowed). Chapter file names are lower-case; the chapter's `filename` is the file stem. Progress is **not** in this folder (it lives in the world save, per team) and is out of scope for import.

## 2. Ids

Every object (book, group, chapter, quest, quest link, task, reward, reward table, image) has a 64-bit id written as **16 upper-case hex digits** (a leading `#` is tolerated, `-` or empty = none). Our model stores ids as strings, so these fit unchanged; we preserve them on export.

## 3. data file (book settings), selected fields
`version` (int); `default_reward_team` (bool); `default_reward_team_by_type` (map type to bool); `default_consume_items` (bool); `default_autoclaim_rewards` (`disabled|enabled|no_toast|invisible`); `default_quest_shape` (string, `default` = empty); `default_quest_disable_jei` (bool); `emergency_items` (list of item stacks), `emergency_items_cooldown` (int); `drop_loot_crates` (bool), `loot_crate_no_drop` (weights); `disable_gui`, `pause_game`, `show_lock_icons`, `drop_book_on_death`, `hide_excluded_quests`, `verify_on_load`, `suppress_all_autoclaiming` (bool); `grid_scale` (double, def 0.5); `lock_message` (string); `progression_mode` (`linear|flexible`, def linear); `detection_delay` (int ticks, def 20); `fallback_locale` (string, def `en_us`); `presets` (visual presets object); `preset` (string).

## 4. Chapter file
Top level: `id`, `group` (group id or `""`), `order_index` (int), then chapter data, then arrays `quests`, `quest_links`, `images`.
Chapter data: `filename` (string, required); `icon` (item stack); `tags` (list of strings); `always_invisible` (bool); `default_quest_shape` (string); `default_quest_size` (double, def 1); `default_hide_dependency_lines` (bool); `default_min_width` (int); `progression_mode` (`default|linear|flexible`); `consume_items` (tri); `hide_quest_details_until_startable`, `hide_quest_until_deps_visible`, `hide_quest_until_deps_complete`, `hide_text_until_complete`, `default_repeatable_quest`, `require_sequential_tasks` (bool); `preset` (string); `autofocus_id` (hex id string).

## 5. Quest (element of `quests`)
Required: `id`, `x` (double), `y` (double). Optional (def): `icon` (item stack), `tags`, `shape` (string, `default`=empty), `guide_page` (string), `size` (double, def 1), `icon_scale` (double, def 1), `min_width` (int), `optional` (false), `invisible` (false, = invisible until completed), `invisible_until_tasks` (int, 0), `min_required_dependencies` (int, 0), `max_completable_dependents` (int, 0), `repeat_cooldown` (int, 0; unit ticks in game logic), `ignore_reward_blocking` (false), `hide_lock_icon` (false), `hide_dependent_lines` (false), `preset` (string), `progression_mode` (`default|linear|flexible`), `dependency_requirement` (`all_completed` def | `one_completed` | `all_started` | `one_started`), `dependencies` (list of hex ids), `dep_control_pts` (map for drawn line shapes).
Tri-state keys: `hide_dependency_lines`, `hide_until_deps_visible`, `hide_until_deps_complete`, `hide_text_until_complete`, `hide_details_until_startable`, `require_sequential_tasks`, `can_repeat`, `disable_recipe_mod`.
Arrays: `tasks` (objects with `id`, `type`, type data) and `rewards` (objects with `id`, `type`, type data).

**Quest link:** `id`, `linked_quest` (hex id), `x`, `y`, `shape`, `size`. **Chapter image:** `id`, `x`, `y`, `width`, `height`, `rotation`, `image` (resource or url string), optional `color` (int rgb), `alpha` (def 255), `order`, `click_action`, `dev` (editors only), `corner`, `dependency` (hex id), `position_locked`, `text_on_image`, `text_shadow`, `text_inset`, `text_h_align`, `text_v_align`.

## 6. Tasks and rewards
Common: task has optional `optional_task` (bool). Reward has optional `team_reward` (tri), `auto` (`default|disabled|enabled|no_toast|invisible`), `exclude_from_claim_all`, `ignore_reward_blocking`, `disable_reward_screen_blur` (bool). `tags` on any object.

Task `type` strings (namespace omitted for the mod's own): `item`, `custom`, `xp`, `dimension`, `stat`, `kill`, `location`, `checkmark`, `advancement`, `observation`, `biome`, `structure`, `gamestage`, `fluid`, `interaction`; also `energy` exists in the latest source.

| Task | Fields |
|---|---|
| item | `item` (stack), `count` (long, def 1), `consume_items` (tri), `only_from_crafting` (tri), `match_components` (enum), `task_screen_only` (bool) |
| checkmark, custom | none |
| xp | `value` (long), `points` (bool: points vs levels) |
| kill | `entity` (id string), `entityTypeTag` (string), `value` (long), `custom_name`, `nbt_filter` |
| advancement | `advancement` (id), `criterion` (string) |
| dimension | `dimension` (id) |
| location | `dimension`, `ignore_dimension` (bool), `position` [x,y,z], `size` [w,h,d] |
| stat | `stat` (id), `value` (int) |
| observation | `timer` (long), `observation_type` (enum), `to_observe` (string) |
| biome / structure | `biome` / `structure` (string) |
| fluid | `fluid` (fluid stack) |
| energy | `value`, `max_input` (long) |
| gamestage | `stage` (string), `team_stage` (bool) |
| interaction | `interaction_type` (enum), `to_interact` (string) |

Reward `type` strings: `item` (default), `choice`, `all_table`, `random`, `loot`, `command`, `custom`, `xp`, `xp_levels`, `advancement`, `toast`, `gamestage`, `currency`.

| Reward | Fields |
|---|---|
| item | `item` (stack), `count` (int, def 1), `random_bonus` (int), `only_one` (bool) |
| choice, all_table, random, loot | reference a reward table: `table_id` (hex id), optional inline `table_data` |
| command | `command` (string), `permission_level` (0..4), `silent` (bool), `feedback_message` (string) |
| xp / xp_levels | `xp` / `xp_levels` (int) |
| advancement | `advancement` (id), `criterion` |
| toast | `description` (string) |
| gamestage | `stage` (string), `remove` (bool) |
| currency | `amount` (long) |

**Reward table file:** `id`, `order_index`, `empty_weight` (float), `loot_size` (int), `hide_tooltip` (bool, def true), `use_title` (bool, def true), `rewards` (array of reward objects with `id`, type data, optional `type` (omitted for item), optional `weight` (float, def 1)), optional `loot_crate` object (`string_id`, `color`, `item_name`, `glow`, `drops`), optional `loot_table_id`. The table's own `title` is in translations.

## 7. Text, translations and older syntax
- Titles, subtitles and descriptions are **not** in the object data in the latest format: they live in `lang/<locale>/` files as flat maps whose keys are `<objecttype>.<HEX16 id>.<part>` with parts `title`, `quest_subtitle`, `quest_desc` (list of strings), `chapter_subtitle` (list). Locale folders are word-characters only (e.g. `en_us`); the book setting `fallback_locale` names the default. Older books keep titles inline as `title`, `subtitle`, `description` keys on the object.
- Older (pre-JSON5) books use the `.snbt` extension and SNBT syntax: unquoted keys, no commas between entries, typed number suffixes (`1b` byte booleans, `5L`, `1.0d`, `2s`, `1.5f`), and item stacks as `{id: "...", Count: 1b}` or a bare id string. Our reader will accept both generations.
- `item`/`icon` values are item-stack objects (`id`, optional `count`, optional data/components). Ids, dimensions, entities, advancements, stats, biomes and structures use the **new** namespaced names of the version that wrote the file.

## 8. What this means for 1.12.2
1. **Id flattening gap:** names written by a 1.21 pack (for example a log or wool variant) do not exist in 1.12.2, where blocks/items use metadata. A **mapping table** (new id to 1.12.2 id + metadata) is required; ids that exist unchanged (many vanilla names, modded names) map directly. The importer must keep unmapped values intact (stored but flagged "unresolved") so nothing is lost on export and the quest stays visible.
2. Item components (custom names, enchantments, etc.) have no 1.12.2 equivalent; map known ones to NBT where straightforward, otherwise keep flagged.
3. Dimensions: names map to numeric ids (overworld 0, nether -1, end 1; modded by registered name). Advancements/stats/structures/biomes need per-name tables; unresolved ones stay flagged.
4. Tasks/rewards our mod does not implement (for example fluid/energy/structure in v1, stage without a stage mod) are imported as inert placeholders that preserve their data.
5. Tags (`#namespace:name`) map to ore-dictionary names only where a table exists.
