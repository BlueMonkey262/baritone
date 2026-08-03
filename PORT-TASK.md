# Task: port Tenor to Minecraft 1.21.4

Java 21, all four loaders. 1.21.4 is **after** the 1.20.5 data-component boundary, so it is on the
same side as our canonical tree and should be the cheap kind of port -- mechanical API renames.

**Do not drop guards to make it compile.** The 1.21.1 port removed the `EQUIPPABLE` and `WEAPON`
checks from `RestockProcess#isJunk` because those components did not exist there. Check whether they
exist on 1.21.4; if they do, keep them. That method decides what may be put into a player's shulker
box, and a missing guard there loses their belongings. If you find yourself deleting any check from
`isJunk`, stop and report it rather than doing it quietly.


## Memory discipline -- read this before running anything

This machine went down from memory exhaustion tonight and **cannot be restarted remotely**. An OOM
ends the whole overnight run, not just your task.

- Run only ONE Gradle command at a time. Never two.
- Before each build run `free -g`. If the "available" column is under 8, wait and re-check.
- Always use an isolated Gradle home and `--no-daemon` so nothing lingers:
  `GRADLE_USER_HOME=/tmp/g1214 ./gradlew :test --no-daemon`
- You cannot run Minecraft or the in-game suite; a human does that.

## Method

This worktree is branch `tenor/1.21.4` from `upstream/1.21.4`. Our feature set is on `shulker-restock`,
the canonical branch, and it is additive -- 90 files, overwhelmingly new. Enumerate rather than guess:

    git diff --name-only upstream/26.1 shulker-restock -- src

Bring them over with `git checkout shulker-restock -- <path>`, then fix what the compiler reports.
Three files have collided on every port so far: `Settings.java`, `InventoryBehavior.java`,
`BuilderProcess.java`. You also need our foojay resolver block in `settings.gradle`, or Gradle cannot
resolve a Java 21 toolchain -- this machine has no JDK below 25 installed.

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
