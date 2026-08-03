# Tenor port notes: Minecraft 1.20.4

## Port point

The additive Tenor feature set came from `shulker-restock` at the exact SHA recorded in
`PORT-POINT.md`: `e7eea59be5e1046d9ea1f60ef8d2a0feff546026`.

The port targets Java 17 and all four configured loaders: Fabric, Forge, NeoForge, and tweaker.
The source files from the canonical branch were enumerated and brought over before compiling
against the 1.20.4 mappings.

## Files requiring collision work

The three expected collision files all required a deliberate merge with the 1.20.4 base:

- `src/api/java/baritone/api/Settings.java`: retained the target settings and logger APIs while adding the Tenor settings, including `toastTimer`.
- `src/main/java/baritone/behavior/InventoryBehavior.java`: retained 1.20.4 inventory slots and click types while adding Tenor tool selection and inventory behavior.
- `src/main/java/baritone/process/BuilderProcess.java`: retained the target builder control flow and inventory APIs while adding the Tenor build/restock behavior.

Additional API adaptation was needed in the new container, restock, shelter, testing, placement,
world-cache, rendering, and scenario code.

## Beyond mechanical renames

### Food classification and auto-eating

The component-based `FOOD` checks were translated to 1.20.4's `Item#isEdible` and
`Item#getFoodProperties`. The Tenor auto-eat settings, exclusion list, hunger threshold, and
process pause behavior are preserved.

### Tool classification and item saving

The component-based `TOOL` checks were translated to the 1.20.4 `DiggerItem`/`TieredItem` hierarchy.
Legacy `EnchantmentHelper` and `Enchantments` calls replace component enchantment lookups, and
the item-saver path retains its spent-tool and replacement behavior.

### Junk classification and wearable blocks

`RestockProcess#isJunk` still checks `BlockItem` first, then refuses edible items, equipment,
digging tools, swords, tridents, and projectile weapons. `Equipable` is the pre-component
equivalent needed for wearable blocks such as carved pumpkins; armour remains excluded by the
existing `BlockItem`-first rule. Food, tools, weapons, and wearables therefore remain protected
from junk deposits.

### Fireworks

The 1.20.4 Elytra code already represents firework data in the legacy `Fireworks` compound, so
the component-based `FIREWORKS` checks were kept as NBT checks for the presence of `Explosions`
and `Flight`. No known vanilla behavior gap remains for this boundary.

### Water traversal cost

Depth Strider is read through the 1.20.4 `EnchantmentHelper` API and clamped to vanilla's
three-level maximum. The Tenor water cost multiplier is retained in `ActionCosts`; the resulting
pathing behavior is the 1.20.4 equivalent of the component-era calculation.

### Shulker-box restocking and dumping

Registered shulker boxes can be indexed, searched, opened, restocked from, and used to deposit
unwanted block items. Capacity, distance, timeout, extra-stack, dump-threshold, and per-trip
limits are preserved, including keeping configured scaffolding blocks and refreshing box contents
after a successful deposit.

### Builder behavior

The builder retains Tenor's orientation-aware placement, protected/skip block handling, backfill
restriction, and material restocking behavior. Inventory selection uses the 1.20.4 selected-slot
field and inventory list.

### Torch grids

`sel cleararea` can retain a configurable floor torch grid, with spacing and avoidance of nearby
existing torches. The placement catalog and placement rules were adapted to 1.20.4 resource
locations without changing their generated placement cases.

### Threat sheltering

The Tenor threat behavior can pause an active process after repeated hostile hits, retreat to a
registered box, unload, optionally sleep, and resume after the configured calm period. The
1.20.4 day/thunderstorm API is used for the shelter sleep decision.

### Test and scenario harness

The scenario harness, fuzz/pathing cases, restock cases, and testing command were ported to the
1.20.4 container and world APIs. The test-only disconnect path uses the target client's
`disconnect()` API.

No known `FOOD`, `TOOL`, `FIREWORKS`, `WEAPON`, or `EQUIPPABLE` behavior gap was found for the
vanilla 1.20.4 item families. The translations intentionally use the item classes available in
1.20.4 because that version has no arbitrary data-component attachment point.

## Gate output

The following is the verbatim output of the final required test gate invocation:

