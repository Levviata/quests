# Phase 2 — Architecture proposal for the 1.12.2 "referencing" library

Status: **revised after user review (2026-11-19); awaiting final approval. No code written.**
Names (decided D6): mod id `reflib`, Maven group `com.levviata`, Java package root `com.levviata.reflib`.

## User decisions (override the original recommendations below where they conflict)
| ID | Decision | Consequence |
|---|---|---|
| D1 | **Cleanroom Loader only** | Sections 3 and 2.10 below updated. No plain-Forge support or CI. Java 25 runtime. Mixin is built in (CleanMix), so an optional Mixin layer is cheaper, but core still avoids it unless needed. |
| D2 | Small lib, features earned by need (default kept) | |
| D3 | Teams = interface + solo default (default kept) | |
| D4 | **Merge `gui` into `core`** (single jar); client code stays behind a `@SideOnly(CLIENT)` boundary in `...gui` subpackages so it can be split out into its own jar later | Module list in 2.1 updated |
| D5 | Shared jar, no embedding (default kept) | |
| D6 | **LGPL-2.1** (changed from OSL-3.0 on user request; "LGPL 2" interpreted as 2.1, the current v2 release), mod id `reflib`, group `com.levviata`, package `com.levviata.reflib` | See licensing note below |
| D7 | Player-supplied original-assets loader out of v1 (default kept) | |
| D8 | **Java 25 + RetroFuturaGradle** | See toolchain note in 2.10 |

**Flags raised by the decisions (please read):**
1. **[H] RetroFuturaGradle on Cleanroom.** The public Cleanroom docs I read (Phase 1) point to CleanroomGradle / CleanroomModTemplate as the Cleanroom dev setup; RetroFuturaGradle is documented for the plain-Forge 1.12.2 ecosystem (template: Gradle 8.10.1, RFG 1.4.1, Forge 14.23.5.2847, run on Java 21). I have not verified that RFG can build and run against Cleanroom, or on Java 25. Plan: first task of Phase 3 is a 1-hour spike (hello-world mod + `runClient` + `runServer` on Cleanroom). If it fails, fall back to CleanroomGradle and tell you; the library code is unaffected.
2. **[C] LGPL-2.1 is a weak-copyleft license.** Mods that only use the library through its public API may generally be under any license, but changes to the library itself must stay LGPL, and LGPL-2.1 imposes conditions on distributing combined works (for example allowing the library to be replaced by a modified version, and keeping notices). How these conditions apply to Java mods loaded as separate jars is not settled; this is not legal advice. Note: LGPL-2.1 has no explicit patent clause and is not compatible with Apache-2.0-only code in one combined work; "or any later version" wording is optional and I will ask before adding it. Copyright line: "Copyright (c) 2026 Levviata" unless you say otherwise. Referencing projects keep their own licenses (checked per project in Phase 4).
3. **Audience:** Cleanroom-only plus Java 25 limits players to Cleanroom packs; accepted by you.

Labels: **[V]** verified from a public source, **[C]** conclusion, **[H]** hypothesis (needs a test), **[R]** recommendation.

Clean-room note: this design is derived only from our own requirements, public Forge/Minecraft 1.12.2 knowledge, Cleanroom/MixinBooter docs and the Phase 1 public findings. ArchaicLib's source, API names and structure were **not** consulted and are **not** a specification. Package and class names here are our own and deliberately generic. (Phase 1 caveat still applies: the earlier session had read ArchaicLib's core source; this doc was written in a fresh session from the Phase 1 text and the prompts only.)

---

## 1. Requirements: what is actually necessary (refined prompt 2.1)

