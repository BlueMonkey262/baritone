# Roadmap: Tenor

Target end state: a **publicly released mod** with its own identity, versioned releases, and docs
written for someone who is not the author.

Tenor's three goals — autonomy over long jobs, correctness on the things that stall, and
**performance** — are stated in `FORK-NOTES.md`. Performance is a first-class goal as of
2026-08-01, with two rules: measure before and after, and treat a slowdown as a defect. Phase 2a's
baseline diff is what makes that enforceable, so it should carry suite wall clock and per-scenario
tick counts, not just pass/fail.

The organising principle: *nothing new until the existing surface is known-good and provably so.*

**Sequencing, revised 2026-08-01.** The original order was defects → tests → ship → features. Phase 2
has since been split, because its first half is a *prerequisite* to Phase 1 rather than a successor:
you cannot verify a fix against a suite that is red by design, and Phase 1's own standing rule ("no
fix without a test") presumes working test infrastructure. So:

| Order | Phase | Why here |
|---|---|---|
| 1 | 0 — honest tree | ✅ done |
| 2 | 2a — trustworthy signal | ✅ **done 2026-08-01** — unit suite green, curated suite green, CI, baseline differ |
| 3 | **1 — defect debt** | ← next. Each fix now lands against a green baseline and a differ that shows what moved |
| 4 | 2b — coverage depth | Interleaved with Phase 1 by the no-fix-without-a-test rule, finished after |
| 5 | 3 — product | Unchanged |
| 6 | 4 — features | Unchanged |

---

## Phase 0 — Make the tree honest (prerequisite, ~1 session)

None of the later phases can be sequenced while the working tree contains unreviewed history and
untracked WIP.

1. ~~**Resolve `0624fb14`.**~~ **Done 2026-08-01.** Authorship settled from the codex rollout logs
   (`~/.codex/sessions/2026/07/31/rollout-…019fbab5…jsonl`): codex created all three scenario files
   from scratch in the session it committed, so the handoff's claim that the work "was already
   staged when the session began" was false. Reviewed by Claude as the opposing model; two findings
   fixed, then replaced by three honest commits (`4db35942`, `3db57918`, `e0487ced`).

   Fixed at rewrite time:
   - `PistonObserverPairScenario`'s javadoc stated **both orientation rules backwards** (claimed
     observer inverts and piston follows; the loom-jar bytecode says the opposite). Same claim
     codex was overruled on twice before, this time baked into source presenting itself as the
     authority on the distinction.
   - The scenario was registered **curated** but cannot fail for the right reason: its facing is
     horizontal and its target stands on a floor, so Baritone satisfies either rule and a pass
     proves nothing. Demoted to `registerUncurated` — a green curated test that validates nothing
     is worse than no test.

   Carried forward to Phase 2 (recorded here so they are not lost):
   - **All three scenarios declare stone in the schematic but never give the player stone**
     (`clear @s` then `give` only the target block). Any staging drift becomes a materials
     shortage — precisely the false "stone floor" lead that cost a build-and-run bisect. One line
     each: `give @s minecraft:stone 64`.
   - **`repeaters-delays` batches four states into one schematic**, the pattern the handoff itself
     identified as poison. It diagnosed cleanly only by ordering luck — `delay=1` is the default
     and was placed before the pause. Reverse the visit order and the other three report missing
     whether or not they were placeable.
   - ~40 lines of `poll()` skeleton, `builderInactiveSince` and `lastProgressNote` are triplicated
     verbatim across the three files; `lastProgressNote` is never reset, correct only because a
     fresh instance is constructed per run. Pull up into a base class (`AbstractBoxBuildScenario`
     is the precedent).
   - `PairState.summary()` hardcodes `"wanted east"` against a `FACING` constant.

   **Process note:** codex's account of its own work was wrong in the handoff, and its orientation
   claim was wrong three times running. The rollout logs settle authorship questions in about a
   minute. Verify against them rather than the narrative.
2. **Land or park `src/main/java/baritone/testing/scenario/gen/`.** 622 generated cases, ~95%
   validated, all `registerUncurated`. Commit it — it is a diagnostic asset and its uncurated
   registration means it cannot break `#testing all`. Note the two open families (observers,
   vertical pistons/dispensers) in the commit message rather than blocking on them.
3. **Kill the filename trap.** `TESTING_HANDOFF.md` and `TESTING-HANDOFF.md` differ by one
   character and both exist. Merge into one.
4. **Branch consolidation.** Six local branches (`fix/builder-pathing`, `fix/hardening`,
   `fix/restock-session`, `wip/mine-deposit`, `wip/proguard`, `wip/review`) are all merged into
   `shulker-restock`. Verify with `git branch --merged` and delete. Decide the long-lived branch
   name now — `main` does not exist locally, and a public repo needs a default branch.
