# Tenor 1.20.1 port notes

## Source point

The additive Tenor source set was ported from `shulker-restock` at:

`e7eea59be5e1046d9ea1f60ef8d2a0feff546026`

The port brought across all 90 source paths in the canonical feature diff. The target branch's 1.20.1 resource metadata and loader configuration were retained. `available_loaders` remains `fabric,forge,tweaker`; no NeoForge loader was added.

## Changes beyond mechanical renames

- Added the Foojay toolchain resolver to `settings.gradle`, allowing the build to provision a matching JDK.
- Merged the Tenor settings, restock APIs/processes, shelter/testing processes, torch-grid schematic/commands, process scheduling, pickup behavior, and associated tests into the 1.20.1 API.
- Restored the target branch's 1.20.1 implementations of `CalculationContext` and `ToolSet`, then reapplied the Tenor water-cost and item-saver/spent-tool changes. The canonical versions used post-1.20.1 enchantment and item-component APIs that do not exist in this target.
- Adapted the rendering path in `SelCommand`, world-data construction in `WorldData`, and 1.20.1 client/testing APIs in `TestingBehavior` and `ShelterProcess`.
- Preserved the target's 1.20.1 build metadata: Minecraft 1.20.1, Java 17, Fabric 0.14.18, Forge 47.0.1, and the existing Baritone artifact naming.

## Collision and compatibility handling

The three historical manual-merge collisions were:

- `src/api/java/baritone/api/Settings.java`
- `src/main/java/baritone/behavior/InventoryBehavior.java`
- `src/main/java/baritone/process/BuilderProcess.java`

Other copied files that needed target-version API adaptation included `EatBehavior`, `RestockProcess`, `ContainerInteractionBehavior`, `RestockBoxCollection`, `PlacementCatalog`, `PlacementRules`, `TestingBehavior`, `ShelterProcess`, `SelCommand`, `WorldData`, `ToolSet`, `CalculationContext`, and `IRestockBox`.

The canonical code referenced 1.20.5+ `DataComponents` for FOOD, TOOL, FIREWORKS, WEAPON, and EQUIPPABLE. The 1.20.1 equivalents used here are:

- FOOD: `Item.isEdible()` and `Item.getFoodProperties()`.
- TOOL: the target's `DiggerItem`/`PickaxeItem` hierarchy and existing 1.20.1 tool logic.
- FIREWORKS: the target's existing `Fireworks` NBT representation in `ElytraBehavior`.
- WEAPON: `SwordItem`, `TridentItem`, and `ProjectileWeaponItem` family checks.
- EQUIPPABLE: the 1.20.1 `Equipable` interface, covering vanilla armor, elytra, shields, and other equipable items.

These preserve vanilla 1.20.1 semantics. There is no generic pre-component equivalent for an arbitrary modded item whose only signal would be a custom component marker; such items may not be recognized by the family-based restock-value shim. No `DataComponents` references remain in the ported source.

## Verification

The gates were run with the downloaded Adoptium JDK 17 and an isolated Gradle user home. The in-game suite, Minecraft, and PrismLauncher were not run, as required.

### `./gradlew :test --no-daemon`

The successful gate output was:

```text
> Task :test

BUILD SUCCESSFUL in 34s
7 actionable tasks: 3 executed, 4 up-to-date
```

### `./gradlew build --no-daemon`

The successful all-loader gate output was:

```text
> Task :tweaker:createDist
614922e25c84f58a8d13646790a33fa59e92c709  baritone-api-fabric-1.10.3-2-g8545600b-dirty.jar
37fa0a0095ea592ef3ff3c5227fa958742018754  baritone-standalone-fabric-1.10.3-2-g8545600b-dirty.jar
72eadebcb72c195274ac0b82139dfa6987af4ad9  baritone-unoptimized-fabric-1.10.3-2-g8545600b-dirty.jar
ad47634d99abb7104250be47350e9dfb69d89cd1  baritone-api-forge-1.10.3-2-g8545600b-dirty.jar
ffa152edb3093fa4d63eba1d11e4725cf2e45cb5  baritone-standalone-forge-1.10.3-2-g8545600b-dirty.jar
e4cbd35135bcdb34dbc5b01ccc910fd52627c055  baritone-unoptimized-forge-1.10.3-2-g8545600b-dirty.jar
1f82114bc74c963585591231c326e1594af8e752  baritone-api-1.10.3-2-g8545600b-dirty.jar
1f8f5aaea0be7f4c77b38afd535021735d65dc32  baritone-standalone-1.10.3-2-g8545600b-dirty.jar
f343b55ee608a9b79c7212f86632aab55f1d76cb  baritone-unoptimized-1.10.3-2-g8545600b-dirty.jar

BUILD SUCCESSFUL in 3m 50s
29 actionable tasks: 22 executed, 7 up-to-date
```

`git diff --check` is clean. The working tree contains the intended port changes plus the supplied task/instruction files and these two notes files; no other branches or worktrees were changed.
