# Handoff — 2026-08-03 (second)

State: **v0.2 is functionally complete and unreleased.** Trunk is `shulker-restock`, clean, gate
green, fully pushed. 38 branches on origin. Supersedes the earlier 2026-08-03 handoff.

## Three things are waiting on a decision, not on work

1. **A batched verification run.** Everything pending needs exactly one launch: pass 2's six
   corrections, wave 4's fifteen first-timers, the curated gate, and the three deliberately-red
   scenarios. The jar is built: `dist/tenor-unoptimized-fabric-0.1.1-23-gcab3c2f8.jar`. Eli asked
   that game launches be batched — Minecraft windows steal focus — so do not run these piecemeal.
2. **Push `ci/version-matrix`.** Committed, not pushed, because pushing triggers Actions runs across
   branches. Nothing proves a workflow change until it runs.
3. **Tag and release.** Nothing technical blocks it.

## Naming history

The project was renamed twice on 2026-08-01: to **Continuo** in `137218c4`, then to **Tenor**.

**Releases `v0.1.0` and `v0.1.1` are published under the name Continuo** — that is what shipped, and
their notes and jar filenames say so. Do not retitle them. v0.2 is the first release under Tenor and
its notes should mention the rename, or users comparing them will reasonably think these are
different projects.

## What v0.2 is

Seven Minecraft versions, each with a branch that builds, passes both gates, and **has been run in a
real world**:

| Version | Branch | Curated | Baseline |
|---|---|---|---|
| 26.1.2 | `shulker-restock` (canonical) | 33/33 | `baseline.json` |
| 1.21.11 | `tenor/1.21.11` | 33/33 | recorded |
| 26.2 | `tenor/26.2` | 33/33 | recorded |
| 1.21.1 | `tenor/1.21.1` | 33/33 | recorded |
| 1.21.4 | `tenor/1.21.4` | 32/33 | recorded |
| 1.20.1 | `tenor/1.20.1` + `fix/restock-1.20.1` | 33/33 | recorded |
| 1.20.4 | `tenor/1.20.4` | 33/33 | recorded |

1.21.4's single failure is T-02, the intermittent that also occurs on 26.1.2 and 26.2. It is not
specific to that version.

Also on trunk: the Tenor rename, `archives_base_name=tenor`, `mod_version=0.2.0`, the multi-version
goal in FORK-NOTES, the raw ore/flint deposit feature, tool swap-back, and 91 uncurated scenarios.

## What is left, in order

**1. Tag and release.** Nothing technical blocks this. `build.gradle` already derives the version
from `git describe` when it finds a `v`-prefixed tag, so tagging `v0.2.0-mc26.1.2` produces
`tenor-standalone-fabric-0.2.0-mc26.1.2.jar` with no build change. One release per version.

**2. Finish validating the scenarios.** 106 are registered; **35 have ever run.** Of those, 18 passed
first time, 15 were fixed across two correction passes, 3 are deliberately red (below). The other
**71 are untested claims**: they compile and follow every convention, but nothing demonstrates they
can fail for the right reason. That is the gap between "we have a hundred scenarios" and "we have a
hundred tests", and it is the single largest piece of remaining work.

**Do not write more scenarios until these are validated.** ~120 backlog proposals remain in
`TEST_BACKLOG_V2.md` and it is tempting to keep going, but authorship has not been the constraint
since wave 2. Producing claims faster than they can be checked is what put 17 of 35 in the failure
column.

*A measurement worth completing:* waves 1-3 were written from the backlog by an author who had never
seen a run, and failed 17 of 35 on first contact. Wave 4 was the first briefed with the failure
taxonomy from real results, and required each scenario to name the world state proving the behaviour
correct. **Its first-run failure rate against 17/35 tells you whether briefing is a real lever.** If
it is, later waves get much cheaper; if not, the run-triage-fix loop is irreducible and should be
planned for rather than optimised away.

**3. Promote validated scenarios to curated** — only after they have passed a real run.

**4. Backport per the policy in `V0.2-PLAN.md` §6a.** Fixes always; scenarios only when they protect
the player's belongings or the harness's honesty, and only after passing on the canonical branch.
Trunk is ~30 commits ahead of the version branches.