5. Housekeeping: the 188 MB `baritone-testing(1)`/`(2)` PrismLauncher clones.

---

## Phase 1 — Close the defect debt (the bulk of the work)

### 1a. Build one ledger first

Open findings are currently scattered across five documents with no status field anywhere:

| Source | Contents | Problem |
|---|---|---|
| `sol-high-review.md` | H1–H10, M1–M8, plus lows | Written against `a045382f`; HEAD is 5+ commits later. **Unknown how many are already fixed.** |
| `UNTRACKED_BUG_REVIEW.md` | 8 findings (2 high, 4 medium, 2 low) | Baseline `35b69368` plus uncommitted work that has since changed |
| `UPSTREAM_BUG_BACKLOG.md` | U-local-01..04, plus T01–T60 upstream issues | U-local-02's diagnosis is disputed by bytecode evidence |
| `FORK-NOTES.md` §8 | Shelter retreat has no distance bound | Reproduced live, not fixed |
| `TESTING_HANDOFF.md` | `dispenser-facing_up` outlier, observer rule | Staging vs. builder defect unresolved |

**First work item: a triage pass that re-verifies every finding against current HEAD and produces
`DEFECTS.md`** — one row per defect with ID, severity, status (open / fixed-unverified / fixed-tested
/ wontfix), and the test that proves it. This is mechanical, well-specified, and reads a lot of code:
a good codex delegation. Do not trust its verdicts without spot-checking the ones it calls fixed.

Everything downstream references ledger IDs. The five source docs become historical and move to
`docs/reviews/`.

### 1b. Fix in severity order

1. **sol highs** — H1–H10 are the dangerous class: pathing stalls (H1 churn cooldown deleted
   immediately, H2 reroute detector watching the wrong set, H3 orientation timeout stranding the
   builder), container-state races (H6 menu never proved to belong to `targetBox`, H8 optimistic
   quick-moves treated as confirmed), and material loss (H9, H10). H4 (shulker lid clearance
   direction) is the highest-value cheap one.
