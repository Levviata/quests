# Full-parity assignment: progress (resume here)

Assignment: bring Reflib Quests to 1:1 with FTB Quests 26.1.2 (phases 1-9, user message of 2026-11-21).

| Phase | State |
|---|---|
| 1 Source audit | Done at feature-group level: `docs/parity-audit-2026-11.md`. Per-feature table with exact counts still to do (Phase 9) |
| 2 Priorities and roadmap | Done (same file) |
| 3 Reference book files | Done: showcase = 4 chapters, 8 KB, in `...\26.1.2\.minecraft\config\ftbquests\quests`. Task types present: dimension, custom, xp, stat, kill, location, advancement, observation, biome. Absent: item, structure, fluid, energy, stage, interaction. No rewards, reward tables, images, chapter groups, quest links or descriptions yet. Book presets: normal, goal, info |
| 4 Inspect FTB UI | Done to the extent possible without claiming rewards or completing quests: see `docs/phase4-ftb-ui-capture.md` (three passes). Uncaptured: Custom/Checkmark task windows, XP reward windows, quest links, Resize/Rotate, claim screen, completion toast, loot crate, description editor, colour picker, locked state |
| 5 Build reference book | Started implicitly: the Rewards and Images chapter holds the new keys. Not yet complete (no groups, links, descriptions, crates) |
| 6 Import | Done at data level: `docs/phase6-import-report.md` (0 importer problems, round trip identical; 4 defects found: numeric table_id, nested item count, inert fluid/stat, dropped hover events). Not yet seen in game |
| 7 Compare | Done: `docs/phase7-comparison.md` (list of differences with priorities, fixes made and which were seen in game). Open items go to phase 8 |
| 8 Fix | Done for what FTB shows and 1.12.2 allows: windows, rows, pickers (item with count, entity type), menus, centring, Esc, icons, Stage/Interaction/Forge Energy tasks, Icon row "None" (log: `docs/phase7-comparison.md` items 1-16). Left on purpose: panel texture and map background (our theme), Entity Type Tag list (1.12.2 has no tags) |
| 9 Final audit | Done: `docs/phase9-final-audit.md` (84 features: 20 Complete, 19 Partial, 12 Missing, 0 Broken, 33 Unverified) |

Decisions: nothing is marked Complete without an in-game check; catalogue counts are stale; do not touch the user's spreadsheet.
Build state: `./gradlew.bat build` passes at 6c8058d (2026-11-21).

## Phase 4 findings that change the roadmap
- Map right-click offers **Image** (chapter image creation): ours has no equivalent (P1.2 confirmed).
- Gear menu has Download Quest Files, Reload Theme, Open Wiki: ours lacks them.
- Quest menu has **Resize**.
- Quest Edit has Icon Scaling, Visual Preset, Max Completable Dependents, Guide Page: ours lacks them. "Invisible until X tasks" is greyed while Invisible Until Completed is false.
- Chapter Edit has Default Visual Preset; chapter Subtitle is a list.
- Book Edit has Per-Team Reward by Type and a Visual Presets list.

## Tooling
Window driver: old `drive2.ps1` at `C:\Users\Lev\AppData\Local\Temp\claude\a5f0abfc2acd3740\b6804a47-4ddc-4ffe-8301-e86cdaf15af1\scratchpad`; `drive3.ps1` (adds `wheel x y notches`, negative scrolls down) in the 37da2ae53352b303 session scratchpad. Copy both somewhere durable if you need them again.

## Session 2026-11-22 (second full-parity pass, worktree `reflib-ftb-quests-parity-00a07b`)
- Phase 1 redone from the code: `docs/parity-audit-v2.md` (113 rows, counts at the bottom; corrects eight claims of the older audits).
- Phase 2: `docs/roadmap-v2.md`.
- Phase 8 started with items that need no FTB capture: team rewards, Only One, advancement criterion, only-from-crafting. Build and unit tests pass; **none seen in game**.
- Phases 3-7 not redone: need the FTB window driver (rebuild it) and the user to open FTB's book; see end of `roadmap-v2.md`.
- Nothing committed.

### Update (end of session 2026-11-22)
Phases 3-5: FTB driven live through `tools/window-driver` (captures in `docs/phase4-ftb-ui-capture.md`, reference book extended). Phase 6: book loaded into ours repeatedly, no importer problems; new keys now read/written. Phase 7: differences in `docs/phase7-comparison.md`. Phase 8: see the final report in `docs/parity-audit-v2.md`. Phase 9: final counts and report at the end of that file. Last commit before this note: `201f83a`. Nothing pushed.
