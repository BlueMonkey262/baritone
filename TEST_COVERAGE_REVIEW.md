# Test Coverage Review

Reviewed 2026-07-28 on branch `shulker-restock` at commit `35b69368`
(with the current uncommitted working tree included).

## Executive summary

The repository currently has a very small unit-test suite relative to its size:

- 8 test source files
- 16 `@Test` methods
- 45 executed test cases (31 are parameterized open-set cases)
- 360 production Java source files
- Approximately 48,700 physical lines of production Java

A one-off JaCoCo run over the `main`, `api`, and `launch` source sets measured:

| Metric | Covered | Total | Coverage |
| --- | ---: | ---: | ---: |
| Lines | 260 | 14,736 | 1.76% |
| Branches | 103 | 8,753 | 1.18% |
| Methods | 73 | 2,789 | 2.62% |
| Classes | 18 | 414 | 4.35% |

These figures exclude `buildSrc` and loader-specific source sets, so whole-project
coverage is slightly lower. JaCoCo is not configured in the normal build; these
numbers came from a temporary local reporting configuration.

## Existing tests

The suite currently covers:

- Binary heap and linked-list open-set behavior
- Several `BuilderProcess` helper/state cases
- Goal equality/heuristic behavior for `GoalGetToBlock`
- Movement-cost constants
- Coordinate and block-position utility behavior
- Basic schematic composition/fill behavior
- Pathing block-type values
- Minecraft `AABB.expandTowards` cases used as an Elytra hitbox reference

The measured production files with meaningful execution include:

- `BuilderProcess`
- `BinaryHeapOpenSet`
- `LinkedListOpenSet`
- `PathNode`
- `ActionCosts`
- `GoalGetToBlock`
- `BetterBlockPos`
- `PathingBlockType`
- Several schematic classes

## Important limitations

The raw test count overstates effective protection:

- `ElytraHitboxTest` exercises Minecraft's `AABB` implementation rather than
  Baritone code. Baritone could regress while this test continued to pass.
- `CachedRegionTest` repeats the coordinate-packing arithmetic instead of calling
  `CachedRegion`, so it does not directly protect that implementation.
- The open-set tests use `Math.random`, making them technically nondeterministic,
  although a random failure is unlikely.
- Some reported coverage in utility classes is incidental class initialization,
  not deliberate behavioral testing.

There is effectively no automated coverage for:

- Process scheduling and priority/preemption behavior
- Restocking and container interaction
- Shelter, threat detection, and dropped-block pickup
- Most builder state transitions
- Behavior classes
- Cache implementation and persistence
- Commands and argument handling
- Mixins, launchers, and loader integrations
- Packet/menu timing and failure handling
- End-to-end or small-world pathing scenarios

Approximate line coverage in several important areas illustrates the imbalance:

| Area | Approximate coverage |
| --- | ---: |
| `baritone/process` | 0.3% |
| `baritone/behavior` | 0% |
| `baritone/cache` | 0% |
| Core pathing | 4.2% |
| Commands | 0% |
| API | 5.2% |
| Launch code | 0% |

## A reasonable target

Without attempting to exhaustively enumerate Minecraft states, a focused suite of
roughly 200–350 well-chosen tests could reasonably reach:

- 50–60% overall line coverage
- 35–45% overall branch coverage
- 70–85% line coverage in fork-specific builder/restock/shelter code
- At least 80% coverage of newly changed lines

About 65% overall line coverage may be possible, but returns will diminish because
mixins, rendering, live movement, networking, and loader integration require more
expensive integration infrastructure. Coverage percentage should remain a signal,
not the goal by itself.

Suggested targets by code type:

| Code type | Line target | Branch target |
| --- | ---: | ---: |
| Pure utilities, goals, schematics, serialization | 80–90% | 70–85% |
| Processes and behaviors | 65–75% | 55–70% |
| Pathing and movements | 50–65% | 40–55% |
| Mixins and loaders | Prefer smoke/integration tests over a line target | — |

## Efficient route to that target

The highest-leverage investment is a compact deterministic process-test harness
with:

- A tiny scripted block-grid world
- Controllable player position, health, inventory, and inputs
- Fake path-calculation outcomes
- Scripted container/menu events
- A deterministic tick clock
- The real `PathingControlManager` coordinating processes

That harness would allow many state-machine paths to be tested without starting a
full Minecraft client. A practical portfolio would be:

| Test area | Approximate cases |
| --- | ---: |
| Scheduler ownership, priority, cancellation, and same-tick handoff | 15–25 |
| Shelter, threat, and pickup behavior | 30–45 |
| Restock and container state machines | 40–60 |
| Builder state transitions and material handling | 40–60 |
| Schematics, goals, and pure utilities | 25–40 |
| Small deterministic pathing worlds | 30–50 |
| Persistence and commands | 20–30 |
| Loader/startup and selected integration scenarios | 20–40 |

Parameterized and property-style tests should be used for coordinate boundaries,
inventory layouts, stack sizes, block variants, and scheduler permutations. This
provides hundreds or thousands of input examples without requiring thousands of
separately maintained test methods.

## Recommended first milestone

Build the process harness and add approximately 60 regression-focused cases around:

1. `PathingControlManager`
2. `RestockProcess`
3. `ShelterProcess`
4. `ThreatBehavior`
5. The known defects in `UNTRACKED_BUG_REVIEW.md`

Initially, avoid imposing a high repository-wide coverage gate on legacy code.
Instead:

- Require a regression test for every fixed defect.
- Require roughly 80% changed-line coverage for new or materially changed logic.
- Ratchet the global floor upward only as the suite grows.
- Keep a small number of full-client smoke tests for integration boundaries that
  unit tests cannot model reliably.

This approach should substantially improve confidence in the fork's most
failure-prone behavior without creating or maintaining tens of thousands of tests.
