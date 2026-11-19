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
| `ReflibChannel` on a real connection (client <-> server round trip) | **Untested**: registry logic is unit-tested, wire path is not |
| Large messages | Limited to 30,000 bytes per message; no chunking yet. Whether Forge splits larger packets is unverified |
| GUI toolkit (`gui`) | **Compiles only; never rendered.** `ScrollPanel` clipping uses raw GL scissor and only translates direct children |
| Protocol-version handshake | Not implemented (messages must be registered in the same order on both sides) |
| Config helpers | Not implemented (Forge `@Config` suffices) |
| RetroFuturaGradle | Not used: the repo's Cleanroom template builds with CleanroomGradle 0.22.0, and RetroFuturaGradle's Cleanroom support is unverified |

## Third-party

No third-party source is included. The build depends on Cleanroom (userdev), JUnit 5 (tests only) and JEI (dev runtime only), through their own licenses.
