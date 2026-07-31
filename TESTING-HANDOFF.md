# In-game test harness — handoff

Written 2026-07-31 at the end of a Claude Code session, for whoever picks this up next.
Everything below is **uncommitted** work on branch `shulker-restock` (last commit `35b69368`).

Read **FORK-NOTES.md §7** first — it documents the harness design. This file covers state, traps,
and the proposed next tests.

---

## 1. What exists

### Harness (all new, untracked)

```
src/main/java/baritone/testing/
  TestingBehavior.java        state machine, autorun, sharding, report writing
  TestArena.java              per-scenario patch of world; command queue; wipe()
  TestScenario.java           the four-phase lifecycle scenarios implement
  ScenarioResult.java         verdict + notes
  TestReport.java             markdown + JSON writer
  scenario/
    PathingCourseScenario.java       walls, trench, water, staircase
    WaterDetourScenario.java         pool vs dry route; takes a multiplier for variants
    AbstractBoxBuildScenario.java    shared 5x5x3 ring builder
    SchematicBuildScenario.java      ring with materials in hand (control case)
    RestockFromBoxScenario.java      ring starting 16 short, fetches from a box
    DirectionalBuildScenario.java    4 stairs, 4 facings, orientation checked
    FuzzPathingScenario.java         seeded courses; plan(seed) is pure and unit-tested

src/main/java/baritone/command/defaults/TestingCommand.java
src/test/java/baritone/testing/ScenarioResultTest.java
src/test/java/baritone/testing/scenario/FuzzPathingScenarioTest.java
src/test/java/baritone/api/pathing/movement/ActionCostsWaterTest.java
scripts/testing/parallel_run.py
```

Registered in `Baritone.java` (behavior) and `DefaultCommands.java` (command).

### Commands

```
#testing list | all | <name>... | cancel | autorun | status
```

`all` = the 7 curated scenarios. `fuzz-1 .. fuzz-240` exist but are deliberately excluded from
`all`; run them by name or via a shard file.

### Running it

```bash
./gradlew :fabric:build --offline -x test
# close Minecraft first, then copy dist/baritone-unoptimized-fabric-*.jar into
# <instance>/minecraft/mods/  (delete the old baritone jar)
# arm autorun: touch <instance>/minecraft/baritone/testing/autorun.flag
# launch, load world "testing2", wait ~10s, don't type anything
```

Parallel across cloned instances:

```bash
python3 scripts/testing/parallel_run.py -n 4 --scenarios list.txt --timeout 3600
python3 scripts/testing/parallel_run.py -n 4 --fuzz 1-40
python3 scripts/testing/parallel_run.py -n 2 --curated
```

Base instance: `~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/baritone-testing`,
world `testing2` (superflat, creative, cheats on). Merged output: `dist/testing/parallel-latest.json`.

---

## 2. Results so far

**77 navigation courses run, 0 failures.** That is why the fuzz suite should shrink — see §5.

| Scenario | Status |
|---|---|
| `pathing-course` | passes (~250 ticks) |
| `water-detour`, `-x8`, `-x30` | pass, 0 ticks wet, after the water fix |
| `build-schematic` | passes (~370 ticks) |
| `build-directional` | passes — 4 stairs, 4 facings, 82 ticks |
| `restock-from-box` | passes, with a verified mid-build restock |
| `fuzz-1 .. fuzz-70` (sampled) | all pass |

### Bugs the harness found and fixed

1. **Water cost equalled dry land.** `CalculationContext` initialised the depth-strider efficiency
   figure to `1.0f` (fully efficient) instead of `0.0f`. Vanilla's `water_movement_efficiency` is a
   `RangedAttribute` with default 0, max 1, so every unenchanted player was modelled as walking
   through water at full speed and the pathfinder saw no reason to go around a pool. One-character
   fix, plus a new `waterCostMultiplier` setting (default 2.0). See FORK-NOTES §3.
2. **All six `/gamerule` staging commands were silently rejected** — the ids were renamed in 26.1.2
   (`doDaylightCycle` → `advance_time`, `doMobSpawning` → `spawn_mobs`, `keepInventory` →
   `keep_inventory`, etc). Two full suite runs reported confident results about a world that had
   daylight cycling and mob spawning on.
3. **The harness hung after finishing.** `onTick` returned early on `player == null`, which is true
   the instant it disconnects, so the shutdown state machine never reached `mc.stop()`.

---

## 3. Traps — read before touching anything

These each cost real time to find.

- **Verdicts must read world state, never chat.** Baritone logs "Done building" when it has nothing
  left it *can* do, which includes having given up — the exact state a materials bug leaves it in.
- **An unreachable goal is indistinguishable from a pathfinder giving up** in a report. The first
  `pathing-course` failure was my own geometry: the staircase's top step sat under the goal platform,
  making a 2-block wall with placing disabled. Every generated course must be solvable by
  construction, which is why `FuzzPathingScenario` gives walls gaps, pits ramps, pools dry lanes.
