# Porting Tenor to a new Minecraft version

This is the procedure for a future maintainer who needs to create another version branch.
The branch strategy and the reasons behind it are in [V0.2-PLAN.md](V0.2-PLAN.md); do not
recreate that discussion here. The in-game harness and its phases are described in §7 of
[FORK-NOTES.md](FORK-NOTES.md#7-in-game-test-harness).

## 1. Start the version branch

Create one branch for the target Minecraft version, named `tenor/<version>`. Derive it from
upstream's branch for that version, then apply Tenor's feature set. `tenor/26.1` is the canonical
branch: fixes land there first and are ported outward. Keeping that ownership clear matters once
the same fix has to exist on several trees.

The fork is additive. Against upstream 26.1, 90 files differ, with +16,025 / −152, overwhelmingly
new files. That is why the version adaptation is expected to be small. For the 1.21.11 → 26.1 port,
the collision surface—the files modified by us that upstream also changed between versions—was
exactly these three files:

`src/api/java/baritone/api/Settings.java`

`src/main/java/baritone/behavior/InventoryBehavior.java`

`src/main/java/baritone/process/BuilderProcess.java`

Use the target version's upstream branch as the starting point rather than trying to make one
source tree serve multiple Minecraft versions.

## 2. Update the build properties

In `gradle.properties`, update `minecraft_version`, `java_version`, and the loader versions:
`forge_version`, `neoforge_version`, and `fabric_version`. The Java version belongs to the
Minecraft version. The 1.21.11 port builds on Java 21; 26.1.2 builds on Java 25. `java_version`
is also the value used by the strict Gradle toolchain in `build.gradle`.

`settings.gradle` carries the foojay resolver so Gradle can download a matching JDK when needed.
This matters on Fedora 44, whose packages do not provide a JDK below 25. Without the resolver,
the build fails during configuration with a message about toolchain download repositories. That
message can look like a local environment problem even when the port itself is correct.

Use the loader versions for the target branch; do not infer them from the Java version. The
compiler and Gradle configuration are the source of truth after the properties are changed.

## 2a. Bring across everything the fork changed, not just `src/`

**This is the step that has already gone wrong.** Every port done on 2026-08-02/03 enumerated the
fork's files with

    git diff --name-only upstream/26.1 shulker-restock -- src

and `-- src` silently excludes `buildSrc/`. Five of the seven branches therefore kept upstream's
`ProguardTask`, which is why the 26.2 port hit ProGuard's missing-`jmods` failure and worked around
it by assembling a JDK under `/tmp` — solving, badly, a problem the canonical branch had already
solved properly.

Enumerate without the path filter and review what comes back:

    git diff --name-only upstream/26.1 shulker-restock

Four areas outside `src/` carry fork changes and all four matter:

| Path | Why it matters |
|---|---|
| `buildSrc/` | `ProguardTask` — packaging fails or silently shrinks against missing classes without it |
| `settings.gradle` | the foojay resolver; without it the build cannot resolve a toolchain at all |
| `gradle.properties` | version and loader properties, which you are editing anyway |
| `scripts/testing/` | the harness runner; needed to run the suite against the new version |

A port that compiles and passes `:test` can still be missing all of these, because none of them is
reachable from a compile error.

## 3. Adapt the source

Compile the new branch and fix what the compiler reports. For the 1.21.11 → 26.1 adaptation, all
changes across `src/` were +74 / −74: symmetric one-line-for-one-line API renames with no logic
change. The observed shapes were:

* `mc.setScreen` → `mc.gui.setScreen`
* `ClickType` → `ContainerInput`
* `Tuple` / `getA()` / `getB()` → `Pair` / `first()` / `second()`

Expect the three collision files named above to need attention. Check each compiler error against
the target version's API and keep the feature logic unchanged while making mechanical adaptations.
If the work turns out to be more than mechanical, stop and re-plan at this point. The first port
is the decision point in the plan; grinding through a larger port would hide a changed assumption.

## 4. Build gates

Run the gates in order. The first confirms the Minecraft-free tests; the second confirms that all
four loaders produce jars; the third is the in-game check that reaches the actual processes.

First run:

```text
./gradlew :test
```

Then build all loaders:

```text
./gradlew build
```

Do not treat a successful compile as runtime evidence. A mixin or process can compile against the
new version and still fail in a live client, which is why the in-game gate is required for each
version.

## 5. Prepare the test instance

Give every version its own PrismLauncher instance, named `tenor-testing-<mcversion>`. The runner
derives the version label from that trailing suffix. Give each instance its own world as well:
worlds are not portable between versions, and a save written by a later version will not open in
an earlier one. Create the world fresh as superflat with cheats enabled. Gamemode does not matter;
the harness issues `gamemode creative` and `gamemode survival` itself.

Before cloning instances, set `pauseOnLostFocus:false` in the **base** instance's `options.txt`.
Clones inherit that file. If it remains true, an unfocused client stops receiving chunks and every
scenario fails staging with “the arena's chunks never loaded”. That is a harness-environment
failure, not evidence of a version incompatibility.

Point the instance at the JDK shipped by PrismLauncher that matches the Minecraft version:
`java-runtime-delta` is Java 21 and `java-runtime-epsilon` is Java 25. The system JDK may not have
the required version.

Never seed a base world from a world that the suite has already run against. Scenarios excavate
their arenas, and the first arena sits on spawn. A post-run world can therefore have no floor where
clients load in, causing them to fall into the void on join. It is recoverable by flying to safe
ground in creative, but starting with a fresh world avoids creating the problem.

## 6. Run and baseline the suite

With the matching jar and instance prepared, run the curated suite using the verified invocation:

```text
python3 scripts/testing/parallel_run.py -n 3 --timeout 1800 --curated \
  --instance tenor-testing-1.21.11 -w "New World" \
  --jar dist/continuo-unoptimized-fabric-<version>.jar
python3 scripts/testing/diff_baseline.py --update
```

For another Minecraft version, replace the instance name and jar version in that invocation. If an
instance name does not end in a version, `--mc-version` overrides the label. Baselines are
per-version, named `baseline-<mcversion>.json`; reports with no version fall back to `baseline.json`.

Record the baseline after the clean run. The first clean 1.21.11 run was 33/33 in 210s, with every
scenario within 4% of the 26.1.2 baseline. The useful signal is the comparison, not the pass count:
the comparison says whether the port is behaviourally equivalent rather than merely compiling.
Read the per-scenario and timing differences before accepting a baseline update.

## 7. Carry fixes between branches

Porting itself is cheap and flat; backporting is the ongoing cost because it scales with Tenor's
development rate. `tenor/1.21.11` was created as a single squashed commit onto upstream's branch,
so it has no shared ancestry with trunk. Consequently, `git cherry` reports every one of our
commits as unported.

Record the trunk commit from which each version branch was ported. That marker makes the unported
set computable. The 1.21.11 branch corresponds to `35881fca` (v0.1.1); its diff against that point
was verified to be pure API adaptation. To see what still needs backporting to the canonical branch:

```text
git log --oneline <port-point>..tenor/26.1 --no-merges
```

Update the marker whenever a fix is backported. Otherwise the next maintainer cannot distinguish
work that belongs on the version branch from work it already contains.

An alternative is to rebuild a version branch as a rebase of Tenor's commit series onto upstream's
branch. That buys mechanical `git cherry-pick` and patch-id deduplication, but requires resolving
conflicts across the series. Choose between the two histories as a judgement call for the branch;
the rebase is useful when the backport burden justifies its one-time conflict cost, not a mandate.