2. **UNTRACKED highs and mediums** — 1 and 2 are shelter-path correctness; 4 (`#addbox` indexing a
   box with a neighbour's contents) corrupts persisted state, which is worse than it sounds.
3. **U-local-01..04** — 04 is root-caused (`HopperBlock.FACING` is `FACING_HOPPER`, not
   `BlockStateProperties.FACING`, so `ORIENTATION_PROPS` never waives it). It is a one-line fix with
   a harness case already reproducing it. 02 needs settling empirically first (see 2c).
4. **Shelter retreat distance bound** — reproduced live, 188-block retreat through unrelated
   terrain. FORK-NOTES already specifies the constraint: new setting, upstream-equivalent default.

### 1c. The standing rule

**No fix lands without a test.** Either a unit test against a pure static helper (the
`RestockProcessTest#fetchTarget` / `ProcessSchedulerTest` pattern — extracting the helper is part of
the fix) or a harness scenario. A fix with neither goes back. This rule is what converts Phase 1
into Phase 2's green signal rather than more untested surface.

---

## Phase 2a — Trustworthy signal (do this next)

The goal is narrow and worth stating plainly: **a green baseline, enforced automatically, that tells
you what moved.** Everything else in testing is depth; this is the part without which none of the
depth is legible.

1. ~~**Settle `build-observers` — this is the gate.**~~ **Done 2026-08-01.** It was a real defect,
   not staging: `GoalPlaceOriented`'s UP branch stood the player 3 below the target while upstream's
   `searchForPlacables` scans only to `dy <= +1`, so the target was never in scan range from the
   position the goal walked to. See U-local-02 for the full account, including a fix that was tried
   and reverted. **The curated suite is now 30/30 across three clients in 203s.**

   Two lessons worth carrying into the rest of this phase:
   - *Instrument before theorising.* Two rounds of careful static reasoning produced a plausible,
     self-consistent, wrong root cause. One diagnostic logging player position and pitch settled it
     in a single run.
   - *Verify a fix against the whole suite, not its own scenario.* The reverted change made
     `build-observers` pass while quietly halving suite throughput and breaking `logs-axes`. A
     green target scenario is not evidence of a good fix — which is exactly what item 4's baseline
     diff is for.
2. ~~**Refactor the box estimate to a pure helper.**~~ **Done 2026-08-01.** The only
   Minecraft-dependent line was `Item#getDefaultMaxStackSize`; the rounding, saturation and
   overflow behaviour the tests actually assert is pure. Extracted as
   `IRestockBox.estimatedUsedSlots(Map<T,Integer>, ToIntFunction<T>)`, with the `default` method
   passing `Item::getDefaultMaxStackSize`. **`./gradlew test` is green: 108 tests, 0 failures**,
   for the first time. Two cases were added while the arithmetic was exposed — a sub-one stack size
   must not divide by zero, and the int-overflow case now says in the test why it matters.
3. ~~**CI.**~~ **Done 2026-08-01.** `.github/workflows/build.yml`: `:test` plus
   `:<loader>:compileJava` across a `fabric/forge/neoforge/tweaker` matrix, on push, PR and manual
   dispatch, with `fail-fast: false` so one broken loader does not mask the rest, concurrency
   cancellation, and the test report uploaded on failure. It compiles rather than builds, because
   `build` drags in ProGuard via `createDist` — a packaging concern, not a "does this compile" one.
   All four loader tasks were verified to exist and compile locally before the workflow claimed to
   run them.
4. ~~**A committed baseline and a differ.**~~ **Done 2026-08-01.**
   `scripts/testing/diff_baseline.py` against `scripts/testing/baseline.json` (currently 10/10,
   203s). It aggregates the per-instance rows a curated run produces, so it reports verdict changes,
   per-scenario median tick changes, suite wall clock, and — free from the aggregation — scenarios
   whose instances disagreed with each other, which is what a marginal case looks like *before* it
   fails. A scenario counts as passing only if it passed on every instance.

   Verified by replaying the real regression: it reports `logs-axes PASS -> TIMEOUT`, the suite
   `203 -> 427 (+110%)`, **and** `build-schematic ticks +80%` — a scenario that stayed green and
   got much slower, which pass/fail alone would have hidden entirely. Verdict regressions exit 1;
   timing alone is a note unless `--fail-on-timing`, on the theory that a gate which blocks on the
   noisiest signal gets switched off within a week.
5. ~~**Document the split gate.**~~ **Done 2026-08-01.** In `FORK-NOTES.md` §7 and `CLAUDE.md`:
   CI is everything a machine can check unattended; the in-game suite is a two-command manual gate
   whose diff is pasted into the PR. Both say plainly that a green tick in CI means the code
   compiles and the pure logic holds, and says nothing about whether the builder still builds.

**Exit criterion — met 2026-08-01.** `./gradlew test` green (108/0), curated suite green (30/30
across three clients, 203s), CI enforcing the automatable half, and a baseline differ that names
what changed including timing. **The regression net exists; Phase 1 is done against it.**

## Phase 2b — Coverage depth

Interleaved with Phase 1 (every fix brings its test) and finished afterwards.

**Priority reframe.** The generated catalog is 622 cases answering one narrow question — placement
orientation. The things most likely to break when features are added are process arbitration and
container state, and `RestockProcess`, `ShelterProcess`, and `PickupBlocksProcess` have **no**
state-machine tests between them. `ProcessScheduler` was split out of `PathingControlManager`
specifically so that layer could be tested without Minecraft; the same extraction is available for
the process state machines. Spend coverage effort there before adding placement families.

0. **Land the four scenario-quality items carried forward from Phase 0 item 1** — the stone supply,
   the `repeaters-delays` split, the triplicated `poll()` skeleton, and the hardcoded `"wanted
   east"`. Cheap, and the first two remove a whole class of misleading verdict.
1. **Minecraft-free state-machine tests for the three process additions**, in the order they carry
   risk: `RestockProcess` (container session, box selection, give-up accounting), `ShelterProcess`
   (retreat → unload → bed → wait, and the `DEFER` handoff), `PickupBlocksProcess`. Extract the
   decision logic the way `ProcessScheduler` was extracted. This is the coverage that protects
   future addons.
2. **The curated/uncurated policy as a standing rule.** *Curated means must-be-green*; a scenario
   reproducing an open ledger defect is uncurated and tagged with its ID; a defect's fix promotes
   its scenario to curated **in the same commit**. Phase 2a makes the suite green once; this keeps
   it that way.
3. **Settle the remaining harness questions.** `dispenser-facing_up` is an unexplained outlier with
   the same rule and geometry as a passing case — explain it, do not paper over it. Keep the
   discipline the handoff earned: **read the bytecode per family, and remember horizontal passes do
   not validate an orientation rule.**
4. **Then breadth.** Follow `TEST_COVERAGE_REVIEW.md`'s milestone, and expand the generated catalog
   only after each new family's placement rule is read from bytecode and checked against one
   hand-verified live case. Generation was never the bottleneck; the per-family rule is.

---

## Phase 3 — Turn it into a product

### Identity and licensing

- **Name.** "Baritone+" works, but be unambiguous that it is an unofficial fork. Upstream is LGPL-3.0
  with the anime exception; that license carries over — keep `LICENSE` verbatim, keep the per-file
  headers, and add the **prominent modification notice LGPL requires**, plus attribution to
  cabaletta and the upstream contributors.
- **Do not rename the `baritone.api` package or the mod id.** Other mods compile against
  `baritone.api`, and `src/api` is deliberately not obfuscated so they can. Staying a drop-in
replacement is Tenor's strongest distribution argument. Brand at the README, jar name, and
  release level instead.
- **Public API is now a contract.** `sol` M7 flagged the new `IRestockBox`,
  `IRestockBoxCollection`, `IRestockProcess`, `IShelterProcess` and the `IBuilderProcess` /
  `IWorldData` changes as compatibility-breaking. Before 1.0: decide what is stable, mark the rest
  experimental, and write down the deprecation policy.

### Versioning and releases

- Resolve the version mismatch: `gradle.properties` says `mod_version=1.18.0` while jar names come
  from `git describe` (`1.15.0-20-gd686accf-dirty`). Pick one source of truth — annotated tags
  driving `git describe`, with a scheme like `X.Y.Z+mc26.1.2`.
- The dirty-tree hazard (`git describe` unchanged, same filename overwritten) is a footgun for
  users too. Make release builds refuse a dirty tree.
- Release workflow producing all four loaders × three variants, with checksums, plus a real
  `CHANGELOG.md`.
- **State the support policy plainly: Minecraft 26.1.2 only.** The README currently inherits
  upstream's 1.12.2–1.21.3 badge wall, which is false for Tenor and the fastest way to earn bad
  issues.

### Documentation

Eight working docs sit at the repo root, all of them internal notes. Restructure:

| Now | Becomes |
|---|---|
| `README.md` (upstream's, with a private-fork banner) | Rewritten for a stranger: what it is, what it adds over Baritone, install per loader, first five minutes |
| `FORK-NOTES.md` | `docs/vs-upstream.md` — the differences doc; already excellent, mostly needs reframing from "notes to self" |
| `sol-high-review.md`, `UNTRACKED_BUG_REVIEW.md`, `TEST_COVERAGE_REVIEW.md`, both handoffs | `docs/reviews/` — historical, superseded by `DEFECTS.md` |
| `UPSTREAM_BUG_BACKLOG.md` | Keep; it is the Phase 4 backlog |
| — | New: settings reference, `#addbox`/restock guide, harness guide, `CONTRIBUTING.md` |

Retarget `.github/ISSUE_TEMPLATE` (currently upstream's) and add the line that saves everyone's
time: *do not report Tenor bugs to cabaletta/baritone.*

### Upstream relationship

Two of these fixes are genuinely upstream bugs, not fork features — the water-cost initialisation
(`waterWalkSpeed` initialised to the *enchanted* value, so the pathfinder priced swimming as
walking) is a one-line fix with a clean diagnosis and a scenario behind it. **Offer it upstream.** A
public fork whose author contributes fixes back is credible; one that only takes is not. It also
shrinks the permanent rebase surface.

Decide and write down the upstream-tracking cadence now — Tenor is branched from `26.1` and
upstream has moved on. Keeping fork commits atomic and rebase-friendly is cheap now and expensive
to retrofit.

### Pre-release safety

A publicly released autonomous bot invites misuse reports. State the anti-cheat/server-rules
position, and keep the design point that already supports it: **everything goes through real
client→server packets** — no inventory injection, no faked server-side effects, so the server's own
range and validity checks always apply.

---

## Phase 4 — Then add things

Only after a green 1.0 is out.

- `UPSTREAM_BUG_BACKLOG.md`'s T01–T60 is the differentiator: 60 triaged upstream issues, P0 first.
  Fixing upstream's long-standing mine/build loops is a better pitch than any new feature.
- Capability expansion (farming, resource loops, base logistics) after that.

---

## Sequencing

| Order | Phase | Rough size | Gate to exit |
|---:|---|---|---|
| 1 | 0 — honest tree | 1 session | Clean `git status`, one branch, no unreviewed history |
| 2 | 2a — trustworthy signal | Moderate | `:test` green, curated `#testing all` green, CI enforcing both, baseline diff naming what moved |
| 3 | 1 — defect debt | The bulk | `DEFECTS.md` has no open highs; every fix has a test |
| 4 | 2b — coverage depth | Substantial | State-machine tests for the three process additions |
| 5 | 3 — product | Moderate | Tagged release, docs for strangers, license compliance |
| 6 | 4 — features | Open-ended | — |

**Immediate next action: settle `build-observers`.** It is the one thing standing between here and a
green curated suite, and until that suite is green nothing downstream can tell you whether a change
broke something.

Right behind it, and independent of it: the **Phase 1a triage**. Until `DEFECTS.md` exists, nobody —
including you — can say which of the ~30 documented findings are still real, and every estimate past
this point is a guess.
