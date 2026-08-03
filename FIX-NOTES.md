# Fix notes

Applied the eight recommendations from `TRIAGE.md`. All scenarios remain uncurated and retain
their existing stable names.

## `path-parkour-on`

Fixed the staging predicate to check the actual side-wall blocks at `(0, 1, -2)` and
`(GOAL_X, 1, 2)`. The sealed corridor geometry is unchanged. The verdict still measures the
unchanged gap and completed goal.

## `pickup-dropped-stack-merge`

Moved the stone target onto the floor at `(TARGET_X, 0, 0)` and started the water at
`TARGET_X + 1`, so the miner can stand on the floor while the fresh drop is displaced. Updated
`stagingComplete` to assert the floor stone and both water endpoints. The verdict remains based on
the target, cobblestone stack reaching 64, no loose cobblestone entity, and the protected diamond
remaining at 1.

## `restock-distance-filter-near-fallback`

Changed the expected whole-slot transfer from `near=1` to `near=0`. The verdict now records and
checks the durable delta `near 2 -> 0`, `far 2 -> 2`, and player white concrete `0 -> 1`; the
far-box position sample remains a negative guard against actually reaching the excluded box.

## `restock-extra-stacks-zero`

Added the staged source-content assertion (`67` white concrete) and replaced the unreliable box
feet sample with authoritative source contents. Completion now requires source `67 -> 64`, a
maximum carried count of exactly 3, all three targets, and no surplus carried afterward.

## `restock-master-off-no-trip`

Recorded the starting carried and source counts. The verdict now rejects only a source-box mutation
or an increase in carried white concrete; consuming the staged one-block supply is allowed. The
measurement note reports both start and current counts at each poll.

## `restock-open-container-one-shot`

Added the staged source-count assertion (`8`) and replaced process-label, inventory-maximum, and
feet-radius requirements with the durable source-box decrease plus the target and closed-door
state. The source decrease is recorded in the verdict note.

## `restock-switch-cancels-old-path`

Added exact staged counts for both candidate boxes. The verdict now requires the blocked first box
to remain at 8 while the fallback source decreases after target completion; it no longer depends
on sampled feet-radius visits or a process label. Source counts are also used in progress and
timeout diagnostics.

## `shelter-preferred-rally-unload`

Replaced `Items.WHITE_CONCRETE` in `JUNK` with `Items.COBBLESTONE`, leaving the separate build
supply at exactly four white concrete and making the staging predicate satisfiable.

No scenario was narrowed and no behavior outside `src/main/java/baritone/testing/` was changed.

## Verification and guess flags

`GRADLE_USER_HOME=/home/eli/.gradle-isolated/fixt2 ./gradlew :test --no-daemon` passed. No Minecraft
run was performed, as required. The whole-slot source-count expectations (`near 2 -> 0`, the
exact-shortfall source `67 -> 64`, and the fallback source decrease) follow the observed behavior
recorded in `TRIAGE.md`; those are the only runtime assumptions beyond the triage evidence.
