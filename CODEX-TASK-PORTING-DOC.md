# Task: write `PORTING.md`

Write a new file `PORTING.md` in the repo root: instructions for porting this fork to a new
Minecraft version. Audience is a future maintainer (or agent) who has never done one.

Read `V0.2-PLAN.md` and `FORK-NOTES.md` §7 first. `V0.2-PLAN.md` is the strategy; `PORTING.md` is
the procedure. Do not restate the strategy — link to it.

**Do not invent numbers or commands.** Every measurement below was taken from this repo today; use
these and nothing else. If you want to state a fact not listed here, verify it against the repo
first, and if you cannot, leave it out.

## Facts established by the 1.21.11 port (2026-08-02)

Branch model: one branch per Minecraft version, `tenor/<version>`, each derived from upstream's
branch for that version plus our feature set. `tenor/26.1` is canonical — fixes land there first
and are ported outward.

The fork is additive: 90 files differ from upstream 26.1, +16,025 / −152, overwhelmingly new files.
That is why ports are cheap.

Collision surface, measured as (files we modify) ∩ (files upstream changed between versions).
For 1.21.11 → 26.1 it was exactly three:

- `src/api/java/baritone/api/Settings.java`
- `src/main/java/baritone/behavior/InventoryBehavior.java`
- `src/main/java/baritone/process/BuilderProcess.java`

Total per-version adaptation across `src/` was **+74 / −74** — symmetric, i.e. one-line-for-one-line
mechanical API renames with no logic change. Examples of the rename shapes:
`mc.setScreen` → `mc.gui.setScreen`, `ClickType` → `ContainerInput`,
`Tuple`/`getA()`/`getB()` → `Pair`/`first()`/`second()`.

Java versions differ per Minecraft version: 1.21.11 builds on Java 21, 26.1.2 on Java 25. This is
`java_version` in `gradle.properties`, and `build.gradle` pins the Gradle toolchain to it strictly.
`settings.gradle` carries the foojay resolver so Gradle downloads a matching JDK on demand — needed
because Fedora 44 packages no JDK below 25. Without it the build fails during *configuration*, with
a message about toolchain download repositories that looks like a local environment problem rather
than an unverified port.

## The procedure to document

1. Branch from upstream's branch for the target version; apply our feature set.
2. Bump `gradle.properties`: `minecraft_version`, `java_version`, and the loader versions
   (`forge_version`, `neoforge_version`, `fabric_version`).
3. Fix what the compiler reports. Expect the three collision files and mechanical renames.
4. Gate 1: `./gradlew :test` green.
5. Gate 2: `./gradlew build` — all four loaders produce jars.
6. Gate 3: the in-game suite on that version (see below).
7. Record a per-version baseline.

**If step 3 turns out to be more than mechanical, stop and re-plan rather than grinding through.**
That is the plan's stated decision point and it is cheap to honour early.

## Test instance setup — this is where the real cost was

The code port built first try. Both failed suite runs failed for harness-environment reasons.
Document these as a checklist, because each cost a full run:

- One PrismLauncher instance per version, named `tenor-testing-<mcversion>` (the runner derives the
  version label from that trailing suffix).
- Each needs its own world. Worlds are not portable between versions — a save written by a later
  version will not open in an earlier one. Create it fresh: superflat, cheats enabled. Gamemode does
  not matter; the harness issues `gamemode creative` / `gamemode survival` itself.
- `pauseOnLostFocus:false` must be set in the **base** instance's `options.txt`. Clones inherit
  `options.txt` from the base. Left true, unfocused clients stop receiving chunks and every scenario
  fails staging with "the arena's chunks never loaded" — which reads exactly like a version
  incompatibility and is not one.
- PrismLauncher ships its own JDKs (`java-runtime-delta` is 21, `java-runtime-epsilon` is 25). Point
  the instance at the right one; the system JDK may not have the version.
- **Never seed a base world from a world the suite has already run against.** Scenarios excavate
  their arena and the first arena sits on spawn, so a post-run world has no floor where clients load
  in and they drop into the void on join. (Recoverable by flying to safe ground in creative, but do
  not create the situation.)

Runner invocation, verified working:

```
python3 scripts/testing/parallel_run.py -n 3 --timeout 1800 --curated \
  --instance tenor-testing-1.21.11 -w "New World" \
  --jar dist/continuo-unoptimized-fabric-<version>.jar
python3 scripts/testing/diff_baseline.py --update
```

`--mc-version` overrides the label if the instance name does not end in a version. Baselines are
per-version (`baseline-<mcversion>.json`); reports with no version fall back to `baseline.json`.

Result of the first clean 1.21.11 run: **33/33 in 210s**, every scenario within 4% of the 26.1.2
baseline. State that the useful signal is the *comparison*, not the pass count — it is what says the
port is behaviourally equivalent rather than merely compiling.

## Backporting — cover this, it is the ongoing cost

Porting is cheap and flat. Backporting is what accumulates, because it scales with our own
development rate rather than with Mojang's changes.

`tenor/1.21.11` was created as a single squashed commit onto upstream's branch, so there is no
shared ancestry with trunk and `git cherry` reports every one of our commits as unported.

The workable convention: **record the trunk commit each version branch was ported from**, so the
unported set is computable. The 1.21.11 branch corresponds to `35881fca` (v0.1.1), verified by the
diff against it being pure API adaptation. Then:

```
git log --oneline <port-point>..tenor/26.1 --no-merges   # what still needs backporting
```

Document that this marker must be updated whenever a fix is backported, and note the alternative —
rebuilding a version branch as a rebase of our commit series onto upstream's branch, which buys
mechanical `git cherry-pick` and patch-id dedup at the cost of resolving conflicts across the
series. Present it as a judgement call, not a mandate.

## Style

Match the existing docs in this repo: prose that explains *why*, not bullet soup. `FORK-NOTES.md` is
the model. Keep it to roughly 100–150 lines. Every LGPL-headed source file carries the licence block;
markdown files do not, so no header needed.

Do not modify any file other than `PORTING.md`.
