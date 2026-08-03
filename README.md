# Tenor

**Tenor keeps a long Minecraft job running when it would otherwise stop.**

Baritone is a very good pathfinding bot, but leave it building something large and you come back to
a bot standing still: out of materials, inventory full, or being hit by a zombie. Tenor adds the
parts that keep it working — it fetches more materials from shulker boxes you've registered,
takes a trip to unload when it fills up, and retreats to shelter when something attacks it. It also
fixes a number of upstream defects that turn a long build into a babysitting exercise.

> **Tenor is an unofficial fork of [Baritone](https://github.com/cabaletta/baritone).** It is not
> affiliated with, endorsed by, or supported by that project. **Please do not report Tenor bugs
> to cabaletta/baritone** — open an issue here instead.

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-LGPL--3.0%20with%20anime%20exception-green.svg" alt="License"/></a>
</p>

**Minecraft 26.1.2 only.** One version is supported at a time. Fabric, Forge, NeoForge and tweaker
builds are produced from the same source.

## What it adds over Baritone

- **Automatic restocking.** Register shulker boxes near your build with `#addbox`. When `#build`
  runs short of a material it walks to a box, takes what it needs, and carries on instead of
  stalling. If nothing can supply a material, it plans around those blocks and finishes the rest.
- **Capacity-aware unloading.** When the inventory fills, it makes a dedicated trip and picks the
  box by how much room the index says it has, not by which one is nearest.
- **Sheltering.** Off by default. When something hostile hits you repeatedly, it retreats to a
  registered box, unloads, and tries to sleep through the night.
- **Fixes for things that stall long jobs** — the builder filling in its own path, backfill fighting
  the builder, water costing the same as dry land, unreachable targets replanned forever.

Everything new defaults to off or to upstream-equivalent behaviour, so the feature switches off
cleanly if you want plain Baritone.

See **[FORK-NOTES.md](FORK-NOTES.md)** for the full list and the reasoning behind each one.

## Getting started

1. Install the jar for your loader into your mods folder — see **[SETUP.md](SETUP.md)**.
2. In game, type `#help`.
3. For restocking, look at a shulker box and type `#addbox`, then `#set allowInventory true`, then
   `#build <schematic>`.

`allowInventory` matters: restocked items land in the main inventory, and the builder can only place
from the hotbar. Tenor warns once if it notices this.

- [Chat commands](USAGE.md)
- [Pathing features](FEATURES.md)
- [What differs from Baritone](FORK-NOTES.md)
- [Known defects](DEFECTS.md)

## Using it as a library

The API is unchanged from Baritone and lives in `baritone.api`, deliberately unobfuscated in the
`api` jars. Tenor is a drop-in replacement: mods that compile against Baritone's API work against
Tenor without modification.

```java
BaritoneAPI.getSettings().allowSprint.value = true;
BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;
BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ(10000, 20000));
```

Anything outside `baritone.api` is not part of the supported surface.
[Upstream's Javadocs](https://baritone.leijurv.com/) still apply to the shared API.

## Building

```bash
./gradlew build          # all loaders; artifacts land in dist/
./gradlew :fabric:build  # one loader
./gradlew :test          # unit tests
```

There is also an in-game test harness that runs scenarios against a live world — see
[FORK-NOTES.md §7](FORK-NOTES.md). It is the only automated coverage that reaches the processes,
because they only mean anything with a world and several thousand ticks of sustained control.

## A word on servers

Tenor interacts with the world through real client→server packets — forced input, genuine
right-clicks gated on reach, real container click packets. Nothing is written into your inventory
behind the server's back, and the server's own range and validity checks always apply.

That does not make it allowed. Many servers forbid automation. Check the rules of the server you
play on; that is on you, not on this project.

## Licence and credit

Tenor is licensed under the **LGPL-3.0**, the same licence as Baritone. See [LICENSE](LICENSE)
and [NOTICE](NOTICE).

The overwhelming majority of this codebase is the work of
[leijurv](https://github.com/leijurv/), [Brady](https://github.com/bhlowe), and the Baritone
contributors. Tenor is a set of changes on top of years of their work, and the pathfinder — the
hard and clever part — is theirs.

The name is from *tenor*, the voice range next to baritone.
