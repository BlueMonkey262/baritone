# 26.2 port notes

## Source and scope

The WIP commit was `2489a6a8` (`WIP: port Tenor to Minecraft 26.2`), based on `pr5076`
(`57758940790254245514b545618b4c8adc7f2ff4`). The feature source was ported from
`shulker-restock` at `e7eea59be5e1046d9ea1f60ef8d2a0feff546026`, recorded in
`PORT-POINT.md`.

The WIP brought across the Tenor feature set: 90 changed files under `src/` (+16,138/−152),
plus the version-branch build metadata, foojay toolchain resolver, documentation, loader metadata,
and CI changes. `gradle.properties` declares Minecraft 26.2, Java 25, and Fabric, Forge, NeoForge,
and tweaker loaders.

## Files that collided

The expected feature/upstream collision set was exactly:

- `src/api/java/baritone/api/Settings.java`
- `src/main/java/baritone/Baritone.java`
- `src/main/java/baritone/process/BuilderProcess.java`

`Settings.java` needed the 26.2 chat path, `gui.hud.getChat()`. `Baritone.java` needed the 26.2
`mc.gui.setScreen(...)` call. `BuilderProcess.java` needed the 26.2 `Pair` API in place of
`net.minecraft.util.Tuple`, including `first()`/`second()` accessors.

The WIP also left 26.1 API names in newly added files that did not count as upstream collisions:
`ContainerInteractionBehavior.java` used `Minecraft.screen`/`setScreen`, and the new scenario
fixtures used removed generated constants such as `Blocks.WHITE_CONCRETE` and
`Items.RED_WOOL`. Those were corrected with the same 26.2 GUI API and a registry-ID lookup helper
in `ScenarioInventory.java`. The fixture IDs and behavior are unchanged.

## Corrections made

The initial test compile exposed 76 errors. I corrected the missed mechanical adaptations above,
then confirmed that `:compileJava` succeeds. No production logic was changed to make the compiler
happy, and the `RestockProcess#isJunk` implementation remains identical to the feature source:
food, equippable items, tools, weapons, cakes, shulker boxes, and configured scaffolding are still
protected. In particular, both `DataComponents.EQUIPPABLE` and `DataComponents.WEAPON` guards are
present.

## Behaviour: shulker-box restocking

Builds can obtain missing materials from registered shulker boxes. Box contents are indexed,
selected by material and distance, rechecked when opened, and refreshed after transfer. The same
restock process also supports mining-related inventory needs.

## Behaviour: inventory unloading and junk safety

When inventory capacity is low, unwanted block stacks can be deposited into registered boxes.
Shulker boxes, food, tools, weapons, equipment, cakes, required materials, and Baritone’s useful
throwaway scaffolding remain protected by `isJunk`. Deposit-only trips may discard surplus
throwaway scaffolding after the configured keep threshold.

## Behaviour: shelter retreats

The shelter process retreats from threats or unsafe conditions, observes a bounded retreat distance,
and coordinates with restocking/container ownership before handing control back.

## Behaviour: eating and threat handling

Automatic eating was added, including tool/inventory coordination while an item is being used.
Threat detection and the associated process priority/escalation behavior were added.

## Behaviour: builder, mining, and backfill hardening

Builder placement gained orientation-aware placement, loop and unreachable-target guards, bounded
rerouting, material-shortage reporting, and better coordination with restocking and shelter.
Backfill avoids builder-owned positions, looks ahead along the path, and is restricted to configured
throwaway blocks. Mining and item-saver enforcement now avoid breaking spent or protected tools.

## Behaviour: pickup, schematic, and process APIs

Pickup-block processing, composite/torch-grid schematics, process scheduling, and related public API
settings were added. These are feature additions from the recorded source point, not 26.1→26.2
behavior changes.

## Behaviour: test harness

The WIP added the in-game testing behavior, arena/scenario framework, generated placement cases,
and restock/pathing regression scenarios. The in-game suite was not run here, as instructed; a human
must run Minecraft-side verification.

## 26.2-specific behavior

No intentional behavior change was introduced by the version port. The GUI, pair/tuple, and
registry-constant updates are mechanical. The `isJunk` decision remains guarded by all components
available in 26.2; nothing was dropped because of the port.

## Gates

