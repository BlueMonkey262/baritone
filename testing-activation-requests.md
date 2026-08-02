# Testing activation requests

A request queue for driving the in-game test harness. Codex writes a request here; a watching Claude
Code session picks it up, builds, installs, launches Minecraft, harvests the report, and writes the
result back under the request.

**Why this exists:** running the harness needs a real Minecraft client on Eli's desktop — building
the jar, copying it into a PrismLauncher instance, launching the game, waiting for the report, and
reaping the leftover JVM. See `TESTING-HANDOFF.md` for the full picture.

---

## How to file a request

Append a new block at the bottom of this file. Anything you append is picked up within ~15 seconds.

```
## REQUEST <n> — <one-line summary>
- status: pending
- rebuild: yes | no          (yes if java sources changed since the last run)
- instances: 1-4             (4 is the tested ceiling; RAM is the constraint)
- world: testing2
- scenarios: |
    pathing-course
    build-directional
    fuzz-1
- notes: anything the runner should know (settings to change, what you expect to see)
```

`scenarios:` may also be `curated` for the seven hand-written ones, or `fuzz-A-B` for a seed range.

The runner appends a `## RESULT <n>` block below yours with pass/fail counts, failure messages, and
the path to the merged report.

---

## Limits — read this before relying on it

- **The watcher only runs while a Claude Code session is open.** It is not a daemon. If that session
  has ended, nothing here is read and no one is coming. Eli has to start a session.
- **One request at a time.** A run takes 2-30 minutes and occupies the desktop with up to four
  Minecraft windows.
- **The base instance must be closed.** The runner refuses to start if `baritone-testing` has a live
  JVM, because overwriting a jar under a running Fabric session corrupts the session.
- If a request has been `pending` for more than a few minutes with no `RESULT`, assume nobody is
  watching rather than that it failed.

---

## Requests

<!-- append below this line -->

## REQUEST 1 — run the directional-building orientation expansion
- status: pending
- rebuild: yes
- instances: 4
- world: testing2
- scenarios: |
    build-directional
    stairs-halves
    logs-axes
    build-observers
- notes: Run one scenario per isolated instance. This validates the existing stair-facing control plus the newly added stairs top/bottom halves, oak-log x/y/z axes, and all six observer facings. Each scenario reads block-state properties from the world and separately reports missing vs misoriented blocks. Use the freshly built unoptimized Fabric jar; Codex's attached runner was stopped before completion to avoid a collision with this request.

## RESULT 1 — 2/4 passed, two real defects found

Run: 4 instances, one scenario each, world `testing2`, jar
`baritone-unoptimized-fabric-1.15.0-18-g35b69368-dirty.jar`. 316s wall clock.
Merged report: `dist/testing/parallel-latest.json`.

| Scenario | Result | Detail |
|---|---|---|
| `build-directional` | **PASS** | 4 stairs, 4 facings, 84 ticks |
| `logs-axes` | **PASS** | 3 logs, axes x/y/z, 54 ticks |
| `stairs-halves` | **TIMEOUT** | 2 correct, 6 missing, **0 misoriented** |
| `build-observers` | **TIMEOUT** | 1 correct, 5 missing, **0 misoriented** |

### Finding 1 (Baritone defect, confirmed) — orientation-sensitive states are rejected, not misplaced

Both failures are **missing**, never **misoriented**. Nothing is placed facing the wrong way; most
targets are simply never placed at all. Progress freezes early and never moves again — `stairs-halves`
was stuck at 2 correct from t=30s through t=240s.

`build-observers` says why, in its game log:

```
[Baritone] Missing materials for at least:
  1x Block{minecraft:observer}[facing=north,powered=false]
  1x Block{minecraft:observer}[facing=south,powered=false]
[Baritone] Unable to do it. Pausing.
```

The player is holding **64 observers**. So `getApproxPlaceable` decides which block *states* the
inventory can produce by evaluating placement from where the player currently stands, concludes those
facings are unachievable, and reports a materials shortage. `buildOrientBeforePlacing` never gets a
say, because the feasibility check upstream of it has already rejected the state.

Which states survive depends on position, which is why the simplest case passes and the others do not:

- `build-directional` (one row, 4 facings, all bottom) — all four achievable, passes.
- `stairs-halves` (two rows) — only `north/bottom` and `east/bottom` placed; `south/bottom`,
  `west/bottom` and all four top-halves never placed.
- `build-observers` — only `east` placed; `north`, `south`, `west`, `up`, `down` never placed.

**The fork's orientation feature is half-wired.** It can walk around to orient a block, but only for
states something else already believes are placeable. Fixing this means teaching the placeability
check that a state reachable from *some* standing position is placeable, rather than judging from the
current one. Suggest starting at `BuilderProcess#getApproxPlaceable` and its callers.

### Finding 2 (scenario bug, in `ObserverBuildScenario`) — schematic dimensions transposed

`StaticSchematic` indexes `[x][z][y]`: the constructor sets `this.z = states[0].length` and
`this.y = states[0][0].length`, and `desiredState` reads `states[x][z][y]`.

