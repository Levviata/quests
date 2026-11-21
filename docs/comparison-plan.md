# Comparison plan: Reflib Quests against FTB Quests 26.1.2

Method: **0** decide what to check (this file) → **1** capture everything on both sides → **2** compare the whole set and write one list → **3** fix the whole list, build once, relaunch.

Reference for what exists: `docs/feature-catalogue.md` (from the supplied comparison sheet). Only features marked Implemented or Partial there are checked here; Not implemented ones are a later phase.

Test data: the showcase book built through FTB's own UI (chapters `Shapes and Sizes`, `Dependencies`, `Visibility Options`, `Task Types`), loaded into both games.

## Priority 1: fundamental features (does it work and look like the same thing)

| Feature (catalogue) | Showcase chapter | What is compared |
|---|---|---|
| Shape, size, position | Shapes and Sizes | every shape draws, the same size and place, the same body and outline |
| Dependency list and requirement; optional quest; locked state | Dependencies | line between quests, locked look, which quests unlock when |
| Hide until dependencies visible or complete; invisible until completed; hide details, text, lock icon | Visibility Options | what shows in edit mode and for a player, the markers |
| Repeatable quest; sequential tasks | Visibility Options | repeat marker, tooltip lines |
| Task types and their pictures | Task Types | the quest picture, the title it gets, the quest window |
| Quest window: description, tasks, rewards | all | layout, slots, check marks, buttons |
| Chapter panel and chapter groups | all | rows, selected state, icons, alerts |
| Completion toast, claim, alerts | all | toast look and wording (needs a quest completed) |

## Priority 2: visuals (the same picture)

Dependency line texture, thickness, colour; quest body and outline colours; locked, completed, optional looks; badges (check, alert, lock, eye); task, quest and chapter icons; panel and window sizes, borders, shadows; map background; sidebar buttons; tooltip boxes.

## Priority 3: details

Tooltip text and its colours; menu entries and their order; option names and the order of their values; window rows; default titles; sounds; keyboard shortcuts.

## Capture list (step 1)

For each chapter, on both sides, in edit mode and with the same window size:

1. the map as it opens
2. each quest: hover tooltip, then the opened quest window
3. one right-click menu per kind of object (quest, task, reward) once per session
4. the chapter panel

Everything is captured before anything is compared, and everything compared before anything is changed.
