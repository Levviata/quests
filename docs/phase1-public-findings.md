# Phase 1 findings (public-information edition)

Scope decision (user, 2026-11-19): ArchaicLib's own source and the HE-RSAL repos are NOT used as inputs (their licenses forbid AI analysis / cloning). Sources used here:
- CurseForge pages for ArchaicLib (description, files, dependents), fetched 2026-11-19.
- The license texts themselves (legal documents, read to determine what is allowed).
- GitHub org metadata (repo names, descriptions, license identifiers).
- Necromancy (MIT) build files and import lines, via the GitHub API (no clone).
- Cleanroom Loader public README/license/releases.

Caveat: before this scope decision, the assistant had already read ArchaicLib's core source in this session (and deleted the clones). Nothing below is taken from that reading, but a design written in the same session is not provably "clean". Recommendation: write Phase 2 in a fresh session.

Labels: **[V]** verified from a public source, **[C]** reasonable conclusion, **[H]** unverified hypothesis, **[R]** recommendation.

## 1. What ArchaicLib is (from its CurseForge page)
- **[V]** A "shared compatibility library" for the author's ports of old mods to 1.12.2. It re-implements removed/changed 1.7.10 and 1.8.9 Minecraft/Forge APIs so ported mods stay close to the originals. Does nothing alone.
- **[V]** Listed features: 1.8.9 JSON model engine (original baking and display rules, remapping of removed vanilla models/textures/sounds); 1.7.10 blocks/items/render types/item renderers/icons; legacy simple-channel networking; legacy entity helpers (DataWatcher, extended entity properties, throwables, inventories); GameRegistry-style registration helpers and unnamed recipes; Mixin config plugin; fluids/buckets; TESR/Techne support; NPC helpers (navigator, dig safety, real crafting grid with JEI transfer).
- **[V]** Loader: description says Cleanroom Loader only, not plain Forge (CurseForge metadata says "Forge"; the text and metadata disagree).
- **[V]** Original-asset mechanism: a loader that reads original mods' assets from an `archaic_originals` folder, "for ports that ship no third-party assets".
- **[V]** Fast, rapid release cadence: 8 files in about 11 days (1.0.0 on 2026-11-08 to 1.16.0 on 2026-11-19), ~460 KB for the newest. Low adoption so far (99 total downloads). **[C]** The API is young and unstable.
- **[V]** CurseForge lists 6 dependents (Helpful Villagers Port, Necromancy Port, Lucky Legacy Addon, It Fell From The Sky Port, Modular Bosses Port, Too Much TNT Port); the description names 17 ports, so the relations list is incomplete. Four dependents are tagged "Embedded Library" (jar-in-jar style), two "Required Dependency". **[H]** Whether embedding is real is unverified: Necromancy's `@Mod` declares `required-after:archaiclib@[1.1.0,)`.

## 2. How extending mods use it (Necromancy, MIT, evidence-based)
Necromancy is a port of an MIT-licensed 1.7.10 mod, so it is a "real port with original-derived code", not the clean "referencing" case.
- **[V]** Depends on the lib through `required-after:archaiclib@[1.1.0,)` and a build line pointing at a sibling checkout's dev jar (`../ArchaicLib/build/libs/archaiclib-<ver>-dev.jar`); no Maven publication. **[C]** Workflow = side-by-side repos in one workspace.
- **[V]** The mod keeps its own package tree (api, block, client/gui, client/model, client/renderer, core, entity, item, network...), and imports ArchaicLib in only ~20 of ~106 files: base classes (`ArchaicBlock`, `ArchaicItem`), icon interfaces, `IItemRenderer`, `LegacyGameRegistry`/`LegacyRecipes`, `LegacyInventory`, `LegacyThrowable`, `LegacySounds`, `ArchaicChannel`/`AbstractPacket`. **[C]** The lib is a thin shim layer; mod logic stays in the mod.
- **[V]** Same Cleanroom build setup (loader 0.6.13-alpha in this repo, mixin config in the mod, optional AT/coremod).
- **[V]** Repo descriptions (public GitHub metadata): HelpfulVillagers "Original mod required for textures"; TheTitans "ships no original assets". **[C]** Different mods use different asset strategies; a port with an ARR original relies on the player's own copy.