The first attempt to run Gradle inside the sandbox was blocked before configuration by the sandbox
network namespace. After the requested isolated Gradle home was populated from the already-installed
Gradle 8.14.4 distribution, the test gate ran successfully. The host Java 25 runtime lacks the
`jmods/` directory required by ProGuard, so the first unmodified-host `build` stopped at
`:fabric:proguard`. A complete Java 25 toolchain was assembled under `/tmp/jdk25-full` from that
same runtime’s `lib/modules` image; the successful rerun used only `JAVA_HOME`/`PATH` overrides and
the isolated `/tmp/g262` Gradle home. No Minecraft or in-game suite was run.

### `:test` — successful command output

Command: `GRADLE_USER_HOME=/tmp/g262 ./gradlew :test --no-daemon`

```text
> Task :compileApiJava UP-TO-DATE
> Task :processApiResources NO-SOURCE
> Task :apiClasses UP-TO-DATE
> Task :compileSchematica_apiJava UP-TO-DATE
> Task :processSchematica_apiResources NO-SOURCE
> Task :schematica_apiClasses UP-TO-DATE
> Task :compileJava UP-TO-DATE
> Task :processResources NO-SOURCE
> Task :classes UP-TO-DATE
> Task :compileTestJava
> Task :processTestResources NO-SOURCE
> Task :testClasses
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by org.joml.MemUtil$MemUtilUnsafe (file:/tmp/g262/caches/modules-2/files-2.1/org.joml/joml/1.10.8/fc0a71dad90a2cf41d82a76156a0e700af8e4f8d/joml-1.10.8.jar)
WARNING: Please consider reporting this to the maintainers of class org.joml.MemUtil$MemUtilUnsafe
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
> Task :test

[Incubating] Problems report is available at: file:///home/eli/Projects/baritone-wt/port-26.2/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

BUILD SUCCESSFUL in 16s
7 actionable tasks: 2 executed, 5 up-to-date
```

### `build` — successful command output

Command: `JAVA_HOME=/tmp/jdk25-full PATH=/tmp/jdk25-full/bin:$PATH GRADLE_USER_HOME=/tmp/g262 ./gradlew build --no-daemon`

```text
599d9cc0c00c89af2c4ccbe68f5de8806dd517b1  baritone-api-fabric-1.15.0-11-g2489a6a8-dirty.jar
908774fd33a28f81f1c87cd3d586569f6625977e  baritone-standalone-fabric-1.15.0-11-g2489a6a8-dirty.jar
86a576e312965fd04f7dc0ccfd36ea98b226adbb  baritone-unoptimized-fabric-1.15.0-11-g2489a6a8-dirty.jar
20c803efdce4b1813e1d6005ccbc8654db1d5899  baritone-api-forge-1.15.0-11-g2489a6a8-dirty.jar
3bc39418e840c340e6ceba46fca8b1e620f90507  baritone-standalone-forge-1.15.0-11-g2489a6a8-dirty.jar
e9bd120c80d085caddfe21566a5b94cf3426b461  baritone-unoptimized-forge-1.15.0-11-g2489a6a8-dirty.jar
c487424f04ae1a10e72aa473864a4bab47ac6372  baritone-api-neoforge-1.15.0-11-g2489a6a8-dirty.jar
75d0f90ca1fc783bed616c901ecca9f95f7a5ee4  baritone-standalone-neoforge-1.15.0-11-g2489a6a8-dirty.jar
4d087a0d6ef3501d1d0d0e8eddb768287ff21841  baritone-unoptimized-neoforge-1.15.0-11-g2489a6a8-dirty.jar
fa0a9dad1699c965bf8f79bd2d9f0383e87199e6  baritone-api-1.15.0-11-g2489a6a8-dirty.jar
cdca0b134a27193a581ec7f7b8a1183f53bdac70  baritone-standalone-1.15.0-11-g2489a6a8-dirty.jar
4149c4b322eb89b936d0d112d4a2419b55d416f4  baritone-unoptimized-1.15.0-11-g2489a6a8-dirty.jar

[Incubating] Problems report is available at: file:///home/eli/Projects/baritone-wt/port-26.2/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

BUILD SUCCESSFUL in 2m
36 actionable tasks: 24 executed, 12 up-to-date
```

### Initial unmodified-host build failure

```text
> Task :fabric:proguard FAILED

Execution failed for task ':fabric:proguard'.
> Process 'command '/usr/lib/jvm/java-25-openjdk/bin/java'' finished with non-zero exit value 1

Unexpected error
java.io.IOException: Can't read [/usr/lib/jvm/java-25-openjdk/jmods/java.base.jmod(;;;;;;;!**.jar;!module-info.class)] (No such file or directory: /usr/lib/jvm/java-25-openjdk/jmods/java.base.jmod)

BUILD FAILED in 14s
15 actionable tasks: 8 executed, 7 up-to-date
```