```text
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.2/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build
> Task :buildSrc:compileJava UP-TO-DATE
> Task :buildSrc:compileGroovy NO-SOURCE
> Task :buildSrc:processResources NO-SOURCE
> Task :buildSrc:classes UP-TO-DATE
> Task :buildSrc:jar UP-TO-DATE

> Configure project :
[Unimined] Plugin Version: 1.2.9
Detected version 1.10.4-2-ge99276a2-dirty
[Unimined/MinecraftDownloader] retrieving version metadata
[Unimined/MinecraftDownloader] retrieving launcher metadata
[Unimined] Plugin Version: 1.2.9
Detected version 1.10.4-2-ge99276a2-dirty
[Unimined/MinecraftDownloader] retrieving version metadata
[Unimined/MinecraftDownloader] retrieving launcher metadata
[Unimined] Plugin Version: 1.2.9
Detected version 1.10.4-2-ge99276a2-dirty
[Unimined/MinecraftDownloader] retrieving version metadata
[Unimined/MinecraftDownloader] retrieving launcher metadata
[Unimined] Plugin Version: 1.2.9
Detected version 1.10.4-2-ge99276a2-dirty
[Unimined/MinecraftDownloader] retrieving version metadata
[Unimined/MinecraftDownloader] retrieving launcher metadata
[Unimined] Plugin Version: 1.2.9
Detected version 1.10.4-2-ge99276a2-dirty
[Unimined/MinecraftDownloader] retrieving version metadata
[Unimined/MinecraftDownloader] retrieving launcher metadata
[Unimined/Minecraft ::main] Applying minecraft config for source set 'main'
[Unimined/Minecraft ::main] Applying NoTransformMinecraftTransformer
[Unimined/Minecraft ::main] Applying run configs
[Unimined/ModRemapper] Remapping mods from official/official to mojmap/intermediary
[Unimined/ModRemapper] No mods found for remapping

> Configure project :fabric
[Unimined/Minecraft :fabric:main] Applying minecraft config for source set 'main'
[Unimined/Minecraft :fabric:main] Applying AccessWidenerMinecraftTransformer
[Unimined/MappingsProvider] Resolving mappings for source set 'main'
[Unimined/MappingsProvider] Mapping tree initialized, official -> [mojmap, intermediary]
[Unimined/Minecraft :fabric:main] Applying run configs
[Unimined/ModRemapper] Remapping mods from intermediary/intermediary to mojmap/intermediary
[Unimined/ModRemapper] No mods found for remapping
[Unimined/Fabric] Generating intermediary classpath.
[Unimined/Runs] Applying runs
[Unimined/FabricLike] Non-fabric ::api found in fabric classpath groups, merging with current (:fabric:main), this should've been manually specified with `combineWith`
[Unimined/FabricLike] Non-fabric ::launch found in fabric classpath groups, merging with current (:fabric:main), this should've been manually specified with `combineWith`
[Unimined/FabricLike] Non-fabric ::main found in fabric classpath groups, merging with current (:fabric:main), this should've been manually specified with `combineWith`

> Configure project :forge
[Unimined/Minecraft :forge:main] Applying minecraft config for source set 'main'
[Unimined/ForgeTransformer] Selected FG3
[Unimined/Forge] Using FG3 transformer
[Unimined/MappingsProvider] Resolving mappings for source set 'main'
[Unimined/MappingsProvider] Mapping tree initialized, official -> [mojmap]
[Unimined/Minecraft :forge:main] Applying FG3MinecraftTransformer
[Unimined/MappingsProvider] Resolving mappings for source set 'main'
[Unimined/MappingsProvider] Mapping tree initialized, official -> [searge, mojmap, intermediary]
[Unimined/Minecraft :forge:main] Applying run configs
Merging client and server jars...
[Unimined/Forge] transforming minecraft jar for FG3
[Unimined/ForgeTransformer] Applying ATs [META-INF/accesstransformer.cfg]
[Unimined/ModRemapper] Remapping mods from searge/searge to mojmap/intermediary
[Unimined/ModRemapper] No mods found for remapping
[Unimined/Runs] Applying runs
[Unimined/ForgeLike] Non-forge ::api found in forge classpath groups, merging with current (:forge:main), this should've been manually specified with `combineWith`
[Unimined/ForgeLike] Non-forge ::launch found in forge classpath groups, merging with current (:forge:main), this should've been manually specified with `combineWith`
[Unimined/ForgeLike] Non-forge ::main found in forge classpath groups, merging with current (:forge:main), this should've been manually specified with `combineWith`

> Configure project :neoforge
[Unimined/Minecraft :neoforge:main] Applying minecraft config for source set 'main'
[Unimined/Forge] Using FG3 transformer
[Unimined/Minecraft :neoforge:main] Applying FG3MinecraftTransformer
[Unimined/Minecraft :neoforge:main] Applying run configs
Merging client and server jars...
[Unimined/Forge] transforming minecraft jar for FG3
[Unimined/ForgeTransformer] Applying ATs [META-INF/accesstransformer.cfg]
[Unimined/ModRemapper] Remapping mods from mojmap/mojmap to mojmap/intermediary
[Unimined/ModRemapper] No mods found for remapping
[Unimined/Runs] Applying runs
[Unimined/ForgeLike] Non-forge ::api found in forge classpath groups, merging with current (:neoforge:main), this should've been manually specified with `combineWith`
[Unimined/ForgeLike] Non-forge ::launch found in forge classpath groups, merging with current (:neoforge:main), this should've been manually specified with `combineWith`
[Unimined/ForgeLike] Non-forge ::main found in forge classpath groups, merging with current (:neoforge:main), this should've been manually specified with `combineWith`

> Configure project :tweaker
[Unimined/Minecraft :tweaker:main] Applying minecraft config for source set 'main'
[Unimined/Minecraft :tweaker:main] Applying NoTransformMinecraftTransformer
[Unimined/Minecraft :tweaker:main] Applying run configs
[Unimined/ModRemapper] Remapping mods from official/official to mojmap/intermediary
[Unimined/ModRemapper] No mods found for remapping
[Unimined/Runs] Applying runs

> Task :compileApiJava UP-TO-DATE
> Task :processApiResources NO-SOURCE
> Task :apiClasses UP-TO-DATE
> Task :compileSchematica_apiJava UP-TO-DATE
> Task :processSchematica_apiResources NO-SOURCE
> Task :schematica_apiClasses UP-TO-DATE
> Task :compileJava UP-TO-DATE
> Task :processResources NO-SOURCE
> Task :classes UP-TO-DATE
> Task :compileTestJava UP-TO-DATE
> Task :processTestResources NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test UP-TO-DATE

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 8s
7 actionable tasks: 7 up-to-date
```

The all-loader build ended with these verbatim final Gradle summary lines:

```text
Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 4m 43s
36 actionable tasks: 29 executed, 7 up-to-date
```

The gates were run with Java 17, an isolated Gradle home, and `--no-daemon`. The build produced
jars for Fabric, Forge, NeoForge, and tweaker. The Minecraft/in-game suite was not run because
the task explicitly reserves that gate for a human.