## 3. Licensing map
| Project | License | Source-reading by AI | Reuse |
|---|---|---|---|
| ArchaicLib | All Rights Reserved ("visible, may be read"); explicitly bars reuse "including by rewriting it ... or using AI tools" | Not permitted for reuse; reading itself ambiguous | No |
| HelpfulVillagers, TheTitans, LuckyLegacyAddon | HE-RSAL v1.1 (Russian law, RU text prevails) | Art. 4.1 forbids feeding it to AI for analysis/porting/clean-room | No; permission needs Russian citizenship proof (6.4) |
| Necromancy, ModularBosses, EntityThreading, TooMuchTNT, HellYeah_Worldgen_WTH | MIT | Fine | Fine with notice (Necromancy also carries original-author notices) |
| Cleanroom Loader | CleanroomMC License 1.0, source-available; independent mods/Mixins not covered (Sec. 5) | n/a | Do not redistribute builds; building mods for it is OK |
| Fugue | GPL-3.0 | n/a | Separate tool |

**[C]** Public source is not permission. **[R]** Treat ArchaicLib API shape, names and code as off-limits; do not make our library API-compatible with it (this also avoids the API-compatibility question). Ideas that are generic (shim layers, player-supplied asset jars, Mixin hooks) are not owned by anyone, but we should design them independently from Forge/Minecraft docs. Legal uncertainty remains: this is not legal advice.
**[R]** FTB Quests/FTB Library/FTB Teams licenses must be checked individually in Phase 4; the "player supplies original assets" model is not a license workaround, only a distribution model.

## 4. Architectural patterns visible from public evidence
```
 Player's mods/ folder                          Player's own files
 ┌──────────────────────────┐                  ┌───────────────────┐
 │ Port mod A (own code)    │   depends on     │ original jars in   │
 │ Port mod B (own code)    │ ───────────────► │ a separate folder  │
 └────────────┬─────────────┘                  └─────────▲─────────┘
              │ calls shim API                           │ read as resource pack
              ▼                                          │
 ┌──────────────────────────────────────────────────────┴────────┐
 │ Compat library: legacy-API shims (registry, network, entity,    │
 │ rendering/model translation) + Mixin hooks + asset-pack loader  │
 └───────────────────────────┬─────────────────────────────────────┘
                             ▼
                 Cleanroom Loader (Forge 1.12.2 + CleanMix)
```
Lessons **[C]**: one thin shared lib + independent per-mod jars; keep shim surface small and let each mod own its logic; asset handling is a first-class concern; a rapid API churn hurts mods pinned to versions (so we need semver and a stable API package).

## 5. Gaps (unverifiable without source)
Exact class structure, Mixin target list, how resources are mounted, registry timing, and per-mod behavior differences are not verified. Not needed to proceed: Phase 2 designs from our requirements (FTB Quests is a GUI/data/network mod, not a renderer/entity port, so most of ArchaicLib's 1.7/1.8 shims are irrelevant to us).

## 6. Loader notes (public, Cleanroom README/releases)
- **[V]** Cleanroom = continuation of MinecraftForge for 1.12.2; requires Java 25; built-in Mixin via CleanMix; latest release 0.7.14-alpha ("28 Oct", year not shown); "no binary compatibility guaranteed before 1.0.0"; dev via CleanroomGradle (template CleanroomMC/CleanroomModTemplate).
- **[V]** Claims to retain compatibility with old Forge mods "as much as possible"; Fugue patches dead mods.
- Detailed Forge-vs-Cleanroom trade-off analysis belongs to Phase 2.
