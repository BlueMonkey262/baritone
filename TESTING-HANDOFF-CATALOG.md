# Testing handoff — 2026-07-31

State of the in-game test harness work at the end of the session. Working document, not a spec.

## Where things stand

The harness and the parallel runner both work and are not the problem. The open work is the
**generated placement catalog** (`src/main/java/baritone/testing/scenario/gen/`), which is new,
uncommitted, and about 95% validated.

### Tree state

- `HEAD` is `0624fb14` "testing: piston pair passes; repeaters documented; hoppers unfinished".
  **This commit was made by a codex agent that had been told not to commit.** It is authored as the
  repo owner, was never pushed, and contains work that was already staged when the session began
  (759 insertions: the three new scenarios plus the original backlog text). Nothing was lost. It is
  the one piece of unreviewed history in the tree — amend, reword, or `git reset --soft HEAD~1` as
  preferred.
- Unstaged on top of it:
  - `UPSTREAM_BUG_BACKLOG.md` — added U-local-04, corrected U-local-02 (see below).
  - `HoppersFacingScenario.java` — javadoc corrected; the old "stone floor" lead was disproven.
  - `TestingBehavior.java` — registers the generated catalog via `registerUncurated`.
  - `src/main/java/baritone/testing/scenario/gen/` — untracked, 5 files, the generated catalog.
- A codex round was **still in flight** when the session ended (thread
  `019fbb8b-150c-7800-a006-6f3f4df695a0`, task `kmi305g5n`). Its edits may or may not be present.
  Check `git status` and re-read `PlacementRules.java` before trusting anything below about the
  observer rule.

## Curated suite: 10/11, no regression

Last full run, jar `1.15.0-20-gd686accf-dirty`, 127s wall clock across 3 clients.

The single failure is `build-observers` on the up-facing observer — the same 9/10 that `d686accf`'s
commit message records, plus the new `piston-observer-pair` passing. Nothing regressed.

**Open question:** `build-observers` is curated while every other known-defect scenario
(`repeaters-delays`, `hoppers-facing`, `shelter-retreat-distance`, `unreachable-build-target`) is
uncurated precisely so `#testing all` can be green. That makes the curated suite permanently red and
useless as a regression signal. Either move it to `registerUncurated` or accept the red. Not changed,
because it is a judgement call about which signal matters.

## The generated placement catalog

622 cases across six families, one target block state per scenario, all registered
`registerUncurated` so `#testing all` is untouched.

| Family | Cases | Status |
|---|---:|---|
| stairs | 464 | **validated** — do not change |
| logs/pillars | 123 | **validated** — do not change |
| observers | 6 | rule is wrong (see below); 5/6 pass anyway |
| pistons + sticky | 12 | horizontal validated; vertical open |
| dispensers/droppers | 12 | horizontal validated; vertical open |
| hoppers | 5 | correct — reproduces U-local-04 |

### Why one state per scenario

Not an aesthetic choice. A single unsatisfiable state pauses the whole schematic (U-local-03), so
batching states means one known gap silently poisons every state batched with it. This was then
**demonstrated**: `place-hopper-facing_down` passes in 11 ticks in isolation, but the hand-written
`hoppers-facing` scenario reports it as one of five missing — it is placeable and simply never gets
its turn once the builder pauses on the other four.

### The bug that nearly poisoned everything

The generator's first version staged targets at `y=1` over a floor topping out at `y=-1`, leaving
**air directly beneath every target** and every side support floating. 1 of 26 cases passed. The one
pass, `place-oak_log-axis_y`, was the only case whose support sat directly below the target — the
control that proved the diagnosis.

Every hand-written scenario maintains the invariant that **a target rests on a solid, schematic-
declared block**. `ObserverBuildScenario` and `LogsAxesScenario` put targets at `y=0` on the floor;
`HoppersFacingScenario` stages an extra floor layer at `y=0` under its `y=1` targets. Any new family
must maintain it too.

### Placement rules are the bottleneck

Verified directly from `/home/eli/.gradle/caches/fabric-loom/26.1/minecraft-client.jar`:

| Block | `getStateForPlacement` | Effective rule |
|---|---|---|
| `ObserverBlock` | `getNearestLookingDirection().getOpposite().getOpposite()` | **identity** |
| `PistonBaseBlock` (in `block/piston/`) | single `.getOpposite()` | inverted |
| `DispenserBlock` | single `.getOpposite()` | inverted |

Observers and pistons have **opposite** rules. The codex agent found this twice and was twice
overruled on the basis of the repo's prose; the bytecode was right both times.

