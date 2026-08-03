Task: port this fork (Tenor, in ~/Projects/baritone) to Minecraft 1.21.11 on a new branch.

This is step 2 of V0.2-PLAN.md — read that file first, especially §2 and §3. It is a deliberately
cheap probe: if the port turns out to be much larger than the plan predicts, that is the finding and
you should stop and report rather than grinding through it.

## Context you need

The fork currently targets 26.1.2. It is almost entirely *additive* relative to upstream: 90 files,
+16,025 insertions, only 152 deletions against `upstream/26.1`. That is why a version port is
expected to be cheap — we add files rather than editing upstream's.

Measured facts, do not re-derive them:
- `upstream/1.21.11` -> `upstream/26.1` is 35 files, +93/-97.
- Exactly THREE files are both changed by upstream across that range and modified by our fork:
  - `src/api/java/baritone/api/Settings.java`
  - `src/main/java/baritone/behavior/InventoryBehavior.java`
  - `src/main/java/baritone/process/BuilderProcess.java`
- Upstream's cross-version changes are mechanical API renames, e.g. `ClickType` -> `ContainerInput`,
  `Tuple`/`getA()`/`getB()` -> `Pair`/`first()`/`second()`, `mc.setScreen` -> `mc.gui.setScreen`.
  Going to 1.21.11 means applying these in REVERSE (1.21.11 is the older API).

## What to do

Work only on a new branch. Trunk is `shulker-restock` and must be left untouched.

    cd ~/Projects/baritone
    git checkout -b tenor/1.21.11 upstream/1.21.11
    git diff upstream/26.1 shulker-restock > /tmp/tenor-featureset.patch
    git apply -3 /tmp/tenor-featureset.patch

`git apply -3` leaves conflict markers where it cannot merge. Resolve them. Expect roughly the three
files above; if you get substantially more, that is a finding — report it before continuing.

Then fix `gradle.properties`. The patch will have brought 26.1.2's values across; they must go back
to 1.21.11's, while KEEPING our fork's `archives_base_name`:

    java_version=21
    minecraft_version=1.21.11
    forge_version=61.1.5
    neoforge_version=42
    fabric_version=0.18.5
    mod_version=1.17.0

Leave `archives_base_name=continuo` and `available_loaders` exactly as the fork has them.

## The Java 21 risk — report on this explicitly

Our fork's code was written against Java 25. 1.21.11 builds on Java 21. Any language feature newer
than 21 that our code uses will fail to compile and will need rewriting to a Java 21 equivalent.

I do not know whether we use any. Finding out is part of this task. If you hit such a construct:
rewrite it to the Java 21 equivalent, preserving behaviour exactly, and list every instance in your
report. Do not silently change semantics to make something compile.

## Gates — both must pass

    ./gradlew :test          # expect 115 tests, 0 failures
    ./gradlew build          # all four loaders must compile

The unit tests run on a plain JVM with no Minecraft bootstrap. Three tests in `RestockBoxEstimateTest`
fail for that reason on ALL branches — that failure is pre-existing and is not yours. Everything else
must be green.

## Rules

- Do not modify behaviour. This is a port. If something looks like a bug, note it in your report and
  leave it alone.
- Do not "improve", refactor, reformat, or update anything not required to compile on 1.21.11.
- Do not touch any branch other than `tenor/1.21.11`. Do not push. Do not create pull requests. Do
  not delete branches.
- Do not run the in-game test harness; it needs a Minecraft client and is not your gate.
- Every file in this repo carries an LGPL header. Preserve them.
- You may read `upstream/1.21.11` freely — it is the reference for what the older API looks like.

## Report

When done, report:

1. Which files conflicted, and how you resolved each. Be specific about the API renames you reversed.
2. Every Java-25-only construct found, where, and what you rewrote it to. Say "none found" if none.
3. Exact output of both gate commands.
4. Anything you changed that was NOT a pure API rename — these are where a port silently becomes a
   behaviour change, and I want to look at each one.
5. Anything that surprised you or that you were unsure about. Uncertainty reported is useful;
   uncertainty hidden is how a bad port ships.

Do not commit unless both gates pass. If they pass, make ONE commit on `tenor/1.21.11` with a message
describing the port. If they do not pass, leave the tree dirty and report what is broken.
