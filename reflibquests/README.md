# Reflib Quests

A quest mod for Minecraft 1.12.2 (Cleanroom) built on reflib. It is an **independent implementation** inspired by FTB Quests; it contains none of that mod's code, and it is not affiliated with or endorsed by FTB. LGPL-2.1-only.

## Status (milestones M1-M3 of Phase 5)

| Part | State |
|---|---|
| Quest model (`model`): chapters, quests, tasks, rewards, reward tables, dependencies, visibility, sequential tasks, repeatables, linear/flexible progression, per-team progress, reward claiming, progress save format | Done, unit-tested (19 + 6 tests). Pure Java, no Minecraft types |
| Importer (`io.UpstreamImporter`): upstream quest folders, latest `.json5` and older `.snbt`, translations | Done, unit-tested (5 tests) on hand-written sample folders. **Not yet tried on a real quest pack** |
| Id map (`config/reflibquests/id_map.snbt`): newer item/entity names to 1.12.2 names, with item metadata | Done, unit-tested. **No built-in table yet**: only entries you add resolve; unmapped 1.21 names make a task inert (listed by `/rquests problems`) |
| Server (`game`): load, per-team progress saved in the world, autosave, task detection, rewards, auto-claim, notifications, Forge events (`api.QuestEvents`), `/rquests` commands | Done. **Verified in game** (dedicated-server smoke test; singleplayer by the user on a hand-written sample pack: item, xp, kill, checkmark, consuming item with id map, choice/random rewards, command reward, auto-claim, persistence across rejoin) |
| Task types detected | item, checkmark, xp, kill, advancement, dimension, location, custom. **Advancement, dimension and location are implemented but were not exercised in game.** Not supported (imported, shown, never progress): stat, observation, biome, structure, fluid, energy, interaction, game stage |
| Reward types granted | item, xp, xp_levels, command, toast (chat message), advancement, custom, random, all_table, choice. **Advancement and all_table rewards were not exercised in game.** Not supported: loot, currency, game stage |
| Sync (`net.QuestNet`): quest book sent on join and after reload (compressed, chunked by reflib), team progress snapshot after each change (at most once per tick), claim/check/hand-in actions from the client re-validated on the server | Done. **Verified in game** in singleplayer: a 1506-quest book (30 KB packed, more than one network frame) arrived intact. Unit-tested wire codec (5 tests). **Not tested with two players or a dedicated server with a remote client.** Progress is re-sent as a full snapshot, not as differences. All quest data, including hidden quests, is sent to every client |
| Questbook screen (`client`): chapter list, pannable/zoomable map with dependency lines and state colours, detail panel with tasks, progress bars, check / hand in / claim / choice buttons; opens with J or `/rqbook` | Built and used by the user in game. **Reported problems fixed but not yet confirmed by the user:** icons overlapping the detail panel, choppy dragging (now follows the frame rate), icons not scaling with the quest square (now proportional, 72% of the square). Limits: squares only (shapes ignored), no chapter images or quest links, detail panel does not scroll, placeholder art |
| Assets from player-supplied upstream jars (reflib loader exists) | Not yet used by this mod |
| Exporter (write quest folders back), editor, party teams | Not started |

Format reference: `../docs/upstream-quest-format.md`. Build and test: `./gradlew :reflibquests:build`.

Known interpretation to verify against real packs: the meaning of the "flexible" progression mode (tasks may progress before dependencies are met, completion waits for them).
