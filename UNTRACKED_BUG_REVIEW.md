# Untracked bug review

Reviewed: 2026-07-28  
Baseline: `35b69368` (`shulker-restock`) plus the current uncommitted shelter, pickup, builder-protection, and capacity-aware unloading changes.

## Tracking and validation checks

These findings were checked against:

- `FORK-NOTES.md`, including its “less exercised” and “not yet exercised” lists;
- `UPSTREAM_BUG_BACKLOG.md`;
- repository TODO/FIXME comments;
- all issues in `BlueMonkey262/baritone` (the repository currently has none);
- targeted issue searches in `cabaletta/baritone` for the new shelter, restock, box-indexing, and pickup behavior.

None of the defects below is recorded in those places. The upstream pickup feature request
[#1043](https://github.com/cabaletta/baritone/issues/1043) asks for pickup functionality but does
not describe finding 8.

`./gradlew test --rerun-tasks` passes from a fresh compile: 45 tests, 0 failures. There are no
automated state-machine tests for `ShelterProcess`, `RestockProcess`, or `PickupBlocksProcess`, so
the successful suite does not exercise the failures below.

## Findings

### 1. High — a refused bed is permanently excluded, so the configured sleep retries never happen

Locations:

- `src/main/java/baritone/process/ShelterProcess.java:429`
- `src/main/java/baritone/process/ShelterProcess.java:477`
- `src/main/java/baritone/process/ShelterProcess.java:575`

When entering a bed times out, `rejectBed` adds its head to `rejectedBeds` and sends the player back
to the rally box. After the regroup delay, `findBed` filters every head in that set. With the common
case of one nearby bed, the second search therefore returns no bed and transitions to `WAITING`.
`shelterMaxSleepAttempts` defaults to 5, but only one attempt can occur against that bed.

Reproduction:

1. Register a rally box near a single bed.
2. Set `shelterOnAttack`, leave `shelterMaxSleepAttempts` at 5, and arrange for monsters to make
   sleeping fail.
3. Trigger sheltering at night.
4. After the first refusal, observe one trip back to the box followed by “no bed within ...” rather
   than a second attempt.

The code needs to distinguish a bed that is structurally invalid/unreachable from a valid bed that
was temporarily refused. Only the first category should be blacklisted for the run; a refused bed
must become eligible again after `shelterRetryDelayTicks`.

### 2. High — completion of the restock handoff lets another process run for one tick mid-shelter

Locations:

- `src/main/java/baritone/process/ShelterProcess.java:369`
- `src/main/java/baritone/process/RestockProcess.java:995`
- `src/main/java/baritone/utils/PathingControlManager.java:157`

While unloading, shelter sees an active restock process and returns `DEFER`. The scheduler then calls
restock later in the same pass. If restock finishes on that call, `finishTrip` resets itself and also
returns `DEFER`; the scheduler continues down the active-process list and can run the interrupted
builder, miner, backfill, or pickup process. Shelter is not evaluated again until the next tick.

That process can issue a new work goal and, if already in reach, perform a break/place action during
what is supposed to be an uninterrupted safety retreat. The special pause used when the handoff is
started prevents the same gap on entry but there is no equivalent on handback.

The restock/shelter protocol needs an explicit completion handshake that consumes the completion
tick, or the scheduler must re-evaluate the higher-priority shelter process before allowing lower
priorities to run.

### 3. Medium — shelter unloading can walk away from the rally box it just reached

Locations:

- `src/main/java/baritone/process/ShelterProcess.java:252`
- `src/main/java/baritone/process/ShelterProcess.java:343`
- `src/main/java/baritone/process/RestockProcess.java:322`

Shelter first chooses the nearest registered box as `rallyBox` and walks there. It then calls the
generic `requestDeposit`, which independently rebuilds the candidate list and sorts capacity before
distance. If the rally box is indexed as partly full and a farther box is estimated to fit the
whole load, restock immediately walks away to the farther box.

This contradicts the documented “unload into the box we retreated to” behavior and can undo the
safety value of reaching the rally point. It also means a later rejected-bed path goes back to the
original rally box, not necessarily the box where the player was left after unloading.

The shelter handoff should pass a preferred/fixed target. At minimum, it should attempt the rally
box first and make any fallback away from it an explicit shelter policy.

### 4. Medium — `#addbox` can index one box with a different nearby box's contents

Locations:

- `src/main/java/baritone/command/defaults/RestockBoxCommand.java:106`
- `src/main/java/baritone/command/defaults/RestockBoxCommand.java:246`

`addBox` verifies that the requested position contains a shulker box, but `indexIfOpen` only verifies
that some foreign container is readable and that the player is within six blocks of the requested
position. It never proves that the open menu came from that position.

Reproduction:

1. Put boxes A and B within six blocks of the player and give them different contents.
2. Open A.
3. Run `#addbox <B coordinates>`.
4. B is persisted with A's contents and timestamp.

The client menu does not itself carry a block position, so eager indexing should be skipped unless
the position of the interaction that opened the current menu was recorded and exactly matches the
registration target.

### 5. Medium — “extra stacks” always means 64 items, over-fetching low-stack-size build materials

Locations:

- `src/main/java/baritone/process/RestockProcess.java:435`
- `src/main/java/baritone/process/RestockProcess.java:676`

The fetch target is `missing + restockExtraStacks * 64`, irrespective of the wanted item's maximum
stack size. A shulker box is itself a placeable block item with a stack size of one; banners and
other block items also need not stack to 64. With the default one extra stack, a build short one
shulker box can therefore keep shift-clicking until it has taken 65, filled the inventory, or
emptied the source box instead of stopping after two.

Use `wantedItem.getDefaultMaxStackSize()` for the surplus conversion. Compute the target in `long`
and saturate it before storing it in `int`; the currently documented upper setting bound can still
overflow when the positive shortfall is added.

### 6. Medium — a deposit sync timeout is not excluded from later deposit trips

Locations:

- `src/main/java/baritone/process/RestockProcess.java:305`
- `src/main/java/baritone/process/RestockProcess.java:650`

When a box opens but never becomes readable, the timeout path adds it only to `failedThisBuild` and
transitions to `CLOSING`. Ordinary material lookup excludes `failedThisBuild`, but
`requestDeposit` excludes only missing boxes and `fullThisBuild`.

If a later candidate unloads at least one stack, `depositImpossible` remains false. The next time
the inventory fills, the timed-out box is eligible again and can be selected again, repeating the
walk and full timeout every unloading cycle.

For a deposit-only trip, the sync timeout should use `failCurrentBox()` or also add the target to
the set that deposit candidate selection excludes. Alternatively, deposit candidate selection
should exclude operational failures as well as known-full boxes.

### 7. Low — `shelterMinHits` values above 16 silently disable sheltering

Locations:

- `src/main/java/baritone/behavior/ThreatBehavior.java:42`
- `src/main/java/baritone/behavior/ThreatBehavior.java:96`

Recent hits are stored in a fixed 16-element array, with the oldest entry discarded when it fills.
`underAttack` accepts an unrestricted `shelterMinHits` and compares it with the number retained.
Consequently, setting the threshold to 17 or more makes the condition impossible even during a
continuous attack.

Use a deque sized to the configured threshold/window, or validate/clamp the setting to the actual
retention capacity and report the correction.

### 8. Low — pickup's spatial match can claim unrelated pre-existing drops

Locations:

- `src/main/java/baritone/process/PickupBlocksProcess.java:45`
- `src/main/java/baritone/process/PickupBlocksProcess.java:96`
- `src/main/java/baritone/process/PickupBlocksProcess.java:114`

An expected drop records only a block position and five-second expiry. During that window, every
living `BlockItem` entity within two blocks of the position matches, regardless of whether it
existed before the break or was produced by another player/event. Expectations are not consumed
when their actual drop is collected, so an unrelated nearby item can be selected afterward.

This violates the class's promise to collect the drops from completed Baritone breaks and can pull
the active job off course for unrelated loot. Matching needs at least a spawn-time/entity-age
boundary and a consumable association; recording the expected item types where they can be
determined would narrow it further.

## Coverage gaps worth closing with the fixes

- A deterministic `ShelterProcess` test should cover one refused bed, repeated retries, restock
  completion, and preservation of control until shelter finishes.
- A command test should open A and register nearby B, proving no cross-box index is written.
- Restock tests should use max stack sizes 1, 16, and 64 and cover a container sync timeout followed
  by a successful second candidate and a later deposit request.
- Pickup tests should include a pre-existing nearby item and the item spawned by the recorded break.
