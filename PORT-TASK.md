# Task: port Tenor to Minecraft 26.2

This worktree is branch `tenor/26.2`, created from the local branch `pr5076` — upstream pull request
#5076's head, which is a complete four-loader 26.2 bump. **There is no `upstream/26.2` branch**, so
unlike every other port this one starts from a PR head rather than a maintained branch. That is the
only unusual thing about it.

`gradle.properties` here already declares `minecraft_version=26.2`, `java_version=25`,
`forge_version=65.0.3`, `neoforge_version=11-beta`, `fabric_version=0.19.3`, all four loaders. Those
loader numbers are facts from upstream; keep them unless the build proves one wrong.

Read `PORTING.md` in this directory first — it is the procedure. This file is only what is specific
to 26.2.

## Method

Our feature set lives on `shulker-restock`, the canonical branch. It is additive: 90 files differ
from `upstream/26.1`, overwhelmingly new files. Enumerate rather than guess:

    git diff --name-only upstream/26.1 shulker-restock -- src

Bring them across with `git checkout shulker-restock -- <path>`, then fix what the compiler reports.

**This is the smallest port of the set.** 26.1 → 26.2 is 23 files upstream, and the collision surface
is expected to be three files: `Settings.java`, `Baritone.java`, `BuilderProcess.java`. Both sides are
Java 25 and both have data components, so there is no boundary to cross — unlike the 1.20.x ports.

You also need our `settings.gradle` foojay resolver block, or Gradle cannot resolve a toolchain.

## The one thing to be careful about

Two earlier ports quietly changed behaviour while claiming to be mechanical. The 1.21.1 port dropped
the `EQUIPPABLE` and `WEAPON` checks from `RestockProcess#isJunk` because those components did not
exist there. On 26.2 they **do** exist, so nothing should be dropped — if you find yourself deleting a
guard from `isJunk`, stop and report it instead. That method decides what may be put into a player's
shulker box, and a missing guard there loses their belongings.

## Gates

1. `./gradlew :test` green.
2. `./gradlew build` produces jars for all four loaders.

You may run both. You may NOT run the in-game suite or launch Minecraft — a human does that.
Do not run outside the sandbox; use an isolated `GRADLE_USER_HOME` under /tmp if Gradle needs one.

## Record the port point

Put the exact `shulker-restock` SHA you ported from in `PORT-POINT.md` and in your commit message.
Without it nobody can compute what still needs backporting.

## Output

`PORT-NOTES.md`: what changed beyond mechanical renames, which files actually collided, the verbatim
gate output, and anything you could not verify. If you changed behaviour anywhere, say so under its
own heading — that is the thing most likely to matter later.