- **`java.util.Random` correlates across small sequential seeds.** Seeds 1-6 produced zero walls in
  16 features (a 1% event). Use `SplittableRandom`. `FuzzPathingScenarioTest` guards this.
- **A finished Minecraft does not reliably exit.** It logs `Stopping!` and the JVM sits resident for
  minutes holding ~2GB. Never wait on process exit; wait on the report's `complete` flag and reap.
- **Never `pkill -f` on a path or script name.** It matches file managers, editors, and your own
  shell — this bit me three separate times (`dolphin`, my own bash, the runner). Filter on
  `comm == "java"` first, then match the instance path.
- **Never overwrite the jar while Minecraft is running** (`ZipException: invalid LOC header`).
- **`/fill` refuses unloaded chunks.** Staging teleports first and waits for the arena's chunks to
  reach the client.
- **`/fill` caps at 32768 blocks.** `TestArena.wipe()` splits the footprint into slices.
- **Arena floors are slabs, not one layer.** The suite origin is lifted to at least 8 above the world
  floor (superflat puts you 4 above bedrock), so a one-layer floor would have air under it and pools
  would drain through.
- **`time set noon` is valid** in 26.1.2 (`TimeCommand` has a `timemarker` branch). A delegated audit
  claimed otherwise from reading `TimeArgument` alone; the game log disproved it. Verify delegated
  findings.
- **The shulker box `{Items:[{id,count,Slot}]}` NBT is valid** in 26.1.2 despite the 1.20.5 component
  migration — verified through `BlockInput.place` → `ContainerHelper.loadAllItems` →
  `ItemStackWithSlot.CODEC`, and corroborated by `restock-from-box` passing.
- **3 pre-existing test failures** in `RestockBoxEstimateTest` (`Components not bound yet` — no
  Minecraft bootstrap in the test JVM). Not yours; don't chase them unless fixing them is the task.
- **Registration order invariants** (from FORK-NOTES §4): `RestockProcess` must be registered before
  `BuilderProcess`, and both `RestockProcess` and `ShelterProcess` must keep `isTemporary() == true`.

---

## 4. Harness internals worth knowing

- **Sharding**: the autorun flag file's *contents* are the scenario list, one name per line; blank
  means the curated suite. Unknown names are skipped with a warning.
- **Arena slots**: 8, reused round-robin, 512 blocks apart. `wipe()` clears the whole footprint before
  each scenario stages, so a scenario never inherits the last occupant's leftovers. Without this a
  300-scenario world grew unboundedly (92MB after 22 scenarios).
- **Reports** are rewritten after *every* scenario (`complete: false`), and only the final write
  produces a timestamped copy and sets `complete: true`. A crash at scenario 59 of 62 keeps the
  other 58.
- **Rejected commands** are detected via `ClientboundSystemChatPacket` and fail staging loudly.
  `/fill`'s harmless "No blocks were filled" is deliberately not treated as a rejection.
- **Settings** are applied per scenario from `settings()` and restored afterwards.

---

## 5. Proposed tests

Ordered by value. The steer from the session: **fewer basic navigation tests** (77 for 77), **more
directional building and more of the never-exercised fork features**.

### A. Directional / orientation building — expand `build-directional`

The current test is 4 stairs on one axis, the easy case. Each item below is a distinct way
`buildOrientBeforePlacing` can be wrong. Assertions must compare the relevant **state properties**,
not just the block, and must distinguish *missing* from *misoriented* — misoriented means the builder
thought it was done.

Space orientation-sensitive blocks apart where they would otherwise negotiate shared state
(stairs `shape`, chests `type`, walls/fences/rails connections), or the comparison fails for reasons
unrelated to aim.

1. **stairs-halves** — 4 facings × `half=top`/`bottom` (8 blocks). Top-half needs clicking the upper
   part of a face; likely the first real failure.
2. **stairs-shapes** — deliberate inner/outer corners. Note `shape` is world-computed, so the
   schematic must ask for what the world will actually produce.
3. **logs-axes** — `oak_log` on `axis=x/y/z`.
4. **observers** — 6 facings including up and down.
5. **pistons** — 6 facings; sticky and normal.
6. **repeaters** — 4 facings × `delay=1..4`.
7. **comparators** — 4 facings × `mode=compare/subtract`.
8. **redstone-torch-wall** — wall torch `facing`, which depends on which side was clicked.
9. **glazed-terracotta** — 4 facings; the classic Baritone orientation pain point.
10. **trapdoors** — `facing` × `half` × `open`.
11. **doors** — two-block, `facing` × `hinge=left/right`, upper and lower halves consistent.
12. **slabs** — `type=top/bottom/double`; double requires placing into an existing slab.
13. **chests** — `facing`, and `type=single/left/right` for doubles.
14. **furnace / dropper / dispenser** — `facing`; dropper and dispenser include vertical.
15. **buttons and levers** — `face=floor/wall/ceiling` × `facing`.
16. **signs and banners** — `rotation=0..15` (16 states from look angle alone; a fine-grained test of
    aim precision).
