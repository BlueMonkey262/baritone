# Task: port Tenor to Minecraft 1.20.1

This one is **not** the mechanical port that 1.21.11 was, and the difference is the point.

Data components were introduced in Minecraft 1.20.5. `upstream/1.20.1` has none. Our tree carries
10 `DataComponents` references across 5 files, and each needs a pre-component equivalent:

    DataComponents.FOOD        (3)   -> Item#isEdible / getFoodProperties
    DataComponents.TOOL        (3)   -> DiggerItem / Tiered
    DataComponents.FIREWORKS   (2)   -> firework NBT
    DataComponents.WEAPON      (1)   -> SwordItem and friends
    DataComponents.EQUIPPABLE  (1)   -> ArmorItem / Equipable

Find them with:

    git grep -n "DataComponents\." shulker-restock -- src

Two of these carry real meaning and must not be approximated casually. `RestockProcess#isJunk` uses
FOOD/TOOL/WEAPON/EQUIPPABLE to decide what may be deposited into a shulker box -- getting it wrong
means the bot files away the player's food or armour. Read that method and its javadoc before
touching it, and preserve its semantics exactly rather than its syntax.

Also note `available_loaders` has no `neoforge` on this branch. That is correct for 1.20.1; do not
add it.

Report the component shim honestly: if any of the five cannot be reproduced faithfully on 1.20.1,
say which and why. A known gap is a finding; a silent approximation is a defect.

## Where you are

You are in a git worktree on branch `tenor/1.20.1`, created from `upstream/1.20.1`. This shares the
`.git` directory with the main checkout, so every one of our branches is reachable from here:
`shulker-restock` is the canonical branch carrying our whole feature set.

Read `PORTING.md` in this directory first. It is the procedure; this file is only what is specific
to this port. If the two disagree, `PORTING.md` is a draft written from one port's experience and
has not itself been reviewed -- trust the repo over the doc, and say so in your notes.

## Method

Our fork is additive: 90 files differ from `upstream/26.1`, +16,025 / -152, overwhelmingly *new*
files. Enumerate them rather than guessing:

    git diff --name-only upstream/26.1 shulker-restock -- src

Bring them across with `git checkout shulker-restock -- <path>`, then fix what the compiler says.
Only three files historically collide, and their changes were mechanical API renames:

  - src/api/java/baritone/api/Settings.java
  - src/main/java/baritone/behavior/InventoryBehavior.java
  - src/main/java/baritone/process/BuilderProcess.java

You also need our build changes, in particular the foojay resolver block in `settings.gradle`.
Without it Gradle cannot find a Java 17 toolchain -- this machine's Fedora packages only JDK 25
and 26, so the build fails during *configuration* with a message about toolchain download
repositories that looks like a local problem and is not.

## gradle.properties

`upstream/1.20.1` currently declares `minecraft_version=1.20.1` and `java_version=17`, with
`available_loaders=fabric,forge,tweaker`. Set `minecraft_version` to the version actually being targeted and
pick loader versions that exist for it. Loader version numbers are facts to look up, not to derive.

## Gates -- do not report success without these

1. `./gradlew :test` green.
2. `./gradlew build` produces jars for every loader in `available_loaders`.

You may run both. You may NOT run the in-game suite, launch Minecraft, or touch a PrismLauncher
instance -- you have no way to run those, and a human does it afterwards.

## Record the port point

The single most useful thing you can leave behind. Our version branches carry no shared ancestry
with the canonical branch, so git cannot compute what still needs backporting unless the starting
commit is written down. Put the exact `shulker-restock` SHA you ported from in your commit message
and in `PORT-POINT.md` in this worktree root.

## Do not

Do not touch other branches or worktrees. Do not push. Do not change behaviour -- this is a port,
not an improvement; if you find a bug, write it in your notes and leave it.

## When you are done

Write `PORT-NOTES.md` here: what you changed beyond mechanical renames, which files actually
collided, anything you were unsure of, and the gate output verbatim. Be honest about what you did
not verify -- an unverified claim recorded as such is useful, and one recorded as success is not.
