# Original features

Things Reflib Quests has or does differently from FTB Quests 26.1.2 on purpose. Differences like these are not bugs when comparing the two. Each line names where to look in the code. This is the only document kept in the repository; it is updated only when asked.

- **Platform**: Minecraft 1.12.2 on Cleanroom only, built on the `reflib` library (`src/`). Party teams are a separate server-side mod (`reflibteams`, `/rteam`). `reflibdebug` is a development tool (local control port for scripting a client), not part of the mod.
- **Own theme**: dirt and grass panel texture, opaque stone map background, chevron dependency line texture (`QuestArt`, `Ui`). FTB's translucent map over the world is not copied. Decision by the user.
- **Fluid and energy tasks are handed in by clicking the task** (`TaskTracker.submitFluid`, `submitEnergy`), the 1.12.2 way, instead of counting what the player carries. Decision by the user.
- **Stage task** uses a player scoreboard tag, since 1.12.2 has no game stages (`TaskTracker`, `player.getTags()`; set with `/scoreboard players tag @p add <stage>`). Team Stage is kept in the file and ignored.
- **Add Dependency** entry in the quest right-click menu (`QuestBookWidget.questMenu`). FTB has no such entry. Kept by decision of the user on 2026-11-23.
- **Delete confirmation** ("Really delete this quest?") and the Back / Add / Close hints in the reward table window.
- **Upstream art from a player-supplied jar**: quest shapes and icons are read from an FTB Quests jar the player puts in `reflib-supplied/` (`reflib` `SuppliedAssets`, `QuestArt`). No FTB art is shipped.
- **Id translation to 1.12.2**: a built-in table of newer item and entity ids mapped to 1.12.2 names, overridable with `config/reflibquests/id_map.snbt` (`BuiltinIdMap`, `IdMap`). Unmapped ids show as a missing-item icon.
- **Importers**: reads FTB Quests 26.1.2 json5 folders (`UpstreamImporter`) and the older 1.12.2 snbt folders (`LegacyImporter`, `LegacyModernizer`), and writes the 26.1.2 format back (`UpstreamExporter`).
- **Commands**: `/rquests` (list, info, check, submit, claim, claimall; admin: complete, reset, reload, lock, blockrewards, problems, export) and the client command `/rqbook`. Default key to open the book is J.
- **Party teams**: progress is stored per party when `reflibteams` is installed (`PartyTeamProvider`), otherwise per player.
- **Custom task and custom reward** have no completion trigger yet; only code calling `QuestServer.setTaskProgress` can complete a custom task (`QuestEvents` only reports progress).
