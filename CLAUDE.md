# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A **private fork** of [cabaletta/baritone](https://github.com/cabaletta/baritone) (Minecraft pathfinding bot),
branched from upstream `26.1` (Minecraft 26.1.2, Java 25, mojmap via unimined).

Everything this fork adds or changes relative to upstream is documented in **FORK-NOTES.md** — read it before
touching `RestockProcess`, `ShelterProcess`, `BuilderProcess`, `BackfillProcess`, `ContainerInteractionBehavior`,
or `ThreatBehavior`. It records not just what changed but *why*, including invariants that are easy to break.

## Build & test

```bash
./gradlew build                     # builds common + every loader in gradle.properties#available_loaders
./gradlew :test                     # unit tests (root project only; loader subprojects have no tests)
./gradlew :test --tests 'baritone.process.RestockProcessTest'
./gradlew :test --tests '*.fetchTarget*'
./gradlew :fabric:build             # single loader (fabric | forge | neoforge | tweaker)
```

Artifacts land in `dist/` (api / standalone / unoptimized variants per loader, plus ProGuard mapping files).
`createDist` depends on `proguard`, which needs a JDK with `jmods/` or a runtime image it can extract modules from.

**Never overwrite a jar in `dist/` (or an installed mods jar) while Minecraft is running.** Loaders read classes
lazily from it for the whole session; replacing the file mid-run surfaces as `ZipException: invalid LOC header`
and a "Network Protocol Error" disconnect that looks like a server fault but isn't.

### Installing a build

When asked to install the mod, copy the freshly built **fabric** jar from `dist/` into both PrismLauncher
instances (both are Fabric, mods dir is `<instance>/minecraft/mods/`):

```
/home/eli/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/26.1.2_copy/minecraft/mods/
/home/eli/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/26.1.2-creative/minecraft/mods/
```

Match the variant each instance is already running rather than picking one: `26.1.2_copy` uses
`baritone-standalone-fabric-*.jar`, `26.1.2-creative` uses `baritone-unoptimized-fabric-*.jar`. Delete the old
`baritone-*-fabric-*.jar` in each mods dir as part of installing — jar names carry the git describe version, so
leaving the previous one behind loads two Baritones. Confirm Minecraft is not running first (see the jar-overwrite
warning above).

The test-harness instance is separate and takes the **unoptimized** jar, so stack traces and harness class names
in reports stay readable:

```
/home/eli/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/baritone-testing/minecraft/mods/
```

Note that a dirty working tree does not change the `git describe` version, so rebuilding without committing
produces a jar with the *same filename* as the installed one. Overwrite deliberately, and don't assume a
differing filename means a differing build.

### In-game test harness

`#testing all` runs scenarios against a live singleplayer world (stage an arena with vanilla
commands → run Baritone in survival → assert on world state) and writes a report to
`<instance>/minecraft/baritone/testing/`, with `latest.md` and `latest.json` always pointing at the
most recent run. `#testing autorun` arms it to run on next world join and then quit the game, which
makes an unattended build → launch → read-report loop possible via
`flatpak run org.prismlauncher.PrismLauncher --launch <instance> --world <world>`.

This is the only automated coverage that reaches processes, restocking, or anything else that needs
a world and sustained control. See FORK-NOTES.md §7 before adding a scenario — particularly why
verdicts read world state rather than chat, and why staging goes through commands rather than the
integrated server's level object.

**Two gates.** CI (`.github/workflows/build.yml`) runs `:test` and compiles all four loaders on
every push; that is everything a machine can check unattended. The in-game suite is a *manual* gate
before a release or a risky merge:

```bash
python3 scripts/testing/parallel_run.py -n 3 --timeout 1800 --curated --jar dist/baritone-unoptimized-fabric-<version>.jar
python3 scripts/testing/diff_baseline.py    # verdict + timing changes vs scripts/testing/baseline.json
```

Judge a change against the **whole** suite, not the scenario you were fixing. A green target
scenario is not evidence of a good fix: one change here made its own scenario pass while doubling
suite wall clock and breaking an unrelated one. `diff_baseline.py` reports tick counts and wall
clock for exactly that reason — performance is a stated goal (FORK-NOTES.md), so a slowdown is a
defect. Refresh the baseline with `--update` only when a change is meant to move it.

### Test environment limits

Tests run on a plain JVM with **no Minecraft bootstrap**. Anything that touches the registries or item components
(`new ItemStack(...)`, `Items.X.getDefaultMaxStackSize()`) throws `NullPointerException: Components not bound yet`.
Three tests in `RestockBoxEstimateTest` currently fail for exactly this reason — that failure is pre-existing, not
something you introduced. Write new tests against pure static helpers (see `RestockProcessTest#fetchTarget`, or
`ProcessSchedulerTest`, which is why `ProcessScheduler` was split out of `PathingControlManager` in the first place).

JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`) — not JUnit 5.

## Source set layout

Gradle wires four source sets in the root project, and the split matters:

| Source set | Contents | Rule |
|---|---|---|
| `src/api` | `baritone.api.*` — interfaces, `Settings`, `BaritoneAPI` | Not obfuscated in `api` jars; other mods compile against it. Keep it implementation-free. |
| `src/main` | `baritone.*` implementation | Depends on `api`; `api` must never depend back. |
| `src/launch` | Mixins (`baritone.launch.mixins.*`) | The only place that patches vanilla classes. |
| `src/schematica_api` | Third-party schematic mod stubs | Compile-only shims. |

Loader subprojects (`fabric/`, `forge/`, `neoforge/`, `tweaker/`) are thin: they shade the root output and add
loader entrypoints. Real logic never goes there.

## Architecture

Everything hangs off `Baritone` (`src/main/java/baritone/Baritone.java`), one instance per player context. Its
constructor is the authoritative registration order for both behaviors and processes.

**Mixins → event bus → behaviors.** `src/launch` mixins fire into `GameEventHandler`, which fans out to every
registered `IBehavior` (`TickEvent`, `PacketEvent`, `ChatEvent`, `BlockInteractEvent`, …). Behaviors are always
listening, whether or not Baritone is in control — that's why damage tracking lives in `ThreatBehavior` and
container-packet tracking in `ContainerInteractionBehavior`, rather than in the processes that consume them.

**Processes and arbitration.** An `IBaritoneProcess` reports `isActive()` and a `priority()`, and each tick returns
a `PathingCommand` (goal + `PathingCommandType`). `ProcessScheduler` (pure, Minecraft-free, unit-tested) sorts
active processes by priority and asks each in turn; the first non-`DEFER` answer wins, and — unless the winner is
`isTemporary()` — every lower-priority process gets `onLostControl()`. `PathingControlManager` then applies that
command to `PathingBehavior`. Returning `DEFER` is the idiom for "hand off to the process below me" (e.g.
`ShelterProcess` defers to `RestockProcess` rather than duplicating container logic).

Two invariants that silently corrupt state if broken:
- `RestockProcess` must be registered **before** `BuilderProcess` — `registerProcess` calls `onLostControl()`
  immediately, and `BuilderProcess#onLostControl` resets the restock process.
- `RestockProcess` and `ShelterProcess` must keep `isTemporary() == true`. `BuilderProcess#onLostControl` nulls
  the schematic, so a non-temporary interruption destroys the in-progress build.

**Pathing.** `pathing/calc` is A* over `Movement` implementations in `pathing/movement/movements`; `ActionCosts`
supplies the cost model and `pathing/precompute` caches per-block-state decisions. `BlockStateInterface` is the
thread-safe world read used off the main thread — never touch `mc.level` directly from pathfinding code.

**Commands.** `ICommand` implementations in `command/defaults`, registered in `DefaultCommands#createAll`; a new
command needs an entry there or it doesn't exist. Argument parsing goes through `IArgConsumer` /
`command/argparser`, and commands should implement tab completion, since `#help` renders it.

**Settings.** Every setting is a `public final Setting<T>` field in `api/Settings.java`; the field name *is* the
in-game name, and the javadoc above it *is* the `#help` text. Serialization (`SettingsUtil`) is reflective over
those fields, so adding one requires nothing else — but renaming one breaks users' `settings.txt`. New behavior
in this fork is expected to default to off/upstream-equivalent (`shelterOnAttack`, `shulkerDump`) so the master
switch reproduces upstream exactly.

**Per-world state.** `WorldProvider` / `WorldData` scope caches to server *and dimension* under
`minecraft/baritone/<server>/<dimension>/` (chunk cache, waypoints, and this fork's `restock/boxes.mp4`).
Persisted item references use registry keys, not numeric ids, and unresolvable entries are dropped on load rather
than failing the file.

## Conventions

- Every file carries the LGPL header block; copy it into new files.
- Interacting with the world means **real client→server packets** — forced input via `InputOverrideHandler`,
  `windowClick` for container transfers, genuine right-clicks gated by `RotationUtils.reachable(...)`. Never write
  directly into the player's inventory or fake a server-side effect; the server's own range and validity checks
  are the point.
- `Helper#logDirect` for user-facing chat output.
- The repo root holds several working review docs (`UPSTREAM_BUG_BACKLOG.md`, `UNTRACKED_BUG_REVIEW.md`,
  `TEST_COVERAGE_REVIEW.md`, `sol-high-review.md`) — known-issue inventories, not specs.