17. **rails** — `shape` straight/curved/ascending, which depends on neighbours.
18. **redstone-circuit** — integration: build a working repeater + comparator + torch + dust circuit
    and assert it *functions* (e.g. a lamp lights), not just that blocks match.

### B. Never-exercised fork features (FORK-NOTES §6 lists these as never run)

19. **shulker-dump-trip** — fill the inventory during a build, verify a dedicated unload trip happens
    and that it unloads *everything*, not just enough to clear `shulkerDumpWhenFreeSlotsBelow`.
20. **dump-picks-best-box** — two registered boxes: one nearly full and close, one empty and far.
    `estimatedFreeSlots()` scoring should pick the far empty one. This is the specific concern flagged
    in FORK-NOTES §6 as untested and possibly too optimistic.
21. **dump-multi-box** — more rubble than one box holds; verify it continues to the next box and that
    hitting `shulkerDumpMaxBoxesPerTrip` is logged rather than reported as "the depot is full".
22. **junk-disposal** — `restockDumpJunk`: non-schematic blocks deposited, tools/weapons/armour/food
    and `acceptableThrowawayItems` kept, shulker boxes never deposited.
23. **indexboxes-all** — `#indexboxes all` re-checks every box, not just unindexed ones.
24. **stale-index-fallback** — index says a box holds the material, it doesn't; verify the index is
    corrected to reality and the next candidate is tried *without* returning to the build.
25. **missing-box-flagging** — break a registered box with the chunk loaded; verify it is flagged
    `MISSING` and **not** deleted, and that an unloaded chunk never triggers the flag.
26. **unobtainable-material** — schematic needs a material no box has; verify the build completes
    around it via the normal "Done building" path instead of stalling.
27. **reroute-clamp** — `builderMaxReroutes`: create goal churn and verify it commits to a path for
    `builderRerouteCommitTicks` instead of re-planning forever.
28. **backfill-vs-builder** — verify backfill neither records nor fills positions inside the active
    schematic's bounds.
29. **backfill-path-lookahead** — a hole dug for a movement several steps ahead must not be refilled.
30. **backfill-materials** — `backfillBlocks` restricts backfill to rubble; verify it never spends
    build materials.
31. **builder-descent** — the original upstream bug: descending through your own build must not
    become break → replace → break forever.
32. **allowInventory-warning** — with `allowInventory false`, verify the one-time warning fires and
    the build doesn't silently fetch materials it cannot use.

### C. Shelter — quarantine these from the pass/fail gate

Needs hostile mobs, so it must undo the harness's peaceful/no-spawn determinism and summon
deliberately. Expect flakiness; treat failures as signal to investigate, not as a red suite.

33. **shelter-on-attack** — summon a zombie, take `shelterMinHits` hits, verify retreat to the nearest
    registered box.
34. **shelter-sleep** — night plus a bed within `shelterBedSearchRadius`; verify it walks there and
    enters.
35. **shelter-refusal-retry** — monsters nearby so the bed refuses; verify it returns to the box,
    waits `shelterRetryDelayTicks`, and retries up to `shelterMaxSleepAttempts`.
36. **shelter-ignores-fall-damage** — the discrimination test, and the most valuable of the four: fall
    damage and lava must **not** trigger sheltering, since running away makes those worse. A naive
    health-drop check fires here; `ThreatBehavior` should not.

### D. Other processes with no coverage at all

37. **mine-quantity** — `#mine 8 iron_ore` in a staged ore field; assert inventory count.
38. **mine-legit** — `legitMine` only mines visible ore.
39. **sel-cleararea** — `#sel cleararea` empties the selection.
40. **torch-grid** — `TorchGridSchematic` places torches at the expected spacing.
41. **pickup-blocks** — `PickupBlocksProcess` collects dropped items.
42. **farm** — `FarmProcess` on staged crops.

### E. Regression sample and flakiness

43. **fuzz-1 .. fuzz-20** — keep a small navigation sample as a regression guard. Drop the other 220.
44. **flakiness sweep** — run ~5 scenarios 10× each and report tick-count variance. Current runs look
    suspiciously deterministic (identical tick counts across runs), so quantifying real
    nondeterminism would tell us how much to trust a single failure.

---

## 6. Open questions

- **`waterCostMultiplier` default is 2.0, and may be too high.** With the base cost bug fixed,
  upstream's `20/2.2` is already ~2× walking. Worth a `water-detour` variant at 1.0: if that also goes
  around, the multiplier isn't earning its place and the real fix was the initialiser alone.
- **The `build-directional` pass is only the easy case** — one axis, bottom halves. Do not read it as
  "orientation works".
- **Nothing is committed.** 20 modified files and ~20 untracked paths. A `git add -A && git commit`
  before further work would make the next handoff much easier, and would also move the `git describe`
  version so built jars stop colliding with installed ones by filename.
