# Reflib

Shared infrastructure for independent Minecraft **1.12.2** reimplementations ("referencing projects") of modern mods. **Cleanroom Loader only**, Java 25. Licensed **LGPL-2.1-only** (see `LICENSE`).

Reflib contains no gameplay content and no code or assets from any other mod. Each referencing project is its own mod (own id, repo, license) that depends on reflib.

## What it provides (API level 1)

| Package (`com.levviata.reflib.…`) | Purpose |
|---|---|
| `api.project` | Register your project; fails at startup on id collisions or if reflib is too old |
| `api.net` | One typed message channel per project (`ReflibChannel`, `ReflibMessage`): id by registration order, side checks, main-thread handlers, size limit, malformed messages dropped and logged |
| `api.data` | `Snbt` human-editable text format (parse/print with line/column errors), immutable `DataNode` tree, `DataUpgrader` (versioned data files), `NbtBridge` (to/from NBT) |
| `api.team` | `TeamProvider` interface, solo default, `Teams` holder (one replacement allowed) |
| `api.integration` | Lazy optional integrations: the integration class is loaded only if the target mod is present in a matching version |
| `gui` (client only) | Small widget tree: `ReflibScreen`, `Widgets.Panel/Label/Button/ScrollPanel`, pure `Rect` and `ScrollModel` |
| `api.assets` | `SuppliedAssets`: use assets from jars the player supplies (folder `reflib-supplied/`): resource pack on the client, JSON-to-`.lang` conversion, `declare`/`missing`/`read`/`alias`, one-time missing-jar notice |
| `api.team` | (see above) `Teams` now also lets mods listen for team changes (`addListener`, `notifyChanged`); a provider such as reflibteams calls `notifyChanged` |
| `api.sync` | `DirtyTracker`: per-recipient changed keys for snapshot-then-delta sync |
| `impl.*` | Internals. Do not import. A test enforces that the example project does not |

Not provided (by design, see `docs/phase2-architecture.md`): entity/rendering/model shims, a Mixin layer, a config wrapper (use Forge's built-in `@Config`), a full teams system, original-asset loading.

## Using it from another mod

```java
@Mod(modid = "myproject", dependencies = "required-after:reflib")
public class MyProject {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        ReflibProjects.register("myproject", "1.0.0", 1);                 // needs API level >= 1
        ReflibChannel channel = ReflibChannel.create("myproject")
                .register(PingMessage.class, Side.SERVER, (msg, ctx) -> { /* ctx.sender() is the player */ });
        Integrations.register("jei", "[4.16,)", () -> new MyJeiIntegration());
    }
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent e) { Integrations.activateAll(); }
}
```

A complete, working example is `src/main/java/com/levviata/reflib/devexample/` (mod id `reflibexample`). It is dev-only: excluded from the release jar, and it only imports `com.levviata.reflib.api`.

```java
DataNode.Compound quest = Snbt.parse("{ title: \"First steps\", xp: 50L, deps: [1, 2] }");
NBTTagCompound nbt = NbtBridge.toNbt(quest);
DataNode.Compound upgraded = new DataUpgrader(1).step(0, c -> c.with("name", c.getString("title", ""))).upgrade(quest);
```

## Building and testing

```
./gradlew build          # compiles, runs 39 JUnit tests, builds build/libs/reflib-*.jar
./gradlew runClient      # dev client (reflib + reflibexample)
./gradlew runServer -Pextra_jvm_args=-Dreflib.smoke=true   # in-game smoke test, prints REFLIB-SMOKE: PASS/FAIL then stops
```

`runServer` asks "Start the server in offline mode?" on first start; answer `y` for a local dev server.

## Status

| Area | Status |
|---|---|
| Build on Cleanroom 0.7.14-alpha / Java 25 | Verified (`./gradlew build` passes) |
| Snbt, DataNode, DataUpgrader, NbtBridge, ReflibProjects, Teams, Integrations (logic), MessageRegistry (encode/decode/rules), ScrollModel/Rect, api/impl boundary | Unit-tested (39 tests) |
| In-game dedicated server smoke test | **Passed** (Cleanroom 0.7.14-alpha, Java 25): reflib and `reflibexample` load, project registry, channel registration, Snbt/NBT round trips, upgrader, default teams, and "integration for an absent mod does not run" all verified in the real game. The log also shows an unrelated `LinkageError` from the server console (jline) that does not affect the test |
| `ReflibChannel` on a real connection (client <-> server round trip) | **Verified in game** (integrated server, 2026-11-19): client ping reached the server, reply came back, handlers ran on the main threads. Dedicated-server-with-remote-client not tested |
| Supplied assets (`api.assets`) | Unit-tested (scan, domains, aliases, case, `minecraft` hidden, JSON lang conversion). **Verified in game with a synthetic test jar** (client: lang, converted multi-line text, texture, no `minecraft` leak; dedicated server: scan and read). The missing-jar notice screen has **not** been viewed. Not yet tried with a real mod's jar |
| Large messages | Chunked transparently up to 8 MiB per message (unit-tested: split, reassembly, limits, timeouts). Not yet exercised with a multi-fragment message over a real connection |
| GUI toolkit (`gui`) | **Partly verified by the user in game**: the demo screen opened and rendered. Fixed after testing: the example's `/reflibgui` command (the chat screen closed the new screen) and a state reset on window resize (the tree is rebuilt on resize by design; documented on `ReflibScreen`). After the fixes the user re-tested in game (2026-11-19) and reported everything worked: `/reflibgui`, counter kept across resize, list scrolling and clipping, buttons. Only manually observed, not automated; one platform/GUI scale only. `ScrollPanel` only translates direct children |
| Protocol check | A per-channel signature (protocol version + ordered message list) is sent with every frame; mismatching peers are ignored with a log line. Unit-tested; not tested between two different real builds |
| Config helpers | Not implemented (Forge `@Config` suffices) |
| RetroFuturaGradle | Not used: the repo's Cleanroom template builds with CleanroomGradle 0.22.0, and RetroFuturaGradle's Cleanroom support is unverified |

## Third-party

No third-party source is included. The build depends on Cleanroom (userdev), JUnit 5 (tests only) and JEI (dev runtime only), through their own licenses.