**5. CI** — done on `ci/version-matrix`, unpushed. Note this deliberately does **not** implement
`V0.2-PLAN.md` §8 step 7's 7x4 matrix job. CI already runs on every branch, so each version branch
tests itself with its own `gradle.properties`; a single matrix would have to check out other
branches, and a push to one version would report failures belonging to another. The matrix already
existed — it just hardcoded `java-version: 25`, which is wrong for the branches needing 21 or 17, and
assumed four loaders when 1.20.1 ships three.

## Open defects

- **T-02** — a down-facing observer sometimes never gets placed, ~1 client in 9, on 26.1.2, 26.2 and
  1.21.4. The scenario's own fixture is the prime suspect: it stages a block directly above the
  target in the same column while its javadoc calls it an "adjacent ledge", and that block sits
  exactly where it can occlude the placement ray. **Rule the fixture out before touching the
  builder.**
- **T-04** — a mine quantity ignores matching drops already held. Origin is probably UPSTREAM;
  `BlockOptionalMeta` is unmodified against `upstream/26.1`.
- **T-05** — a full-inventory retry can skip the capacity-recovery deposit.
- **U-local-01** — `builder-orient-timeout-independent-target` fails against it: an independent,
  reachable target with its own material was left air while the builder stayed active for 3,600
  ticks. Pass 2 declined to "fix" that scenario, correctly.

Three scenarios **fail on purpose**: `mine-existing-quantity` (T-04),
`container-exact-deposit-destination` (T-05) and `builder-orient-timeout-independent-target`
(U-local-01). Do not "fix" them. They are the only mechanical record those
defects exist, and making them green erases it.

## What this week actually cost, and why

The ports were cheap and behaved as `V0.2-PLAN.md` predicted — three colliding files each, mechanical
renames. **The expensive part was the harness**, and every expensive failure had one shape: *an
instrument that could not see reported a plausible zero instead of an error.*

- The container oracle read the client copy, then the wrong hash bucket, and answered 0 rather than
  "unknown". That produced two confident, wrong conclusions — that junk depositing was broken, and
  later that the deposit path was destroying player items. Neither was true. (T-01, fixed:
  `BetterBlockPos.hashCode` differs from the plain `BlockPos` that `LevelChunk` keys entities by.)
- A rejected staging command reported the server's error text and not the command, so two days of
  `STAGING_FAILED` were diagnosed by guessing. The harness now records what it sent, and the cause
  fell out in minutes: six gamerules renamed between 1.21.4 and 1.21.11.
- Staging asserted a shulker box existed but never its contents, so a 1.20.1 fixture placed empty
  boxes for a day and looked like a restocking defect. (Item NBT changed at 1.20.5.)
- A sampling loop reported "4 passed, 0 failed" when it had taken 4 samples instead of 12.

`FORK-NOTES.md` §7 now requires scenarios to show the measurement their verdict rests on, for exactly
this reason. When you next add an instrument to this harness, make it able to say *unknown*.

## Process notes worth keeping

**Verify a claim by compiling it, not by believing it.** `DEFECTS.md` T-03 said the 1.21.1 port
dropped a guard the version supports. Restoring it failed with `cannot find symbol` — the component
does not exist there. Three ledger entries were wrong this week, all written confidently from a
single observation.

**A failing test and a broken test are different things.** Twice, a correction pass nearly erased a
real finding by making a red scenario green.

**Scenarios written without ever running fail on their own assumptions, not the mod's behaviour.**
35 met reality: 17 failed and only 2 were real defects. The commonest fault by far was a verdict
gated on the job *finishing* when the correct behaviour is the job *stopping*.

**A clean merge says nothing about whether the result compiles.** Two sessions working from the same
base each produced correct changes that did not build together: one turned
`AbstractShulkerDumpScenario.countCarriedRubble` from `static` into an instance method, the other
static-imported it. Git reported both merges clean and trunk was briefly broken. Compile after
merging branches that touch a shared base class.

**Do not put a Gradle home under `/tmp` on this machine.** It is tmpfs. Twelve of them reached 18G of
RAM and were the main cause of an out-of-memory crash. Use `/home/eli/.gradle-isolated`.

**Identify a JVM by its own argv, never its parent.** PrismLauncher is single-instance: one launcher
process parents every instance started afterwards, and its argv still names the first one. That
reasoning killed the user's game. `parallel_run.py`'s reaper already did this correctly.
