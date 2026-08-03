# Port notes

## Port point

This port was taken from `shulker-restock` at exact SHA
`e7eea59be5e1046d9ea1f60ef8d2a0feff546026`.

## Changes

- Imported the additive feature set: 90 source files from the canonical branch, including
  restocking, shelter, threat handling, torch-grid building, process scheduling, and the
  scenario/test support code.
- Applied the 1.21.1 API equivalents mechanically: `ContainerInput` to `ClickType`,
  `Identifier` to `ResourceLocation`, later inventory accessors to the 1.21.1 inventory
  fields, later effect names to `DIG_SPEED`/`DIG_SLOWDOWN`, and the corresponding packet,
  world-height, daylight, and renderer method signatures.
- Added the Java 21 Foojay resolver in `settings.gradle` and set the 1.21.1 Minecraft,
  Fabric, Forge, NeoForge, and Java toolchain properties. The NeoForge property is `65`
  because this branch's existing build logic supplies the `21.1.` prefix.
- Updated loader metadata to target Minecraft 1.21.1 and identify the project as Continuo.

## Collisions and compatibility decisions

The three files identified by the port procedure actually collided:

- `src/api/java/baritone/api/Settings.java`
- `src/main/java/baritone/behavior/InventoryBehavior.java`
- `src/main/java/baritone/process/BuilderProcess.java`

Their existing 1.21.1 APIs were retained while the canonical feature changes were merged.
Additional compatibility edits were required in feature files for APIs that do not exist in
1.21.1: `WorldData` keeps the target two-argument constructor, `ToolSet` keeps the target
`TieredItem` material-cost logic, and `RestockProcess` omits the unavailable `EQUIPPABLE` and
`WEAPON` data components from its block-item junk check.

The existing 1.21.1 Unimined/build pipeline was retained; changing mappings or upgrading its
build plugins was unnecessary once the target loader coordinates and Foojay resolver were in
place.

## Gates

The host default JDK is too new for this Gradle/Unimined setup, so both gates were run with the
Java 21 toolchain provisioned by the Foojay resolver:

`JAVA_HOME=/home/eli/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :test`

Verbatim successful gate output:

```text
> Task :test

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

BUILD SUCCESSFUL in 15s
7 actionable tasks: 3 executed, 4 up-to-date
```

`JAVA_HOME=/home/eli/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew build`

Verbatim successful gate output:

```text
Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.7/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 5m 47s
36 actionable tasks: 29 executed, 7 up-to-date
```

The build produced distributions for Fabric, Forge, NeoForge, and tweaker. ProGuard emitted
the existing duplicate-library and unknown-class notes during packaging; they did not fail the
build. No in-game suite, Minecraft launch, or PrismLauncher instance was run, so runtime and
loader behavior remain unverified here.
