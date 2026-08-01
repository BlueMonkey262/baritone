# Handoff — 2026-08-01

State at `6736a184` on `phase-1/defect-triage`. Working tree clean; nothing pushed since the three
open PRs. Supersedes `TESTING-HANDOFF.md` and `TESTING-HANDOFF-CATALOG.md` for "what to do next";
those remain as history.

## Where things are

`ROADMAP.md` is the plan: Tenor → publicly released mod. Phase 0 and phase 2a are done,
phase 1 (defect debt) is in progress.

**The regression net exists now, and this is the thing to actually rely on.**

- `./gradlew test` — green, 108 tests. CI runs it plus a four-loader compile on every push
  (`.github/workflows/build.yml`), verified green on real runners.
- Curated in-game suite — **33/33 across three clients, ~210s**.
- `scripts/testing/diff_baseline.py` compares a run against `scripts/testing/baseline.json` and
  reports verdict changes, per-scenario ticks, wall clock, and cross-instance disagreement.

Judge a change against the **whole** suite. Twice today a change made its own scenario green while
breaking something else; both times only the full run caught it.

## Naming history

The project was renamed twice on 2026-08-01: to **Continuo** in `137218c4`, then to **Tenor**.

**Release `v0.1.0` is published under the name Continuo** — that is what shipped and its notes and
jar filenames (`continuo-*-0.1.0.jar`) say so. Do not retitle it. The next release is the first
under Tenor and its notes should mention the rename, or users comparing the two will reasonably
think they are different projects.

## Open PRs, stacked, all CI-green, none merged

| PR | Branch | Base |
|---|---|---|
| [#1](https://github.com/BlueMonkey262/baritone/pull/1) | `fix/observer-up-placement` | `shulker-restock` |
| [#2](https://github.com/BlueMonkey262/baritone/pull/2) | `phase-2a/test-signal` | #1 |
| [#3](https://github.com/BlueMonkey262/baritone/pull/3) | `phase-1/defect-triage` | #2 |

`phase-1/defect-triage` has three commits **not yet pushed** (`fcb8291d`, `d0ba7b6a`, `6736a184`).
Pushing them updates #3.

## Do this next, in order

### 1. Push, and re-run the suite once (~10 min)

`git push origin phase-1/defect-triage`, then one curated run. Two changes landed today that touch
the builder's control flow on every tick, and each has exactly one green run behind it. A second run
is the cheapest possible confidence, and `diff_baseline.py` makes the comparison mechanical.

### 2. Fix the two broken scenarios (~30 min, delegable)

Both are registered uncurated and both fail for the *wrong* reason — neither creates the condition it
tests, so neither could catch a regression:

- `restock-multiple-boxes` — **STAGING_FAILED**, the arena never appeared on the client. Check its
  staging commands against this Minecraft version. It got further on an earlier run, so this is
  likely a specific command, not the harness.
- `restock-empty-indexed-box` — completes the ring without ever visiting the stale box, so the
  live-index correction never runs. The stale box has to be the one box selection actually picks
  first; see how candidates are ordered (recorded contents first, then distance).

### 3. Settle `shelter-retreat-distance` (~20 min)

It now reaches its assertion — it previously never received hostile damage, so it had never really
run — and reports: *selected a shelter box 50 blocks away after hostile damage; bound is 12*.

That is a **configuration mismatch, not a bug**. `shelterMaxRetreatDistance` defaults to 64, the
scenario wants 12, and the scenario predates the setting so it never sets it. Decide which is right:
set the setting in the scenario's settings map (most likely), or lower the default. Then F1 is
verified and the scenario can be promoted to curated.

### 4. Then continue the ledger

`DEFECTS.md` is the single source of truth for what is open. Of 31 triaged findings, most were
already fixed; what remains after today:

- **M2** — a box switch does not rescope the old restock path. Small fix, no test.
- **U-local-03** — repeaters need place-then-interact. The largest item: a new builder mechanism, and
  it blocks several entries in `TEST_BACKLOG.md`.
- **H4 residual** — lid clearance still uses `isBlockNormalCube`, so a slab in the lid's swing reads
  as clear.
- **H6 / H8 residuals** — container ownership and quick-move settling are PARTIAL; see their entries.
- **L4** — reroute docs contradict the behaviour.

## Two things worth knowing before you touch anything

**Instrument before theorising.** The observer defect took two rounds of careful static reasoning
that produced a plausible, self-consistent, wrong answer. Six lines of logging settled it in one run.
The harness can print anything you want into the report via `arena.note`.

**Verify what agents report, not just what they produce.** Today alone: a triage said "37 items" when
there were 31; a diagnosis stated a step misleadingly enough to send the fix in the wrong direction;
and a carefully specified change was implemented perfectly and still had to be reverted because the
spec was wrong. Both review concerns raised against the U-local-01 patch turned out to be real bugs
the author confirmed on being asked. Ask.

## Performance is now a stated goal, and is not yet measurable

`FORK-NOTES.md` lists it third, with two rules: measure before and after, treat a slowdown as a
defect. Two real optimizations landed today with **no measurable effect**, because every build
scenario in the suite is tiny — `placeable` never grows enough for the quadratic terms to matter.

**Nothing currently detects a build-scaling regression.** A large-build scenario asserting on tick
count is the missing instrument, and it is the highest-value single addition to the harness. It is
not in `TEST_BACKLOG.md`'s 100 because nobody proposed it.

## Housekeeping left undone

- `TESTING-HANDOFF.md` and `TESTING-HANDOFF-CATALOG.md` should fold into history; this file replaces
  their forward-looking half.
- Stale PrismLauncher clones: `baritone-testing(1)`, `(2)`, `-1` … `-4`, several hundred MB.
- `sol-high-review.md` is gitignored on purpose — it is local-only. `DEFECTS.md` supersedes it.