Read the bytecode for every new family. `javap -p -c` on the class extracted from the loom jar is
enough, and it is what settled both U-local-04 and this.

**Horizontal passes do not validate a rule.** With a floor below the target, Baritone can choose a
standing position whose look satisfies either rule, so horizontal observer cases passed with the
support on the wrong side. Only the vertical cases discriminate.

### Open vertical failures

With target at `y=0` and player eye ≈1.62:

| case | rule | look needed | face staged? | live |
|---|---|---|---|---|
| observer down | identity | DOWN | floor below — yes | PASS 20t |
| observer up | identity | UP | nothing above — no | FAIL |
| piston up | inverted | DOWN | floor below — yes | PASS 85t |
| piston down | inverted | UP | support at `(0,1,0)`, lower face y=1.0 is *below* the eye — no upward ray | FAIL |
| dispenser up | inverted | DOWN | floor below — yes | **FAIL — outlier, unexplained** |
| dropper down | inverted | UP | as piston down | FAIL |

Five of six follow from the corrected rules, which is good evidence they are staging failures rather
than builder defects. A look-UP case needs the target raised so the player's eye can sit *below* the
underside it must aim at. `dispenser-facing_up` has the same rule and geometry as `piston-facing_up`
and must be explained, not papered over.

## U-local-02 is suspect

Its diagnosis rests on "`getNearestLookingDirection()` returns EAST and observer placement inverts it
to WEST". The bytecode says observer placement **does not invert**. Either the entry's reasoning is
wrong, or Baritone's own placement simulation applies an inversion vanilla does not — which would be
a real and more interesting bug than the entry describes. Deliberately not rewritten. The harness now
has the isolation to settle it empirically once the observer rule is corrected.

Consequence: `place-observer-facing_up` may be a staging failure, not U-local-02 — and if so, the
curated `build-observers` failure may be too.

## U-local-04 was found and root-caused this session

`couldProduce` waives `ORIENTATION_PROPS`, which names `DirectionalBlock.FACING` =
`BlockStateProperties.FACING`. `HopperBlock.FACING` is a *different* instance,
`BlockStateProperties.FACING_HOPPER`, so no hopper facing is ever waived and a plain hopper is judged
incapable of producing any specific facing — a false "missing materials" while holding 64 of them.

Refined by the isolated run: this bites only **non-default** facings. `facing=down` is the hopper's
default, matches, and places fine.

## How to run things

```bash
./gradlew :fabric:build                        # jar lands in dist/
./gradlew compileJava                          # fast check

# sharded live run; -n is client count, --keep preserves clones (and their logs)
python3 scripts/testing/parallel_run.py -n 3 --timeout 1800 --keep \
  --scenarios <file-with-one-name-per-line> \
  --jar dist/baritone-unoptimized-fabric-<version>.jar

# merged results, with per-case notes
python3 -c "
import json,pathlib
d=json.loads(pathlib.Path('dist/testing/parallel-latest.json').read_text())
print(d['passed'],'/',d['total'])
for s in sorted(d['scenarios'],key=lambda s:s['name']):
    print(f\"{s['status']:<5} {s['name']:<42} {s['ticks']:>4}  {s['message'][:70]}\")
"
```

Gotchas learned the hard way:

- **The clone's `minecraft/logs/latest.log` is the highest-value diagnostic** and is deleted unless
  `--keep` is passed. It carries Baritone's chat output. U-local-04 was diagnosed in one grep of it
  after a build-and-run bisect had failed to find the cause.
- A codex agent in its sandbox **cannot start Gradle** (read-only wrapper lock at
  `~/.gradle/wrapper/dists/.../gradle-8.14.4-bin.zip.lck`). It substitutes its own `javac`. Always
  compile and build yourself rather than taking its word.
- `git describe` drives the jar filename, and a dirty tree does not change it. Rebuilding without
  committing overwrites the same filename.
- Never overwrite a jar while Minecraft is running.

## Suggested next steps

1. Score the in-flight codex round against its own predictions; re-run the 28-case validation batch.
2. Resolve `dispenser-facing_up`.
3. Settle U-local-02 empirically now that observers can be tested one state at a time.
4. Only then expand the catalog. Each new family needs its bytecode rule read, then a live run against
   a hand-verified case, before its cases are trusted. Generation is not the bottleneck; the
   per-family placement rule is.
5. Decide on `build-observers` curated vs uncurated.
6. Housekeeping: `baritone-testing(1)` and `baritone-testing(2)` under the PrismLauncher instances dir
   are 188 MB leftovers, both still `name=baritone-testing` in `instance.cfg`, so the launcher shows
   three identically named instances. Left in place.
