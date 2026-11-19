# Reflib Quests

A quest mod for Minecraft 1.12.2 (Cleanroom) built on reflib. It is an **independent implementation** inspired by FTB Quests; it contains none of that mod's code, and it is not affiliated with or endorsed by FTB. LGPL-2.1-only.

## Status (milestone M1 of Phase 5)

| Part | State |
|---|---|
| Quest model (`model`): chapters, quests, tasks, rewards, reward tables, dependencies, visibility, sequential tasks, repeatables, linear/flexible progression, per-team progress, reward claiming | Done and unit-tested (19 tests). Pure Java, no Minecraft types |
| Importer (`io.UpstreamImporter`): reads upstream quest folders, latest `.json5` generation and the older `.snbt` generation, including translations | Done and unit-tested (5 tests) with hand-written sample folders. **Not yet tried on a real quest pack** |
| Item/entity/dimension id mapping 1.21 to 1.12.2 | Not started (imported ids are kept as written) |
| Exporter (write quest folders back) | Not started |
| Server side: task detection, reward granting, persistence, commands | Not started |
| Sync and questbook screen | Not started |
| Assets from player-supplied upstream jars | Not started (reflib loader planned) |

Format reference: `../docs/upstream-quest-format.md`. Build and test: `./gradlew :reflibquests:build`.

Known interpretation to verify against real packs: the meaning of the "flexible" progression mode (tasks may progress before dependencies are met, completion waits for them).