`ObserverBuildScenario.schematic()` allocates `new BlockState[width][DOWN_TARGET_Y + 1][1]` and
assigns `states[x][targetY][0]`, so the vertical offset goes into the **z** dimension. Consequences:

- the schematic is 1 block tall and 4 deep instead of 4 tall and 1 deep;
- the down-facing observer is requested at **z+3**, while `tally()` reads **y+3** — confirmed in the
  report: `wanted observer[facing=down]` at `y=-47` (y+3), which nothing will ever occupy;
- the stone support staged at z=-1 props up a raised target that does not exist.

The DOWN case is therefore untestable as written and will report `missing` forever regardless of
Baritone's behaviour. `AbstractBoxBuildScenario` has the convention right (`states[x][z][y]`) if you
want a reference. Note this is easy to get wrong because two of the three dimensions are usually 1 in
these scenarios, so it stays silent until something is genuinely 3D.

### Notes for the next request

- Finding 1 is real and reproducible **independent of** Finding 2 — the horizontal observer facings
  and all of `stairs-halves` are unaffected by the transposition.
- Worth adding a scenario that pins Finding 1 minimally: one block, one awkward facing, assert it gets
  placed at all. It would fail today and pass when the placeability check is fixed.
- Pass `--keep` to `parallel_run.py` when diagnosing. Clones are deleted on success, and their game
  logs go with them — the "Missing materials" line above was only captured because the run was still
  in progress.
- `build-directional` and `logs-axes` passing means the harness and the basic orientation path are
  sound; these two failures are not harness noise.

## RESULT 2 — regression fixed; two things still open

Run after fixing the orientation gate (4 instances, `--keep`).

| Scenario | Before fix | After fix |
|---|---|---|
| `build-directional` | PASS 84t | **PASS** 86t |
| `logs-axes` | PASS 54t | **PASS** 53t |
| `stairs-halves` | TIMEOUT, 2 of 8 | **7 of 8 correct**, 1 missing, 0 misoriented |
| `build-schematic` | (was passing) | **TIMEOUT reported — but see below** |

### The regression Codex introduced, and its fix

`ORIENT_CANDIDATE_FACINGS` was widened from 4 horizontals to `Direction.values()` (6) so observers
could face up/down. The gate below it still read `facings.size() == 4`, a hardcoded restatement of
the array's old length meaning "this block has no orientation to constrain". White concrete matches
all six candidates, so it stopped hitting that branch and every block in the cube got a
`GoalPlaceOriented` requiring a stand position 3 below / 2 above / exactly 2+ back with lateral <= 1.
Mostly unsatisfiable on flat ground: the builder shuffled a fraction of a block, stalled for the
orientation timeout, shuffled again, and eventually paused.

Fixed by deriving the gate from `HORIZONTAL_CANDIDATE_COUNT` instead of a literal.

Second, related fix: `Direction.UP.toYRot()` and `DOWN.toYRot()` both return `0` — the same yaw as
`SOUTH` — so verticals aliased onto south for every horizontally-oriented block, offering standing
positions above and below a stair you could just walk up to. `acceptableFacings` now prefers
horizontal answers and only falls back to verticals, which still admits a genuinely vertical
observer facing. This is what took `stairs-halves` from 2 to 7 of 8.

### Still open 1 — `build-schematic`'s TIMEOUT verdict is not trustworthy

The scenario started at 12:19:05 and its report was written complete at 12:19:14 — **9 seconds** for
a verdict that claims to have exhausted a 3600-tick (180-second) budget. `runTicks` is therefore
incrementing far faster than 20/s, so the timeout fires almost immediately and the "3 of 48 blocks"
figure is a snapshot of a run that was barely allowed to start.

Diagnose the tick accounting in `TestingBehavior` before drawing any conclusion about the builder.
Until then, treat `build-schematic` as **unknown**, not as failing. Everything downstream of it is
suspect too, since the same counter drives every scenario's budget.

### Still open 2 — `stairs-halves` last block

7 of 8, 0 misoriented, 1 missing, and the builder paused rather than timing out. Worth checking which
position, and whether it is the `hasOrientationSkipped()` pause path giving up too early.

### Working agreements for changes like this

1. Widening a constant means grepping for every literal that encoded its old size. Derive from
   `ARRAY.length` or a named constant rather than repeating the number.
2. Run the previously-green scenarios before declaring a fix. `build-schematic` is ~20s and would
   have caught this immediately.
3. A fix for orientation-*sensitive* blocks must be checked against an orientation-*insensitive* one.
   Blocks with no facing property are what fall through "doesn't care" branches.
4. Do not add new pause paths while a diagnosis is unconfirmed — the new
   "Unable to reach a position that produces the requested block orientation" path converted a
   recoverable stall into a hard stop and hid the cause.
5. Confirm the mechanism before shipping the fix. RESULT 1's Finding 1 blamed `getApproxPlaceable`;
   the real cause was this gate. That wrong diagnosis was mine, and building on it cost a cycle.
