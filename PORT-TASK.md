# Task: port Tenor to Minecraft 1.20.4

Java 17, all four loaders. **This one crosses the data-component boundary**: components arrived in
1.20.5, so `upstream/1.20.4` has none. Our tree carries 10 `DataComponents` references across 5 files
and each needs a pre-component equivalent:

    DataComponents.FOOD        (3)   -> Item#isEdible / getFoodProperties
    DataComponents.TOOL        (3)   -> DiggerItem / Tiered
    DataComponents.FIREWORKS   (2)   -> firework NBT
    DataComponents.WEAPON      (1)   -> SwordItem and friends
    DataComponents.EQUIPPABLE  (1)   -> ArmorItem / Equipable

    git grep -n "DataComponents\." shulker-restock -- src

A port to 1.20.1 has already done this translation; it is on branch `tenor/1.20.1` and you may read
it as a reference -- but it is **unaudited**, so copy its reasoning only where you agree with it.

`RestockProcess#isJunk` is the one that matters. Preserve its semantics exactly rather than its
syntax: it must still refuse food, tools, weapons and wearables. Note `isJunk` tests
`instanceof BlockItem` first, so armour is already excluded -- the case that actually needs the
EQUIPPABLE equivalent is a *wearable block* such as a carved pumpkin.

Report honestly if any of the five cannot be reproduced faithfully on 1.20.4: a known gap is a
finding, a silent approximation is a defect.


## Memory discipline -- read this before running anything

This machine went down from memory exhaustion tonight and **cannot be restarted remotely**. An OOM
ends the whole overnight run, not just your task.

- Run only ONE Gradle command at a time. Never two.
- Before each build run `free -g`. If the "available" column is under 8, wait and re-check.
- Always use an isolated Gradle home and `--no-daemon` so nothing lingers:
  `GRADLE_USER_HOME=/tmp/g1204 ./gradlew :test --no-daemon`
- You cannot run Minecraft or the in-game suite; a human does that.

## Method

This worktree is branch `tenor/1.20.4` from `upstream/1.20.4`. Our feature set is on `shulker-restock`,
the canonical branch, and it is additive -- 90 files, overwhelmingly new. Enumerate rather than guess:

    git diff --name-only upstream/26.1 shulker-restock -- src

Bring them over with `git checkout shulker-restock -- <path>`, then fix what the compiler reports.
Three files have collided on every port so far: `Settings.java`, `InventoryBehavior.java`,
`BuilderProcess.java`. You also need our foojay resolver block in `settings.gradle`, or Gradle cannot
resolve a Java 17 toolchain -- this machine has no JDK below 25 installed.

Read `PORTING.md` in this directory for the full procedure.

## Gates

1. `./gradlew :test` green.
2. `./gradlew build` produces jars for every loader in `available_loaders`.

## Record the port point

Put the exact `shulker-restock` SHA you ported from in `PORT-POINT.md` and in your commit message.
Without it nobody can compute what still needs backporting.

## Output

`PORT-NOTES.md`: what changed beyond mechanical renames, which files actually collided, verbatim gate
output, and **every behaviour change under its own heading**. Commit when the gates pass.
