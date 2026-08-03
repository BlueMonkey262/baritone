# Port notes

## Port point

This port applies the feature delta from `upstream/26.1` through the exact
`shulker-restock` source point:

`e7eea59be5e1046d9ea1f60ef8d2a0feff546026`

The SHA is also recorded in `PORT-POINT.md` and in the commit message.

## Port summary

The Tenor feature delta was ported to Minecraft 1.21.4 across the Fabric,
Forge, NeoForge, and Tweaker loaders. The port includes shulker-box restocking
and deposit behavior, inventory/tool safety, builder placement and reroute
changes, shelter and auto-eat behavior, torch-grid selection support, process
scheduling, and the testing harness additions.

The 1.21.4 build uses Java 21. Gradle's Foojay resolver is enabled in
`settings.gradle` so the version-specific toolchain can be provisioned.

## Collisions and API adaptations

The three planned collision areas were merged against the 1.21.4 baselines:

- `Settings.java`: retained the 1.21.4 settings structure while adding the
  feature-delta settings.
- `InventoryBehavior.java`: retained the target inventory implementation and
  added spent-tool protection and throwaway restrictions.
- `BuilderProcess.java`: retained the target builder baseline and integrated
  the feature-delta placement, protection, reroute, restock, and safety logic.

Additional structural merges were required in `WorldData.java` and
`SelCommand.java`, where the target branch had diverged from the source point.
The 1.21.4 API differences were adapted directly: `ClickType` replaced the
newer container input type, `ResourceLocation` replaced `Identifier`, target
inventory fields replaced newer inventory accessors, and the target world
disconnect/day APIs were retained.

## Behaviour changes

### EQUIPPABLE guard retained

The `EQUIPPABLE` guard remains in `RestockProcess.isJunk`. This is present in
1.21.4 and prevents wearable blocks such as carved pumpkins from being
deposited as junk.

### WEAPON guard omitted on 1.21.4

The `WEAPON` guard is omitted because `DataComponents.WEAPON` does not exist
in 1.21.4. This omission is vanilla-inert: `isJunk` first requires the item to
be a `BlockItem`, and no vanilla `BlockItem` carries the `WEAPON` component.
Only a hypothetical modded block item carrying that newer component would
behave differently. No class-based substitute was introduced.

## Verification

Both requested gates passed with the installed Java 21 toolchain after checking
available memory before each run. The successful commands and final output
were:

```text
JAVA_HOME=/home/eli/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 PATH=/home/eli/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin:$PATH GRADLE_USER_HOME=/tmp/g1214 ./gradlew :test --no-daemon

BUILD SUCCESSFUL in 18s
7 actionable tasks: 3 executed, 4 up-to-date
```

```text
JAVA_HOME=/home/eli/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 PATH=/home/eli/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin:$PATH GRADLE_USER_HOME=/tmp/g1214 ./gradlew build --no-daemon

BUILD SUCCESSFUL in 3m 35s
36 actionable tasks: 29 executed, 7 up-to-date
```

The build produced distributions for all four configured loaders. The task
does not request the Minecraft/in-game suite, so it was not run.