The first target is FTB Quests, a GUI + data + networking mod. Its upstream 1.12.2 versions exist [V: guide lists FTB Quests as 1.12.2–1.21.1, [craftdownunder](https://craftdownunder.co/guides/mods/ftb-quests)], but the current version is multi-loader and relies on Architectury/FTB Library/FTB Teams [V: [CurseForge relations](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/relations/dependencies)]. Detailed needs are a Phase 4 task; the table is a **[C]/[H]** estimate from the mod's public description, to be re-checked in Phase 4.

| 2.1 area | Needed for the first target? | Decision |
|---|---|---|
| Newer-mod-API compat | Partly: only where a referencing mod wants to look like the original to *other* mods | **Out of core.** Per-project adapter modules; no global "modern API emulation" |
| Recreating MC/Forge functionality | Small: a few modern-style conveniences (resource ids, item-stack matching/predicates, text components helpers) | **Small util module** |
| Registry / resource mgmt | Minimal (a few items/blocks/commands; reload hooks) | **Thin helpers**, no wrapper registry |
| Networking | **Yes**: quest data sync, progress updates, edit actions | **Core**: typed message layer over `SimpleNetworkWrapper` |
| Entity / tile entity | Probably not (task types may watch entities, not define them) | **Not in v1.** Add only when a project needs it |
| Rendering / models | Only 2D/GUI item & icon drawing | **GUI-scoped only**; no model engine |
| GUI / client | **Yes, the biggest piece**: scrollable quest map, panels, text fields, tooltips, editor | **Core (client)**: small widget toolkit on top of `GuiScreen` |
| Config | Yes (server rules, client UI options) | **Core**: thin wrapper on Forge `Configuration` + sync flag |
| Serialization | **Yes**: quest files must be human-editable and diffable | **Core**: SNBT-like text codec + NBT/JSON helpers |
| Teams / data sync | **Yes** (progress per player/team, server→client sync) | **Core: abstraction only** (a `TeamProvider` interface + default solo/party impl). We do *not* recreate FTB Teams inside the library |
| Other-mod integration | JEI/item-list, maybe permissions | **Optional-integration framework** (see 2.7) |
| Mixins / coremods | Not needed for Quests as far as known | **Not in core.** Optional, isolated `reflib-mixin` module only if a later project proves a need |
| Optional dependency handling | Yes | Core pattern |
| Shared utilities | Yes (logging, scheduling, side-safe helpers) | Core, kept small |

**[R]** Principle: *the library earns a feature only if two referencing projects (or one project plus a clear second use) need it.* Everything else stays inside the referencing mod. This is the opposite of a 1.7/1.8-era shim catalogue: FTB Quests needs almost none of that.

## 2. Architecture (refined prompt 2.2)

### 2.1 Modules (REVISED per D4: gui merged into core)
```
reflib-core      (mod id: reflib)   networking, serialization, config, teams SPI, util, lifecycle,
                                    plus client-only widget toolkit in com.levviata.reflib.gui.* (split-ready)
reflib-compat-*  (optional, per integration: jei, ...)  loaded only if target mod present
reflib-mixin     (optional, future)     only if a project proves Mixin is required (Cleanroom has Mixin built in)
```
Split-readiness rule: `gui` code may depend on `api`, never the reverse, and has no mutual references with other impl packages, so it can be lifted into its own jar later without API breakage.
(Original proposal, superseded: separate `reflib-gui` jar.)
**[R]** Two required jars for a GUI mod (core + gui) is acceptable; keeps servers free of client code and makes the GUI toolkit replaceable. If this feels heavy to the user, merge `gui` into `core` behind a `@SideOnly(CLIENT)` boundary (decision D4).

Referencing projects (`ftbquests-ref`, later others) are **separate mods** with their own mod ids, versions, licenses and repos. They never contain each other's code.

### 2.2 Package structure (root `com.levviata.reflib`; code blocks below abbreviate it as `dev.reflib`, read as `com.levviata.reflib`)
```
dev.reflib.api.*        STABLE public API (semver-guarded; the only thing projects may import)
  net/        message registration + typed messages
  data/       codec/serialization interfaces, SNBT reader/writer entry points
  team/       TeamProvider SPI, team/player data ids
  config/     config declaration annotations/builders
  integration/ optional-integration SPI
  lifecycle/  init hooks, reload hooks
dev.reflib.impl.*       internals; may change in any release; not exported
dev.reflib.gui.api.*    widget/screen API (in reflib-gui)
dev.reflib.gui.impl.*   internals
```
**[R]** Enforce the api/impl split mechanically (a build check that fails if a referencing project imports `impl`), because `@API`-style annotations alone are not enough in 1.12.2 where everything is on one classpath.

### 2.3 API design (sketch, not final signatures)
- **Networking**: a project registers *typed messages* with a codec; the lib owns the single channel per mod id, handles side-checking, thread hopping (handler runs on the main thread), size limits, and versioned handshakes. Built on Forge's `SimpleNetworkWrapper` **[V: standard 1.12.2 API]**. Projects write `register(MyMsg.class, Side.CLIENT, handler)`, not packet plumbing.
- **Data sync**: helper for "snapshot on join + delta on change" with a per-player dirty set. This is the pattern FTB-style mods need **[C]**.
- **Serialization**: (1) SNBT-like text format for authored data. 1.12.2 already has `JsonToNBT`/`NBTTagCompound#toString` that cover a close dialect **[C: from vanilla]**, but its exact parsing limits (quoting, comments, long/array types, pretty print) need a spike (**[H]**); the lib wraps or replaces it with a stable parser/printer plus **format versioning and a data-fixer hook** per project. (2) Optional JSON and binary (`PacketBuffer`) codecs from one declarative description to avoid writing each three times.
- **Config**: declarative annotations → Forge `Configuration`; flag fields `syncToClient`.
- **Teams**: `TeamProvider` SPI (`getTeam(player)`, `members`, change events). The lib ships a trivial default (each player = own team). A referencing project can ship real party teams or an adapter for an existing team mod; FTB Teams itself would be its own Phase 4 decision.
- **GUI toolkit** (client): widget tree (panel, button, scroll area, text field, item icon, tooltip), layout helpers, input routing, theming from resource pack textures. Plain `GuiScreen`/`GuiContainer` underneath **[V: vanilla 1.12.2]**. Must not require any asset from the original mod.
- **Integration SPI**: see 2.7.

### 2.4 Internals
- Sided code isolated in `client`/`server` subpackages; common code never references client classes. Standard 1.12.2 practice, avoids `NoClassDefFoundError` on dedicated servers **[V]**.
- Logging per project via an injected logger; no global state keyed on class names.
- No reflection into Minecraft internals in core. Where unavoidable, use a Forge access transformer (`META-INF/*_at.cfg`) **[V: supported by Forge 1.12.2]**, not Mixin, so core runs identically on Forge and Cleanroom.

### 2.5 How referencing projects depend on the library
- **Runtime**: a normal mod dependency, `dependencies = "required-after:reflib@[1.0,2.0)"` **[V: Forge version-range syntax]**. One shared `reflib` jar in `mods/` serves all projects.
- **Compile time**: published API-only jar (`reflib-api`, no impl) to a Maven repo / GitHub Packages / local `mavenLocal` initially. Projects compile against `api`, never against the full jar. **[R]**
- **No embedding / shading by default.** Phase 1 shows the CurseForge "Embedded Library" tag exists for other projects, but embedding a shared lib per mod breaks single-version-per-JVM guarantees (two copies of the same channel/classes). Forge 1.12.2 has no native jar-in-jar **[C]**. Rule: one shared jar, declared dependency.

### 2.6 Coexistence of multiple projects
- Namespacing by mod id everywhere (channels, config files, data folders, registry names). The lib refuses collisions at registration time with a clear error.
- Each project owns its data folder and its own data-format version.
- The lib exposes a *project registry* (id, version, required lib API level) used for diagnostics and the compat check below.

### 2.7 Optional integrations
Pattern: an integration is a class in the *referencing project* (or a `reflib-compat-*` jar) annotated for a target mod id; the lib instantiates it only if `Loader.isModLoaded(id)` and the declared version range matches **[V: Forge API]**. No direct imports of the other mod's classes outside the integration class. Failures are caught and logged, never crash the game. JEI integration lives in its own class/jar. **[R]**

### 2.8 Versioning and compatibility
- **API semver** (`MAJOR.MINOR.PATCH`) applied to `dev.reflib.api.*` only. Phase 1 evidence of a very young, fast-moving public library (8 releases in ~11 days) is the cautionary example **[C]**, so: no 0.x freeze-less churn once projects exist; deprecate for one minor before removal.
- **Wire protocol version** separate from API version; handshake fails with a readable message if mismatched.
- Projects declare a required `apiLevel`; mismatch → clear startup error rather than `NoSuchMethodError`.
- Data formats versioned per project with explicit upgrade steps.
- Releases are tagged and published; projects pin ranges, never snapshots.

### 2.9 Assets and external resources
The strategy per referencing project (decided in Phase 4 with per-licence analysis):
1. **Default: ship only our own assets** (original textures/lang/art created by us or permissively licensed). Library GUI toolkit uses its own default theme.
2. **Optional player-supplied assets** (user places original mod jar in a folder; loaded as a resource pack) is a *possible* mechanism **[C: Phase 1 shows another project uses this approach]**. **[R]** Offer it as an opt-in loader utility only; it is a distribution model, not a licensing workaround, and must not be used for assets whose licence forbids it. Not part of v1 unless FTB Quests' analysis in Phase 4 wants it.
3. Lang/translations: write our own; do not copy upstream strings.

### 2.10 Build and dev workflow
- **Repos**: `reflib` (multi-module Gradle) and one repo per referencing project. Avoid the "sibling checkout jar path" workflow seen in a published port **[V: Phase 1]**; use Gradle composite builds (`includeBuild`) locally and a published Maven artifact in CI.
- **Toolchain** [V, sources: [MixinBooter wiki](https://cleanroommc.com/wiki/forge-mod-development/mixin/mixinbooter), search results]: documented 1.12.2 setups use **RetroFuturaGradle** (template reports Gradle 8.10.1, RFG 1.4.1, Forge 14.23.5.2847, running on Java 21) and the CleanroomMC Maven (`maven.cleanroommc.com`). Cleanroom's own template is `CleanroomMC/CleanroomModTemplate` [V from Phase 1]. **[H]** Exact versions to be re-verified in Phase 3 setup; I could not confirm a newer Forge build than 2847.
- **Tests**: pure-Java unit tests for codecs/SNBT/versioning (JUnit, no Minecraft); in-game smoke tests via a dev client + dedicated server run configs; later a headless server boot test in CI for both loaders (see 3).
- **Docs**: `docs/` per module; API javadoc published; CHANGELOG with API-level notes.
- **Toolchain (D8, user): Java 25 + RetroFuturaGradle.** See flag 1 at the top: RFG-on-Cleanroom/Java 25 is unverified **[H]**; Phase 3 starts with a spike, fallback CleanroomGradle (the documented Cleanroom setup, **[V]** Phase 1).
- **Licence of the lib (D6, user): LGPL-2.1.** Add `LICENSE` (full LGPL 2.1 text), file headers, and `THIRD_PARTY.md`. See flag 2. (Earlier proposals MIT and OSL-3.0 are superseded.)
- **Attribution/third-party**: keep a `THIRD_PARTY.md`; no third-party source copied in.

## 3. Loader strategy: Forge 1.12.2 vs Cleanroom (refined prompt 2.3)

### Facts
- **[V]** Cleanroom Loader is a continuation of MinecraftForge for 1.12.2 with built-in Mixin (CleanMix), Java 25 per the current wiki/README (older mirrors still say Java 21), claims high compatibility with existing Forge mods, with the **Fugue** patch mod for remaining incompatibilities. Sources: [Cleanroom wiki](https://cleanroommc.com/wiki/end-user-guide/introduction), [Cleanroom repo](https://www.github.com/CleanroomMC/Cleanroom), [Fugue listing](https://www.modpackindex.com/mod/58623/fugue). The "~99 % of mods" figure is project marketing, not verified.
- **[V]** Phase 1: Cleanroom "no binary compatibility guaranteed before 1.0.0"; latest 0.7.x alpha; its licence is source-available and does not cover independent mods.
- **[V]** MixinBooter is the route for Mixin on plain Forge 1.12.2; from 11.0 the early/late loader split is dropped. [MixinBooter wiki](https://cleanroommc.com/wiki/forge-mod-development/mixin/mixinbooter), [CurseForge](https://curseforge.com/minecraft/mc-mods/mixin-booter).
- **[V]** Standard Forge 1.12.2 (14.23.5.x) runs on Java 8 (long-established; modern setups run it on newer JDK with toolchains **[H]**).

### Options
| | A. Forge-only | B. Cleanroom-only | C. Forge baseline, tested on both (**recommended**) |
|---|---|---|---|
| Audience | Biggest 1.12.2 player base | Players on modern Java / Cleanroom packs | Both |
| Java | 8 | 25 (current) | Bytecode target 8; run on either |
| Mixin | Needs MixinBooter | Built in | Core avoids Mixin; optional module handles both |
| Risk | Aging toolchain, slow ecosystem | Alpha, no binary-compat guarantee, narrower audience | Two-way test cost, small |
| Dev cost | Lowest | Low, but ties us to alpha churn | Low if core stays on stable Forge APIs |

### Reasoning
**[C]** FTB Quests-style functionality needs only stable Forge 1.12.2 APIs (`SimpleNetworkWrapper`, capabilities/world saved data, `GuiScreen`, config). None needs Mixin or Cleanroom-only features. Therefore the library does not *have* to choose a loader. Claiming Cleanroom-only (like another project does per Phase 1 **[V]**) would shrink the audience for no technical gain; claiming Forge-only without testing would make "works on Cleanroom" an unverified promise (**[H]** that it does).

### DECISION (D1, user): Cleanroom Loader only
Option B is chosen. (The original recommendation was C, Forge baseline tested on both; superseded.) Consequences:
- Target Java 25 bytecode/runtime; use Cleanroom's built-in Mixin (CleanMix) if and when a Mixin is needed; no MixinBooter.
- Test matrix is Cleanroom dev client + dedicated server only. Pin a Cleanroom version per lib release and record it in the changelog, because Cleanroom promises no binary compatibility before 1.0 **[V, Phase 1]**.
- Keep core code on stable Forge-1.12.2-origin APIs anyway (cheap, and keeps the Forge door open) but we do not test or promise it.
- Risk: alpha-stage loader churn; mitigation: pinned versions and a smoke test on each Cleanroom bump.

## 4. Risks
1. **[H]** The same code runs unchanged on Cleanroom (Java 25 vs 8-target bytecode, `sun.*`/reflection issues). Mitigation: smoke test early in Phase 3.
2. **[C]** GUI toolkit is the largest effort; scope creep risk. Mitigation: only widgets FTB Quests needs, built incrementally.
3. **[H]** SNBT dialect can't reach byte-level parity with modern FTB Quests files without studying the upstream format (Phase 4, licence-dependent). Keep the codec independent; data compat only as far as format spec is public.
4. Licences of FTB Library / Teams / Quests unverified (Phase 4); the lib design must not assume permission.
5. Young-ecosystem churn on Cleanroom/MixinBooter versions.

## 5. Decisions needed from the user
- **D1** Loader strategy: C (recommended) vs Forge-only vs Cleanroom-only.
- **D2** Scope: confirm the "small lib, features earned by need" principle (no entity/rendering/mixin shims in v1).
- **D3** Teams: lib ships only an abstraction + solo default; real teams decided in Phase 4.
- **D4** Module split: separate `gui` jar vs one core jar.
- **D5** Dependency model: shared jar, no embedding (recommended).
- **D6** Library licence (MIT recommended) and the naming/IDs (mod id, Maven group, package root).
- **D7** Player-supplied original-assets loader: out of v1 (recommended) vs include.
- **D8** Minimum supported tooling (Java 8 bytecode + RetroFuturaGradle) acceptable?

## Sources
- [Cleanroom wiki — end-user intro](https://cleanroommc.com/wiki/end-user-guide/introduction)
- [CleanroomMC/Cleanroom](https://www.github.com/CleanroomMC/Cleanroom)
- [MixinBooter wiki](https://cleanroommc.com/wiki/forge-mod-development/mixin/mixinbooter) · [MixinBooter CurseForge](https://curseforge.com/minecraft/mc-mods/mixin-booter)
- [Fugue (modpackindex)](https://www.modpackindex.com/mod/58623/fugue)
- [FTB Quests CurseForge relations](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/relations/dependencies)
- [FTB Quests guide (versions)](https://craftdownunder.co/guides/mods/ftb-quests)
- `docs/phase1-public-findings.md`

Limits: web research here was search-snippet level (standard search); no primary-page reads of Forge docs or Cleanroom README. Items marked [H] are untested.
