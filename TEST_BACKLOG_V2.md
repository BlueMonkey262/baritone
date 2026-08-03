# Harness scenario backlog V2

This is a backlog of new live-world scenarios, not an implementation plan. I treated the current
`TestingBehavior` registration block and every existing entry in `TEST_BACKLOG.md` as occupied,
including the hand-written restock, deposit, item-saver, mining, placement, and shelter reproductions
and the generated placement catalog. Names below are intentionally new. The proposals use player
inventory, world blocks, player position, process activity, and dropped-entity state as their
oracles; they do not use chat as a verdict.

Unless a proposal says otherwise, stage with plain blocks, `/fill`, `/setblock`, `/give`, and
`/tp`. Do not embed a shulker's item NBT in a portable fixture. Where a box is needed, parameterise
the version-specific staging command for the loader under test. A normal scenario should use a
120–240 tick budget; only the explicit performance and shelter-wait cases need more.

## Restock state machine

### 1. `restock-master-off-no-trip`

With a registered source box but `restockFromBoxes=false`, a build may stop at a real material
shortage exactly as upstream does. **Failure line:** fail if the player reaches the box or the
shortage item appears in player inventory; the first part of the build must already have placed a
control block, proving the process ran. **Code:** `RestockProcess.requestRestock`; `BuilderProcess.onTick`.
Difficulty: trivial.

### 2. `restock-index-off-speculative`

With `restockIndexBeforeBuild=false`, an unindexed box is tried on demand after the builder has
placed at least one target, rather than requiring a pre-build indexing trip. **Failure line:** fail
if the target ring completes without a post-placement box trip, or if it pauses while the player
never visits the staged box; completion and the player’s increased material count are independent
evidence. **Code:** `RestockProcess.requestRestock`, `findCandidates`; `BuilderProcess.recalc`.
Difficulty: moderate.

### 3. `restock-reindex-clears-giveup`

After one build pass marks a material unobtainable, an explicit `#indexboxes all` refresh must make
newly supplied stock eligible again. **Failure line:** fail if the second pass still leaves the
target air after the player has visited the box and received the item; the first pass’s partial
placement proves that the second pass actually exercises the stale state. **Code:**
`RestockProcess.requestIndexing`, `clearUnobtainable`; `BuilderProcess.fullRecalc`. Difficulty:
needs-new-harness-capability (issue a command or add a phase transition during a scenario).

### 4. `restock-switch-cancels-old-path`

When the first candidate box becomes unreachable during a trip, switching to the next candidate
must cancel the old path segment before walking to the second box. **Failure line:** fail if the
player keeps oscillating toward the sealed first box, or if the second box is reached but the build
does not resume; target placement and final player position provide positive evidence. **Code:**
`RestockProcess.failCurrentBox`, `startPathing`; `ProcessScheduler.executeProcesses`. Difficulty:
moderate.

### 5. `restock-open-container-one-shot`

Opening a registered box must be one genuine right-click and then a stable readable menu, not a
held right-click that can activate adjacent blocks. **Failure line:** fail if a neighboring button,
door, or placed block changes while the player reaches and transfers stock; the player inventory
gain and completed build prove the intended container interaction occurred. **Code:**
`RestockProcess.tickOpening`; `ContainerInteractionBehavior.resetSync`; `BlockPlaceHelper`. Difficulty:
moderate.

### 6. `restock-open-menu-identity`

An already-open menu for box A must never be accepted as the menu for box B when the path changes
targets. **Failure line:** fail if the build gains the wrong material or transfers while the player
is at B but the intended B interaction has not completed; reaching B and the final player inventory
are independent evidence. **Code:** `RestockProcess.tickOpening`, `tickAwaitingSync`;
`ContainerInteractionBehavior.openContainer`. Difficulty: moderate.

### 7. `restock-quickmove-server-confirmed`

Optimistic local quick-move predictions must not cause a second transfer until the first local menu
state is settled, while still taking the requested amount. **Failure line:** fail if the player
inventory exceeds the requested shortfall plus configured surplus, or if the build resumes without
the expected inventory gain; the world target count proves work continued. **Code:**
`ContainerInteractionBehavior.quickMove`; `RestockProcess.tickTransferring`. Difficulty: needs-new-
harness-capability (controlled delayed packet or a deterministic server-lag fixture).

### 8. `restock-full-live-box-falls-through`

If an indexed source is full when opened, restocking must inspect the live menu and continue to a
later viable box. **Failure line:** fail if the bot repeatedly opens the full source or abandons the
build after reaching it; the later box visit, player inventory gain, and completed target are
independent evidence. **Code:** `RestockProcess.tickAwaitingSync`, `hasItemInContainer`,
`failCurrentBox`. Difficulty: moderate.

### 9. `restock-open-timeout-zero`

`restockOpenTimeoutTicks=0` must expire safely and leave the process able to hand off to a later
candidate rather than making a negative timeout loop. **Failure line:** fail if the player remains
at the first box beyond the one-tick budget, or if the fallback box is never visited despite a
successful build. **Code:** `RestockProcess.tickOpening`, `tickAwaitingSync`; `Settings` timeout.
Difficulty: moderate.

### 10. `restock-distance-filter-with-near-fallback`

`restockMaxDistance` must exclude a stocked box beyond the limit while still allowing a nearer
registered source to finish the build. **Failure line:** fail if the player crosses the distance
limit to the far box, or if the near box is ignored; the near-box visit and completed blocks prove
the bot acted. **Code:** `RestockProcess.findCandidates`, `requestRestock`. Difficulty: trivial.

### 11. `restock-extra-stacks-zero`

With `restockExtraStacks=0`, the player should receive the immediate shortfall and no speculative
stack. **Failure line:** fail on an inventory count above the exact requested amount after a build
whose requested amount is not stack-aligned; placement of the requested blocks proves the transfer
was real. **Code:** `RestockProcess.fetchTarget`, `requestRestock`. Difficulty: trivial.

### 12. `restock-negative-extra-stacks-clamped`

A negative `restockExtraStacks` value must be treated as zero rather than reducing the requested
shortfall or producing a negative arithmetic path. **Failure line:** fail if the build receives
less than the required material or if the player loops at the box; completed target blocks and the
player inventory count are the evidence. **Code:** `RestockProcess.fetchTarget`. Difficulty:
trivial.

### 13. `restock-stack-one-surplus`

For a stack-size-one item used as a tool or non-stackable building aid, one extra stack means one
spare item, not 64. **Failure line:** fail if the player receives 64 copies or cannot finish two
tool-consuming work segments; the two separated work areas prove the spare was used. **Code:**
`RestockProcess.fetchTarget`, `requestTool`. Difficulty: moderate.

### 14. `restock-allow-inventory-off-bounded`

With `allowInventory=false`, a successful fetch into the main inventory must produce a bounded,
diagnosable stop rather than an infinite attempt to use an unreachable hotbar item. **Failure line:**
fail if the bot keeps pathing or reopens the box after the item is present; a visited box, changed
player inventory, and partial build prove it reached the intended state. **Code:**
`RestockProcess.requestRestock`; `InventoryBehavior.throwaway`; `BuilderProcess`. Difficulty:
moderate.

### 15. `restock-no-boxes-bounded`

When no box is registered, a shortage must be latched as a bounded result instead of re-requesting
the same trip every tick. **Failure line:** fail if the player moves after the first shortage or if
the same build state consumes the full long budget; earlier placed blocks and a stable stopped
process are independent evidence. **Code:** `RestockProcess.requestRestock`, `markUnobtainable`;
`BuilderProcess.onTick`. Difficulty: trivial.

### 16. `restock-tool-spares-one-trip`

`requestTool` must fetch configured spare tools in one trip and reuse them across two later mining
segments. **Failure line:** fail if the player returns to the box for each replacement or if either
segment breaks the protected tool; both mined target sets and the player’s remaining spare count
provide evidence. **Code:** `RestockProcess.requestTool`, `swapBackTool`, `afterContainerWork`;
`MineProcess`. Difficulty: moderate.

### 17. `restock-failed-box-quarantine`

Once a box fails pathing during the current build, it must not be retried every tick while another
candidate remains. **Failure line:** fail if the player makes repeated attempts toward the same
sealed coordinates, or if the later candidate is not used; a completed target and bounded tick count
prove forward progress. **Code:** `RestockProcess.failCurrentBox`, `failedThisBuild`, `findCandidates`.
Difficulty: moderate.

### 18. `restock-restored-missing-box-retry`

A box marked missing after a loaded-block removal must become usable again after it is restored and
explicitly re-registered. **Failure line:** fail if the restored box remains permanently excluded
after the player reaches it and receives material; the new build pass proves the retry is not a
no-op. **Code:** `RestockBoxCollection`, `RestockBoxCommand`, `RestockProcess.findCandidates`.
Difficulty: needs-new-harness-capability (restore/register state during one run).

## Unloading and junk policy

### 19. `dump-no-junk-no-trip`

An inventory-full check must not start an unload journey when every carried item is worth keeping.
**Failure line:** fail if the player leaves the active work area or an unrelated block changes; a
control block placed after the full-inventory check proves the job continued. **Code:**
`RestockProcess.requestDeposit`, `junkStacksCarried`; `BuilderProcess.inventoryIsFull`.
Difficulty: moderate.

### 20. `dump-trigger-threshold-exact`

The dedicated `shulkerDumpWhenFreeSlotsBelow` trigger must fire below the threshold and not at the
threshold itself. **Failure line:** fail if a trip begins with exactly the configured free-slot
count, or fails to begin one slot below it; reaching the box and resuming work are positive
evidence. **Code:** `BuilderProcess.inventoryIsFull`, `MineProcess.inventoryIsFull`; `Settings`.
Difficulty: moderate.

### 21. `dump-live-capacity-mismatch-fallback`

When the index overestimates a box’s free slots, the live deposit result must move on to a box that
can accept the remaining rubble. **Failure line:** fail if rubble remains on the player while a
fallback box exists, or if the bot retries the mismatched box; reaching both boxes and resuming the
clear proves the fallback path ran. **Code:** `RestockProcess.tickDepositing`, `failCurrentBox`.
Difficulty: moderate.

### 22. `dump-deposit-timeout-quarantine`

A box whose deposit session times out must be excluded from later unload trips in the same build,
while a second box remains eligible. **Failure line:** fail on a repeated return to the timed-out
box or on a full-inventory stall despite the fallback; later work and the second-box visit are
independent evidence. **Code:** `RestockProcess.failCurrentBox`, `depositOnly`, `failedThisBuild`.
Difficulty: needs-new-harness-capability (deterministic open/sync timeout).

### 23. `dump-reindex-after-deposit`

After a successful deposit, the live box estimate must be refreshed so the next trip does not select
it as empty. **Failure line:** fail if the second trip again targets a box that the first trip just
filled while another box has capacity; two completed work segments and their player positions prove
both trips. **Code:** `RestockProcess.afterContainerWork`, `RestockBoxCollection` indexing.
Difficulty: moderate.

### 24. `dump-whole-load-before-return`

An unload trip should empty all eligible junk from one capable box before returning to the worksite,
not stop merely because the trigger threshold is satisfied. **Failure line:** fail if the player
returns while eligible rubble remains in player inventory and no cap was reached; resumed clearing
is independent evidence. **Code:** `RestockProcess.tickDepositing`, `finishTrip`.
Difficulty: moderate.

### 25. `dump-keep-throwaway-zero`

With `shulkerDumpKeepThrowawayStacks=0`, configured throwaway blocks are eligible for unloading
while build materials remain protected. **Failure line:** fail if a full trip leaves a configured
throwaway stack in the player inventory despite spare box capacity, or if a required build block is
lost; the build’s completed target is the independent positive check. **Code:**
`RestockProcess.junkStacksCarried`, `keptThrowaway`. Difficulty: trivial.

### 26. `dump-keep-throwaway-multiple`

Keeping two configured throwaway stacks must preserve exactly that reserve while unloading the rest.
**Failure line:** fail if fewer than two stacks remain or if no junk is unloaded; the player must
reach a box and resume a clear with a verified inventory count. **Code:**
`RestockProcess.isJunk`, `keptThrowaway`; `Settings.shulkerDumpKeepThrowawayStacks`. Difficulty:
trivial.

### 27. `dump-keep-configured-throwaway-only`

The keep reserve must follow the live `acceptableThrowawayItems` setting rather than hard-coding
stone or cobblestone. **Failure line:** fail if a custom dirt reserve is deposited or an unconfigured
stone reserve is protected; the unload trip and resumed work show the decision was exercised. **Code:**
`RestockProcess.isJunk`, `keptThrowaway`; `InventoryBehavior.firstValidThrowaway`. Difficulty:
moderate.

### 28. `dump-raw-copper-bulk`

An allowlisted raw-copper drop may be deposited during a clear, while a currently requested block
remains in inventory. **Failure line:** fail if raw copper blocks the clear after the trip or if the
requested material disappears; the cleared blocks and player counts are independent evidence. **Code:**
`RestockProcess.isJunk`, `depositableBulkItems`; `BuilderProcess.inventoryWants`. Difficulty:
moderate.

### 29. `dump-raw-gold-bulk`

Raw gold must follow the same explicit bulk-item allowlist path as raw iron and raw copper. **Failure line:** fail if the gold remains in a full inventory and the clear stalls, or if an unrelated
non-allowlisted item is deposited; box arrival and resumed work prove the trip. **Code:**
`RestockProcess.isJunk`; `Settings.depositableBulkItems`. Difficulty: moderate.

### 30. `dump-custom-bulk-nondefault`

Replacing the bulk allowlist with a custom item must allow that item and reject the former defaults.
**Failure line:** fail if the custom item is not unloaded, or if a removed default item is treated as
junk; the player reaches the depot and the clear resumes as independent evidence. **Code:**
`RestockProcess.isJunk`, `worthKeeping`; `Settings.depositableBulkItems`. Difficulty: moderate.

### 31. `dump-active-substitute-kept`

Every configured `buildSubstitutes` option must count as wanted while the active schematic is using
one of the alternatives. **Failure line:** fail if the substitute needed by a later target is
deposited and the later target remains air; an earlier substitute placement and a resumed build
prove the active-job predicate ran. **Code:** `BuilderProcess.schematicWants`, `inventoryWants`;
`RestockProcess.isJunk`. Difficulty: moderate.

### 32. `dump-large-schematic-safe-default`

For a schematic larger than the palette-scan limit, the junk policy must conservatively keep blocks
instead of depositing unknown material. **Failure line:** fail if a later target cannot be completed
because its material was unloaded; the large build’s earlier and later placed blocks prove the
policy was exercised. **Code:** `BuilderProcess.schematicWants`, `paletteTooLarge`; `RestockProcess`.
Difficulty: needs-new-harness-capability (the fixture must exceed the scan limit without idling the
suite).

### 33. `dump-equippable-blockitem-guard`

A wearable block item such as a carved pumpkin must remain protected during unloading on every
supported version. **Failure line:** fail if the item leaves player inventory during a dedicated
deposit trip; the player’s return and resumed work prove the trip happened. **Code:**
`RestockProcess.isJunk`; pre-component loader shims. Difficulty: moderate, with a version-matrix
fixture because the guard differs between 1.20.1/1.21.1 and current versions.

### 34. `dump-full-depot-bounded`

When every eligible box is full, a dedicated unload trip must stop with a bounded result and let the
active job report its real inventory-full outcome. **Failure line:** fail if the player cycles among
boxes past the configured cap or remains in a path loop; the initial clear progress and bounded
process stop are independent evidence. **Code:** `RestockProcess.requestDeposit`, `fullThisBuild`,
`depositImpossible`. Difficulty: moderate.

## Shelter and threat handling

These need deterministic hostile damage. A `/summon` plus `/damage` fixture is preferable to
relying on mob AI; if the command syntax differs between the seven versions, parameterise it.

### 35. `shelter-threshold-one-hit`

With `shelterMinHits=1`, one hostile hit must interrupt an active mine and select a valid rally box.
**Failure line:** fail if the mob damages the player but the player keeps mining; a mined control
block before the hit and movement toward the box prove both sides of the transition. **Code:**
`ThreatBehavior.underAttack`; `ShelterProcess.requestShelter`. Difficulty: moderate.

### 36. `shelter-hit-window-expiry`

Hits separated by more than `shelterThreatMemoryTicks` must not meet the shelter threshold. **Failure line:** fail if the player retreats after the second delayed hit; mining progress after each hit is
independent evidence that the threshold window was actually tested. **Code:**
`ThreatBehavior.pruneExpired`, `recordHit`; `ShelterProcess.requestShelter`. Difficulty: moderate.

### 37. `shelter-projectile-attribution`

An arrow damage source must count as the hostile skeleton or pillager that fired it, not as an
unattributed projectile. **Failure line:** fail if two controlled arrows leave the player mining in
place; pre-hit and post-retreat positions prove a shelter transition. **Code:**
`ThreatBehavior.isHostileDamage`; `DamageSource.getEntity`. Difficulty: moderate.

### 38. `shelter-drowning-ignored`

Drowning damage must not trigger a retreat because the shelter behavior explicitly reacts only to
`Enemy` damage. **Failure line:** fail if the player leaves the water test area after drowning damage;
the mine or path process must continue to place/break a control block. **Code:**
`ThreatBehavior.isHostileDamage`, `onTick`; `ShelterProcess.requestShelter`. Difficulty: needs-new-
harness-capability (deterministic health/damage staging).

### 39. `shelter-starvation-ignored`

Starvation health loss must not be mistaken for a hostile hit. **Failure line:** fail if the player
retreats without an `Enemy` source while the active job continues making world progress; the control
placement after the health loss is the independent evidence. **Code:** `ThreatBehavior.onTick`,
`isHostileDamage`. Difficulty: needs-new-harness-capability.

### 40. `shelter-no-box-hands-back`

Under attack with no registered shelter box, the temporary process must decline and allow the active
job to make a bounded decision rather than claiming control forever. **Failure line:** fail if the
player freezes in place or repeatedly logs a retreat attempt while the mine’s target blocks remain
reachable; post-hit mining or a clean process stop is positive evidence. **Code:**
`ShelterProcess.requestShelter`, `nearestBoxWithReason`, `finish`. Difficulty: moderate.

### 41. `shelter-preferred-rally-unload`

After choosing a rally box, shelter unloading must hand that exact box to restocking before capacity
sorting considers farther alternatives. **Failure line:** fail if the player leaves the rally box for
another depot before unloading or resumes work with the carried rubble; retreat arrival, inventory
change, and return are independent evidence. **Code:** `ShelterProcess.tickUnloading`,
`RestockProcess.requestDeposit(..., preferredTarget)`. Difficulty: moderate.

### 42. `shelter-unload-disabled-still-shelters`

`shelterUnloadOnRetreat=false` must suppress only the deposit handoff, not the retreat and threat
state machine. **Failure line:** fail if the player keeps mining after hostile hits or if the process
opens a box despite the setting; the rally position and continued later work prove sheltering ran.
**Code:** `ShelterProcess.tickUnloading`, `requestShelter`. Difficulty: trivial.

### 43. `shelter-bed-radius-bound`

`shelterBedSearchRadius` must exclude a bed just outside the configured radius and fall back to
waiting at the rally box. **Failure line:** fail if the player travels to the outside bed or remains
in an unbounded approach; rally arrival and a bounded waiting state are independent evidence. **Code:**
`ShelterProcess.findBed`, `shelterBedSearchRadius`. Difficulty: moderate.

### 44. `shelter-bed-head-foot-dedupe`

A two-block bed must be treated as one candidate, with its head and foot normalized before pathing.
**Failure line:** fail if the player makes two approach attempts for one bed or clicks the wrong half;
the final sleeping state or bounded refusal count is observable world evidence. **Code:**
`ShelterProcess.findBed`, `headOf`, `footOf`. Difficulty: moderate.

### 45. `shelter-nether-bed-disabled`

Shelter must not attempt a bed in a dimension where beds explode, even when the bed-search setting is
enabled. **Failure line:** fail if the bed block changes or the player takes explosion damage; retreat
to the safe rally box and continued work provide positive evidence. **Code:** `ShelterProcess.bedsWorkHere`,
`tickSeekingBed`. Difficulty: needs-new-harness-capability (dimension staging).

### 46. `shelter-sleep-attempt-cap`

Repeated bed refusals must stop after `shelterMaxSleepAttempts` and transition to bounded waiting.
**Failure line:** fail if the player clicks beyond the configured number or loops between bed and box
past the retry budget; counted approach cycles and the eventual rally position are evidence. **Code:**
`ShelterProcess.tickEnteringBed`, `rejectBed`, `sleepAttempts`. Difficulty: moderate.

### 47. `shelter-threat-cleared-on-finish`

After sheltering finishes, the hits that caused it must be cleared so the same stale history does not
immediately interrupt the resumed job. **Failure line:** fail if the player retreats twice without a
new hostile hit; resumed mining progress proves the first handoff completed. **Code:**
`ShelterProcess.finish`; `ThreatBehavior.clearThreat`. Difficulty: moderate.

### 48. `shelter-retreat-stall-timeout`

An unreachable rally path must hit `shelterRetreatTimeoutTicks` and return control to the active job
instead of holding a temporary process indefinitely. **Failure line:** fail if the player remains at
the same feet position beyond the bound; pre-hit work and post-timeout work are independent evidence.
**Code:** `ShelterProcess.tickRetreating`, `notePathingProgress`, `giveUpOnRetreat`. Difficulty:
moderate.

### 49. `shelter-max-wait-timeout`

If a hostile continues reaching the player at the rally box, `shelterMaxWaitTicks` must eventually
hand control back rather than waiting forever. **Failure line:** fail if the active process never
resumes after the wait cap; a new control block placed after the cap proves the handback. **Code:**
`ShelterProcess.tickWaiting`, `shelterMaxWaitTicks`. Difficulty: moderate.

## Auto-eat, item saver, and inventory arbitration

The harness needs a small fixture helper for food level and, for the input assertions, a per-tick
snapshot of selected slot and use-key state. These are real behaviors, not hypothetical features.

### 50. `autoeat-selects-best-fit`

At a known hunger deficit, `EatBehavior.bestFoodSlot` must choose the smallest non-wasting food that
still makes progress. **Failure line:** fail if the larger food stack decreases while the smaller
food remains; hunger recovery and a resumed build prove that eating happened. **Code:**
`EatBehavior.bestFoodSlot`, `onTick`. Difficulty: needs-new-harness-capability.

### 51. `autoeat-exclude-list`

An excluded poisonous or emergency food must never be selected when it is the only tempting item on
the hotbar. **Failure line:** fail if the excluded stack decreases or its effect appears; a hunger
fixture with a safe alternate or bounded no-food state supplies independent evidence. **Code:**
`EatBehavior.isEdible`, `autoEatExclude`. Difficulty: needs-new-harness-capability.

### 52. `autoeat-hotbar-only`

Food in the main inventory must not be silently consumed because `bestFoodSlot` searches only the
hotbar. **Failure line:** fail if the main-inventory count decreases; the active path/build must
continue or stop for the documented no-hotbar-food reason. **Code:** `EatBehavior.bestFoodSlot`.
Difficulty: needs-new-harness-capability.

### 53. `autoeat-threshold-boundary`

Food must start at `autoEatFoodLevel` and not while the food level is one point above it. **Failure line:** fail if the stack decreases in the above-threshold phase, or remains unchanged at the exact
threshold while a controlled job is active; both phases need hunger snapshots. **Code:**
`EatBehavior.onTick`; `autoEatFoodLevel`. Difficulty: needs-new-harness-capability.

### 54. `autoeat-full-hunger-no-use`

Even with `autoEatFoodLevel=19`, full hunger must not start a vanilla use action. **Failure line:**
fail if the selected slot changes, use key is held, or food count drops; a moving path proves the
behavior was active rather than the world simply idling. **Code:** `EatBehavior.onTick`,
`shouldRun`. Difficulty: needs-new-harness-capability.

### 55. `autoeat-existing-use-waits`

If another item is already being used, auto-eat must wait rather than cancel it or switch slots.
**Failure line:** fail if the original use ends early or the selected slot changes; the item-use
completion and unchanged original stack are independent evidence. **Code:** `EatBehavior.onTick`,
`ctx.player().isUsingItem()`. Difficulty: needs-new-harness-capability.

### 56. `autoeat-mining-waits`

Auto-eat must defer while left-click mining is actively forced, then eat after the break gap without
losing the target block. **Failure line:** fail if the food starts while attack is held or the target
is left half-mined forever; block destruction followed by hunger recovery proves both phases. **Code:**
`EatBehavior.onTick`, `BlockBreakHelper.tick`. Difficulty: needs-new-harness-capability.

### 57. `autoeat-releases-after-meal`

The use key and selected food slot must be released immediately after the server completes the meal,
without a stray right-click on the next block. **Failure line:** fail if the next block changes or
the food use restarts after `isUsingItem` becomes false; hunger increase plus block-state stability
are independent evidence. **Code:** `EatBehavior.continueEating`, `stopEating`. Difficulty:
needs-new-harness-capability.

### 58. `autoeat-secondary-does-not-control`

Only the primary Baritone instance may auto-eat when two clients/instances share the world controls.
**Failure line:** fail if a secondary instance changes the hotbar or food count while the primary is
inactive; a primary-controlled path and per-instance inventory snapshots provide evidence. **Code:**
`EatBehavior.shouldRun`, `BaritoneAPI.getProvider().getPrimaryBaritone()`. Difficulty: needs-new-
harness-capability.

### 59. `autoeat-world-exit-releases-input`

Leaving the world during a meal must clear the held use key and reset eating state. **Failure line:**
fail if a reconnect sees the use key forced or the old slot locked; a second-world control action
proves state was reset. **Code:** `EatBehavior.onTick`, `stopEating`. Difficulty: needs-new-
harness-capability.

### 60. `itemsaver-all-hotbar-spent`

When every hotbar tool is spent, item saver must not fall back to slot zero and break that spent
tool. **Failure line:** fail if the target block changes or slot zero durability decreases; the active
mine’s unchanged target and bounded stop are positive evidence. **Code:** `ToolSet.getBestSlot`,
`BlockBreakHelper`, `InventoryBehavior.bestToolAgainst`. Difficulty: moderate.

### 61. `itemsaver-traverse-obstacle`

The movement-traverse obstruction branch must respect item saver rather than swinging the currently
held item. **Failure line:** fail if the obstruction breaks with the protected tool or if the player
gets stuck in an attack loop; a detour or bounded movement completion proves the branch was entered.
**Code:** `MovementTraverse`, `BlockBreakHelper.tick`. Difficulty: moderate.

### 62. `itemsaver-pillar-break`

The movement-pillar break-above branch must use the same central spent-tool guard as ordinary mining.
**Failure line:** fail if the overhead block is broken with a tool at/below threshold; a successful
pillar movement with the block intact or a bounded stop is independent evidence. **Code:**
`MovementPillar`, `BlockBreakHelper.tick`. Difficulty: moderate.

### 63. `inventory-stationary-gate`

With `inventoryMoveOnlyIfStationary=true`, automatic hotbar swaps must wait until the player stops,
while the same job swaps promptly when it is false. **Failure line:** fail if the hotbar changes
mid-traverse in the gated phase, or never changes after the player reaches a standstill; selected
slot and completed movement are direct evidence. **Code:** `InventoryBehavior.requestSwapWithHotBar`,
`InventoryPauserProcess`. Difficulty: moderate.

### 64. `inventory-main-throwaway-swap`

An acceptable throwaway only in the main inventory must be moved to a temporary hotbar slot before
placement. **Failure line:** fail if the path tries to place with no throwaway or overwrites a
disallowed hotbar slot; the placed scaffold block and final hotbar contents prove the swap. **Code:**
`InventoryBehavior.throwaway`, `requestSwapWithHotBar`. Difficulty: moderate.

### 65. `inventory-main-tool-swap`

The best tool found outside the hotbar must be swapped into the mining slot when inventory control
is enabled. **Failure line:** fail if stone is mined bare-handed or the wrong hotbar tool loses
durability; the target break and slot change provide independent evidence. **Code:**
`InventoryBehavior.bestToolAgainst`, `requestSwapWithHotBar`. Difficulty: moderate.

## Builder state, substitutions, and placement

### 66. `builder-ignore-existing`

`buildIgnoreExisting=true` must leave an existing non-air block untouched while placing missing
positions around it. **Failure line:** fail if the existing block is broken, or if the surrounding
control targets remain air; both world-state checks prove the builder acted. **Code:**
`BuilderProcess.correct`, `fullRecalc`; `Settings.buildIgnoreExisting`. Difficulty: trivial.

### 67. `builder-ignore-blocks-air`

When a schematic asks for air, a block in `buildIgnoreBlocks` must be accepted and left in place.
**Failure line:** fail if that ignored block is mined, while a neighboring desired block must still
be placed to prove the build was not skipped wholesale. **Code:** `BuilderProcess.correct`,
`buildIgnoreBlocks`. Difficulty: trivial.

### 68. `builder-skip-current`

`buildSkipBlocks` must protect a skipped current block even when the schematic asks for air, while
other schematic positions remain active. **Failure line:** fail if the skipped block changes or if
the neighboring target is not built; the two state checks separate policy from a total pause. **Code:**
`BuilderProcess.shouldSkip`, `correct`. Difficulty: trivial.

### 69. `builder-ok-if-air`

Putting a desired block in `okIfAir` must allow an air position to count as complete while another
required block is placed. **Failure line:** fail if the bot attempts to place the `okIfAir` block,
or if the control block remains air; final world states are direct evidence. **Code:**
`BuilderProcess.correct`, `okIfAir`. Difficulty: trivial.

### 70. `builder-valid-substitute`

`buildValidSubstitutes` must accept a pre-existing substitute at a desired position without breaking
it, while still building a separate missing position. **Failure line:** fail if the accepted block is
replaced or the separate target is untouched. **Code:** `BuilderProcess.correct`,
`buildValidSubstitutes`. Difficulty: trivial.

### 71. `builder-substitute-fallback-order`

`buildSubstitutes` must try the configured alternatives in order and use the first one that is
actually placeable and available. **Failure line:** fail if it pauses on the unavailable first item
or places a later alternative while the first is available; the final block state and player
inventory prove the chosen path. **Code:** `SubstituteSchematic`, `BuilderProcess.placeAt`.
Difficulty: moderate.

### 72. `builder-ignore-properties`

Ignoring a named block property must accept that property difference but continue checking all other
properties. **Failure line:** fail if a state with a non-ignored property mismatch is accepted, or
if the ignored-only mismatch is needlessly rebuilt; paired target states make both cases observable.
**Code:** `BuilderProcess.sameBlockstate`, `buildIgnoreProperties`. Difficulty: moderate.

### 73. `builder-ignore-direction`

`buildIgnoreDirection=true` must accept a wrong facing without invoking an orientation stand goal,
while the same fixture with it false corrects the facing. **Failure line:** fail if the setting-on
phase walks to an orientation stand or alters the block, or if the setting-off phase leaves the wrong
state; player position and state are independent evidence. **Code:** `BuilderProcess.correct`,
`orientedPlacementGoal`, `buildIgnoreDirection`. Difficulty: moderate.

### 74. `builder-orient-distance-one`

The minimum supported orientation stand distance of one must place a horizontal directional block
without aim jitter producing a neighboring facing. **Failure line:** fail if the target state is a
neighboring direction or if the player remains in an orientation loop; the final state proves a
successful click. **Code:** `GoalPlaceOriented.isInGoal`, `orientedPlacementGoal`.
Difficulty: moderate.

### 75. `builder-orient-distance-four`

The maximum supported stand distance of four must remain reachable and produce the requested state.
**Failure line:** fail if the target is reachable only at an unusable distance or remains air after
the player reaches the stand region; target state and feet position provide evidence. **Code:**
`GoalPlaceOriented`, `searchForPlacables`; `buildOrientStandDistance`. Difficulty: moderate.

### 76. `builder-orient-timeout-independent-target`

When one orientation-sensitive position cannot be reached before `buildOrientTimeoutTicks`, other
independent positions must continue building. **Failure line:** fail if the timeout poisons the whole
schematic, or if the impossible target is silently treated as complete; completed neighboring blocks
and the remaining target state distinguish both outcomes. **Code:** `BuilderProcess.orientedPlacementGoal`,
`isOrientationSkipped`, `fullRecalc`. Difficulty: moderate.

### 77. `builder-churn-cooldown`

Alternating break/place at one position must trigger `builderChurnDetection` and leave that position
alone for `builderChurnCooldownTicks` while unrelated targets proceed. **Failure line:** fail if the
same position alternates past the threshold or if unrelated blocks stop changing; action counts plus
neighboring completed blocks are independent evidence. **Code:** `BuilderProcess` churn tracking,
`searchForPlacables`; settings `builderChurn*`. Difficulty: needs-new-harness-capability (fixture
must create a controlled competing path/build position).

### 78. `builder-churn-window`

Break/place alternations separated beyond `builderChurnWindowTicks` must not be falsely classified as
one loop. **Failure line:** fail if a deliberately slow two-phase correction is blacklisted; the
later target state and absence of a cooldown are observable. **Code:** `BuilderProcess` churn
window bookkeeping. Difficulty: needs-new-harness-capability.

### 79. `builder-reroute-commit-changing-targets`

When the selected placement target changes every tick, `builderMaxReroutes` must commit to a path for
`builderRerouteCommitTicks` rather than recalculating forever. **Failure line:** fail if repeated
identical calculations consume the budget while the player never moves; movement toward a target and
eventual placement are positive evidence. **Code:** `BuilderProcess.commitAwarePathingCommand`,
`rerouteCommitTicks`. Difficulty: moderate.

### 80. `builder-reroute-zero-disabled`

Setting `builderMaxReroutes=0` must preserve continuous revalidation so a newly unblocked target is
noticed immediately. **Failure line:** fail if an obstacle is removed and the builder keeps the old
route through the commit window; the obstacle removal and subsequent target placement prove a live
replan was possible. **Code:** `BuilderProcess.commitAwarePathingCommand`. Difficulty: needs-new-
harness-capability (change terrain during a running scenario).

### 81. `builder-layers-bottom-up`

`buildInLayers=true`, `layerOrder=false` must finish every lower layer before placing a higher-layer
control block. **Failure line:** fail if any higher target appears while a lower target is still air;
the final complete structure proves the builder did not simply stop. **Code:** `BuilderProcess.recalc`,
`layer`, `stopAtHeight`. Difficulty: moderate.

### 82. `builder-layers-top-down`

With `layerOrder=true`, the same layered schematic must proceed from the top layer downward. **Failure line:** fail if the first placed layer is not the top layer, while a final complete structure proves
the order was exercised. **Code:** `BuilderProcess.recalc`, `layerOrder`. Difficulty: moderate.

### 83. `builder-layer-height-two`

`layerHeight=2` must group exactly two Y-levels per layer instead of advancing after every level.
**Failure line:** fail if a third-level control block appears before the first two-level group is
complete; final completion distinguishes ordering from a pause. **Code:** `BuilderProcess.recalc`,
`layerHeight`. Difficulty: moderate.

### 84. `builder-start-at-layer`

`startAtLayer=1` must leave lower-layer targets untouched while building the selected higher layer.
**Failure line:** fail if a lower control block changes or the selected layer remains air; both block
states make the start offset observable. **Code:** `BuilderProcess.onLostControl`/initial layer setup,
`recalc`; `startAtLayer`. Difficulty: trivial.

### 85. `builder-skip-failed-layer`

`skipFailedLayers=true` must skip an unsatisfiable layer and continue to a later independent layer.
**Failure line:** fail if the later layer remains air after its supports are valid, or if the failed
layer is falsely reported complete; staged layer states provide the distinction. **Code:**
`BuilderProcess.onTick`, `skipFailedLayers`, layer transition. Difficulty: moderate.

### 86. `builder-repeat-count`

`buildRepeat` with a finite `buildRepeatCount` must build exactly the requested number of translated
copies and then stop. **Failure line:** fail if a third copy appears or the second is missing; each
copy’s world state is an independent count. **Code:** `BuilderProcess`, `RepeatSchematic`/build
repeat transition. Difficulty: moderate.

### 87. `builder-repeat-sneaky`

`buildRepeatSneaky=true` must reuse the original schematic coordinates for each repeat while the
non-sneaky setting translates state-aware schematic notifications. **Failure line:** fail if one mode
leaves a copy with the wrong orientation or misses a translated block; compare two small copies in
world state. **Code:** `BuilderProcess`, repeat schematic notification path. Difficulty: needs-new-
harness-capability.

### 88. `builder-schematic-transform`

Each 90-degree rotation, mirror, and axis-orientation setting must transform a directional fixture
consistently before placement. **Failure line:** fail if the transformed block state or coordinates
match the untransformed fixture; paired target states and final positions provide evidence. **Code:**
`BuilderProcess.build`, schematic transform wrappers. Difficulty: moderate.

### 89. `builder-map-art-topmost`

`mapArtMode=true` must care about the topmost block in each column and ignore lower decorative
positions. **Failure line:** fail if a lower column block is changed or the top block remains wrong;
the two Y-level states distinguish the mode. **Code:** `BuilderProcess` map-art calculation. Difficulty:
moderate.

### 90. `builder-ok-if-water`

`okIfWater=true` must accept current water where the schematic asks for a solid, while still correcting
an adjacent dry mismatch. **Failure line:** fail if the water is replaced in the setting-on phase or
the adjacent solid remains wrong; both states are directly observable. **Code:** `BuilderProcess.correct`,
`okIfWater`. Difficulty: trivial.

### 91. `builder-disallow-break-anyway`

`allowBreak=false` plus `allowBreakAnyway` must permit only the explicitly listed obstructing block
to be broken. **Failure line:** fail if an unlisted obstruction changes or the listed obstruction
remains while its target is reachable; world states and completed neighbor prove activity. **Code:**
`BuilderProcess.correct`, `CalculationContext.allowBreakAnyway`. Difficulty: moderate.

### 92. `builder-next-horizon-protection`

The builder/backfill coordination must not refill a path hole that is eleven movements ahead once it
has left the documented ten-movement protection horizon, while still protecting the ten immediately
upcoming movements. **Failure line:** fail if the ten-step hole is refilled before traversal, or if
the eleven-step hole never gets restored after traversal; path movement and final blocks provide
independent evidence. **Code:** `BackfillProcess.partOfCurrentMovement`, `BuilderProcess.searchForPlacables`.
Difficulty: needs-new-harness-capability (a deterministic long path and backfill event stream).

### 93. `builder-incorrect-size-cap`

`incorrectSize` must bound the tracked incorrect-position set without making a completed nearby
portion disappear from the build. **Failure line:** fail if the scenario exceeds the cap and nearby
control positions remain air despite repeated reachable passes; completed distant/nearby counts are
the evidence. **Code:** `BuilderProcess.fullRecalc`, `incorrectSize`. Difficulty: moderate.

## Backfill and process handoff

### 94. `backfill-parkour-incompatible`

`backfill=true` with `allowParkour=true` must disable backfill and leave the pathing job’s holes
untouched rather than placing blocks anyway. **Failure line:** fail if a hole is filled or if the
backfill process holds control; continued parkour movement proves the main job remained active. **Code:**
`BackfillProcess.isActive`. Difficulty: trivial.

### 95. `backfill-unsafe-cancel`

When `isSafeToCancel=false`, backfill must request a pause without placing or clearing the active
movement input. **Failure line:** fail if a block changes during the unsafe tick or the path’s next
movement is lost; a later safe placement and final route completion prove the deferral was temporary.
**Code:** `BackfillProcess.onTick`, `tickPlacement`. Difficulty: needs-new-harness-capability.

### 96. `backfill-outside-schematic-only`

Backfill may restore a hole outside the active schematic but must never claim a position owned by the
builder, even when both positions are air. **Failure line:** fail if the in-schematic target gets
rubble or the outside hole remains after a safe opportunity; paired world states provide evidence.
**Code:** `BackfillProcess.toFillIn`, `BuilderProcess.managesPosition`. Difficulty: moderate.

### 97. `backfill-no-rubble-no-placement`

If no configured `backfillBlocks` item is available, backfill must defer while the pathing process
continues rather than placing build material or an arbitrary block. **Failure line:** fail if the
hole changes or a build-material count decreases; a completed path segment proves backfill was active.
**Code:** `BackfillProcess.tickPlacement`; `InventoryBehavior.setThrowawayRestriction`. Difficulty:
moderate.

### 98. `backfill-restriction-clears`

The one-tick throwaway restriction must be cleared after backfill so a subsequent builder placement
can use its ordinary material inventory. **Failure line:** fail if the later build target stays air
with a valid build block available, or if the backfill block is consumed for the build; two distinct
world states identify leakage. **Code:** `BackfillProcess.onTick` `finally`; `InventoryBehavior.selectThrowawayForLocation`.
Difficulty: moderate.

### 99. `backfill-clears-on-control-loss`

When a higher-priority temporary process interrupts backfill, queued replacement positions must be
cleared rather than applied after the original path is gone. **Failure line:** fail if an old hole is
filled after the player has moved to shelter or restock, while the new process visibly completes its
own work. **Code:** `BackfillProcess.onLostControl`, `ProcessScheduler.executeProcesses`. Difficulty:
needs-new-harness-capability.

### 100. `backfill-drops-solid-entry`

An entry recorded for a broken block must disappear from the queue as soon as another process makes
that position solid. **Failure line:** fail if backfill attempts to replace the newly solid block or
keeps holding control; a second queued hole filled successfully proves the process did not simply
stop. **Code:** `BackfillProcess.isActive`, `blocksToReplace` pruning. Difficulty: moderate.

### 101. `backfill-unloaded-entry-drops`

An unloaded-chunk hole must be removed from active backfill consideration rather than causing a
cross-chunk path loop. **Failure line:** fail if the player is pulled toward the unloaded coordinate;
a loaded nearby hole and completed path provide positive evidence. **Code:** `BackfillProcess.isActive`,
`EmptyLevelChunk` guard. Difficulty: needs-new-harness-capability (controlled chunk unload).

### 102. `handoff-restock-preserves-schematic`

When restock temporarily wins control, the builder’s active schematic and progress must survive the
handoff. **Failure line:** fail if the player returns with the fetched item but the already-correct
blocks are rebuilt or the remaining target is forgotten; before/after block-state comparison and
completion prove preservation. **Code:** `RestockProcess.isTemporary`, `BuilderProcess.onLostControl`,
`ProcessScheduler`. Difficulty: moderate.

### 103. `handoff-backfill-does-not-cancel-builder`

Backfill’s temporary control must not call the builder’s `onLostControl` and erase an in-progress
schematic. **Failure line:** fail if a queued hole is filled but the builder does not place its next
target afterward; backfilled outside state and later target state are independent evidence. **Code:**
`BackfillProcess.isTemporary`, `ProcessScheduler`, `BuilderProcess.onLostControl`. Difficulty:
moderate.

### 104. `handoff-cancel-releases-input`

Cancelling a temporary process during a forced placement or deposit must release its input overrides
before the next process takes control. **Failure line:** fail if the next job breaks/places one extra
block because an old click remains forced; the intended next-job state and input snapshot prove the
handoff. **Code:** `BackfillProcess.onLostControl`, `RestockProcess.clearOpeningInput`,
`InputOverrideHandler`. Difficulty: needs-new-harness-capability.

## Mining and pickup

### 105. `mine-existing-quantity`

If the player already holds the requested quantity, `MineProcess` must cancel without breaking an
extra target. **Failure line:** fail if a target block changes or a drop appears; a controlled goto
to the mine area before the quantity check proves the process was started. **Code:** `MineProcess.onTick`,
`desiredQuantity`, `filter`. Difficulty: trivial.

### 106. `mine-dropped-item-scan-off`

With `mineScanDroppedItems=false`, a matching dropped item must not become a mining goal. **Failure line:** fail if the player diverts to the item while target ore remains; a target block mined in the
same fixture proves the mine continued on blocks. **Code:** `MineProcess.droppedItemsScan`,
`updateGoal`; setting `mineScanDroppedItems`. Difficulty: moderate.

### 107. `mine-goal-update-zero`

`mineGoalUpdateInterval=0` must disable periodic rescans without preventing completion of the targets
known at start. **Failure line:** fail if known targets remain after the last one is broken, or if
the process loops because it cannot rescan; target block states and inventory drops are evidence.
**Code:** `MineProcess.onTick`, `rescan`. Difficulty: moderate.

### 108. `mine-goal-invalidation-on`

With `cancelOnGoalInvalidation=true`, mining a goal must cancel the now-invalid path instead of
walking through the old target location. **Failure line:** fail if the player continues into a staged
hazard after the ore breaks; the ore drop and a safe alternate target prove the cancellation path.
**Code:** `MineProcess`, `PathingBehavior`; `cancelOnGoalInvalidation`. Difficulty: moderate.

### 109. `mine-goal-invalidation-off`

With the setting off, the current path may finish its movement after the target disappears, without
losing the later target. **Failure line:** fail if the process cancels all mining or leaves the later
target untouched; later target break and path position are independent evidence. **Code:**
`MineProcess` goal update and path invalidation handling. Difficulty: moderate.

### 110. `mine-blacklist-unreachable-then-reachable`

With `blacklistClosestOnFailure=true`, an unreachable nearest ore must be blacklisted so a reachable
farther ore is mined. **Failure line:** fail if the player repeatedly targets the sealed ore or stops
before the reachable one; the farther block’s broken state proves fallback. **Code:**
`MineProcess.onTick`, `blacklist`, `knownOreLocations`. Difficulty: moderate.

### 111. `mine-allow-break-anyway`

When `allowBreak=false`, a target listed in `allowBreakAnyway` may be mined while an unlisted target
remains intact. **Failure line:** fail if the unlisted block changes or the allowed ore stays in
place; both target states and the obtained drop prove the filter. **Code:** `MineProcess.prune`,
`CalculationContext.allowBreakAnyway`. Difficulty: trivial.

### 112. `mine-force-internal-adjacent`

`forceInternalMining=true` must choose a goal that can mine adjacent vertically aligned targets
efficiently without skipping one of their drops. **Failure line:** fail if one target remains or the
quantity count is short after both blocks change; two target states and player inventory are independent
evidence. **Code:** `MineProcess.coalesce`, `internalMiningGoal`. Difficulty: moderate.

### 113. `mine-internal-air-exception`

Toggling `internalMiningAirException` must change only the internal goal coalescing around air, not
the set of requested blocks. **Failure line:** fail if either phase mines a non-filter block or
misses a requested target; paired target states and drop counts expose the distinction. **Code:**
`MineProcess.internalMiningGoal`, `coalesce`. Difficulty: moderate.

### 114. `mine-y-range`

`minYLevelWhileMining` and `maxYLevelWhileMining` must prune matching targets outside the configured
range while mining those inside it. **Failure line:** fail if an out-of-range block changes or the
in-range block remains; target states give a direct verdict. **Code:** `MineProcess.prune`,
`filterFilter`; Y settings. Difficulty: trivial.

### 115. `mine-location-cap`

`mineMaxOreLocationsCount` must cap candidate tracking without causing the miner to abandon later
targets after the first batch. **Failure line:** fail if the cap-sized first batch completes but a
known later target remains indefinitely; final target states and quantity prove batch progression.
**Code:** `MineProcess.rescan`, `prune`, `mineMaxOreLocationsCount`. Difficulty: moderate.

### 116. `mine-explore-off-bounded`

With `exploreForBlocks=false` and no known matching blocks, mining must cancel cleanly rather than
walking an endless exploration goal. **Failure line:** fail if the player moves away from the staged
start after the bounded cancellation window; a visible no-target arena and stable process state are
the evidence. **Code:** `MineProcess.updateGoal`, `rescan`. Difficulty: trivial.

### 117. `mine-legit-diagonal-off`

`legitMineIncludeDiagonals=false` must not reveal a diagonally hidden ore through an exposed neighbor.
**Failure line:** fail if the hidden ore changes before it becomes physically visible; an exposed ore
mined first proves legit mining was active. **Code:** `MineProcess.addNearby`, `legitMineIncludeDiagonals`.
Difficulty: moderate.

### 118. `mine-legit-diagonal-on`

With the diagonal option on, an adjacent exposed vein may become eligible after the legal visibility
condition is met. **Failure line:** fail if the option-on case behaves identically to the off case
after the exposing block is mined; first and second target states distinguish discovery from x-ray.
**Code:** `MineProcess.addNearby`, `legitMineIncludeDiagonals`. Difficulty: moderate.

### 119. `mine-loiter-zero`

`mineDropLoiterDurationMSThanksLouca=0` must advance without waiting for a delayed drop while still
counting a later matching item if pickup scanning finds it. **Failure line:** fail if the process
idles for the loiter interval or abandons the quantity after the drop arrives; target break and final
inventory count provide evidence. **Code:** `MineProcess.updateLoucaSystem`, `droppedItemsScan`.
Difficulty: moderate.

### 120. `mine-prefer-silk-touch`

`preferSilkTouch=true` must select a valid silk-touch tool when the target’s drop behavior makes it
preferable, without choosing it when the setting is false. **Failure line:** fail if the wrong tool
loses durability or the resulting item form is wrong; target state and player inventory prove both
selection and drop. **Code:** `ToolSet.calculateSpeedVsBlock`, `MovementHelper.switchToBestToolFor`.
Difficulty: moderate.

### 121. `mine-axe-tool-path`

An axe-required target must use an axe from inventory rather than the pickaxe-only replacement path.
**Failure line:** fail if the log remains or the pickaxe loses durability while an axe is available;
the broken log and axe durability are independent evidence. **Code:** `ToolSet`, `InventoryBehavior.bestToolAgainst`,
`MineProcess`. Difficulty: moderate, with a version-shim check for the tool interface.

### 122. `pickup-builder-drop-only`

With `pickupBlocks=true`, a builder must collect its own newly created rubble while leaving an older
unrelated item entity alone. **Failure line:** fail if the unrelated item enters player inventory or
the builder drop remains after the player passes it; entity UUIDs and player inventory are independent
evidence. **Code:** `PickupBlocksProcess`, `BuilderProcess` drop ownership. Difficulty: moderate.

### 123. `pickup-dropped-stack-merge`

A picked-up matching drop must merge into an existing player stack without displacing protected
items. **Failure line:** fail if the matching count does not increase or a protected inventory item
is lost; player inventory snapshots before and after are direct evidence. **Code:**
`PickupBlocksProcess`, vanilla item pickup handling. Difficulty: moderate.

## Pathing and setting interactions

### 124. `path-source-fluid-placement`

`allowPlaceInFluidsSource=false` must route around a source-fluid placement that would otherwise be
legal, while true may use it. **Failure line:** fail if the off case changes a source fluid or takes
the same blocked route as the on case; route position and block state are evidence. **Code:**
`CalculationContext`, `MovementHelper.canPlaceAgainst`; fluid-placement settings. Difficulty: moderate.

### 125. `path-flow-fluid-placement`

The flow-fluid setting must be checked separately from the source-fluid setting on a fixture with
one of each. **Failure line:** fail if disabling flow placement changes the source case or if the
flow case is used while disabled; the two route choices and fluid states distinguish them. **Code:**
`CalculationContext`, movement placement cost/validity. Difficulty: moderate.

### 126. `path-strict-liquid-check`

`strictLiquidCheck=true` must reject a path whose collision/placement assumptions depend on a liquid
edge, while false permits the ordinary route. **Failure line:** fail if both modes traverse the same
unsafe liquid edge or if strict mode cannot reach a safe goal despite a staged detour. **Code:**
`CalculationContext`, liquid movement helpers. Difficulty: moderate.

### 127. `path-assume-walk-water`

`assumeWalkOnWater=true` must allow a still-water route to be considered walkable, while false chooses
the dry detour. **Failure line:** fail if the setting-on route never touches the water or the setting-
off route enters it; player path coordinates and final position are direct evidence. **Code:**
`CalculationContext`, water movement cost. Difficulty: moderate.

### 128. `path-assume-walk-lava`

`assumeWalkOnLava` must alter only the planning assumption for a staged lava bridge, with the safe
detour still preferred when the setting is false. **Failure line:** fail if the player enters lava in
the off case or cannot reach the goal in the on case; final health and path coordinates provide
independent evidence. **Code:** `CalculationContext`, lava movement checks. Difficulty: needs-new-
harness-capability (safe lava-damage fixture).

### 129. `path-water-bucket-fall`

`allowWaterBucketFall=true` must allow a fall route within the bucket height, while false takes the
long detour or stops safely. **Failure line:** fail if the off case falls the configured height or
the on case refuses a reachable bucket landing; player Y trajectory and final position are evidence.
**Code:** movement fall costs, `allowWaterBucketFall`, `maxFallHeightBucket`. Difficulty: moderate.

### 130. `path-max-fall-no-water-bound`

Changing `maxFallHeightNoWater` must change the maximum safe solid landing considered by the pathfinder.
**Failure line:** fail if the low-limit route takes the excessive drop or the high-limit route ignores
the staged reachable landing; path coordinates and health prove the choice. **Code:** fall movement
costs; `maxFallHeightNoWater`. Difficulty: moderate.

### 131. `path-max-fall-with-bucket-bound`

`maxFallHeightBucket` must not make a fall beyond the configured bucket range appear safe. **Failure line:** fail if the player takes the over-limit fall or if a within-limit landing is rejected in the
control case; final Y/health and route choice provide evidence. **Code:** fall movement; setting
`maxFallHeightBucket`. Difficulty: moderate.

### 132. `path-diagonal-ascend`

`allowDiagonalAscend=true` must use a legal diagonal step when it is shorter, while false takes the
orthogonal staircase. **Failure line:** fail if the enabled route never uses the diagonal or the
disabled route crosses the blocked diagonal; path positions and final goal are evidence. **Code:**
`MovementDiagonal`, `allowDiagonalAscend`. Difficulty: moderate.

### 133. `path-diagonal-descend`

`allowDiagonalDescend=true` must accept a safe diagonal descent and false must choose the longer safe
route. **Failure line:** fail if the off case takes the diagonal hazard or the on case cannot reach
the goal; intermediate feet positions are independent evidence. **Code:** diagonal movement costs,
`allowDiagonalDescend`. Difficulty: moderate.

### 134. `path-downward-off-shaft`

`allowDownward=false` must avoid mining directly beneath the player and choose a staircase route when
one exists. **Failure line:** fail if the off case breaks the block below the feet or remains at the
shaft entrance; a completed side route proves the setting was honored. **Code:** `CalculationContext`,
`allowDownward`; downward movement costs. Difficulty: moderate.

### 135. `path-parkour-on`

`allowParkour=true` must use a solvable jump gap that is excluded when parkour is false. **Failure line:** fail if the on case takes the long detour despite a safe jump or the off case enters the
gap; path positions and final goal are evidence. **Code:** `MovementParkour`, `allowParkour`.
Difficulty: moderate.

### 136. `path-parkour-place`

`allowParkourPlace=true` must permit a legal placed-block parkour route while false chooses a route
that needs no placement. **Failure line:** fail if the placed bridge appears in the off case or the
on case cannot reach the goal; world block state and player position are independent evidence. **Code:**
`MovementParkour`, `allowParkourPlace`, `InventoryBehavior.selectThrowawayForLocation`. Difficulty:
moderate.

### 137. `path-bottom-slab-off`

`allowWalkOnBottomSlab=false` must reject a bottom-slab shortcut while true may traverse it. **Failure line:** fail if the off case enters the slab route or if the on case takes an unrelated detour; feet
positions and final goal prove the branch. **Code:** slab movement costs; `allowWalkOnBottomSlab`.
Difficulty: moderate.

### 138. `path-vines-setting`

`allowVines=true` must permit a vine ascent and false must route around it or stop without breaking
the vine. **Failure line:** fail if the disabled case climbs or breaks the vine, or if the enabled
case cannot reach the upper goal; vine state and player Y are evidence. **Code:** vine movement;
`allowVines`. Difficulty: moderate.

### 139. `path-avoidance-mob-radius`

With avoidance enabled, changing `mobAvoidanceRadius` must alter the chosen route around a staged
hostile while the direct route remains available. **Failure line:** fail if both radii take identical
dangerous coordinates or if the larger radius cannot reach the goal; path trace and final position
are independent evidence. **Code:** avoidance map, `mobAvoidanceRadius`, `mobAvoidanceCoefficient`.
Difficulty: needs-new-harness-capability (stable entity/path trace).

### 140. `path-mob-spawner-avoidance`

`mobSpawnerAvoidanceRadius` and coefficient must prefer a detour around a staged spawner without
changing behavior when avoidance is off. **Failure line:** fail if the avoidance-on path crosses the
spawner radius or if the off control cannot use the direct corridor; path positions are the evidence.
**Code:** avoidance map, `mobSpawnerAvoidanceRadius`, `mobSpawnerAvoidanceCoefficient`. Difficulty:
needs-new-harness-capability.

### 141. `path-overshoot-traverse-off`

`overshootTraverse=false` must not accept a one-block-past traverse as the goal, while true may
accept it when the overshoot is safe. **Failure line:** fail if the off case stops past the target or
the on case needlessly backtracks; final feet position and route length are direct evidence. **Code:**
`MovementTraverse`, `overshootTraverse`. Difficulty: moderate.

### 142. `path-sprint-ascends-off`

`sprintAscends=false` must plan the slower non-sprint ascent and not rely on sprint input on a narrow
stair. **Failure line:** fail if sprint state is forced in the off case or if the route fails only
because the planner assumed sprint; input snapshot and final position provide evidence. **Code:**
`MovementAscend`, `sprintAscends`. Difficulty: needs-new-harness-capability (input trace).

### 143. `path-sprint-in-water-off`

`sprintInWater=false` must remove sprint assumptions from water traversal while preserving a reachable
dry detour. **Failure line:** fail if sprint is held in water in the off case or if the planner chooses
an impossible water shortcut; input/path trace is evidence. **Code:** water movement helpers,
`sprintInWater`. Difficulty: needs-new-harness-capability.

### 144. `path-enter-portal`

`enterPortal=true` must walk into a portal block instead of stopping one block short, while false
must stop before entering. **Failure line:** fail if the true case never changes dimension or the
false case enters; dimension and player position provide definitive evidence. **Code:** portal goal
handling, `enterPortal`. Difficulty: needs-new-harness-capability (dimension transition and reset).

### 145. `path-cached-only-boundary`

`pathThroughCachedOnly=true` must refuse an unloaded-region route and accept a fully cached equivalent
route. **Failure line:** fail if the bot enters an unloaded chunk in the cached-only case or fails
the cached control; chunk state and final position are independent evidence. **Code:** path calculation
chunk loading, `pathThroughCachedOnly`. Difficulty: needs-new-harness-capability.

### 146. `path-load-boundary-cutoff`

`cutoffAtLoadBoundary=true` must stop a path at the known loaded boundary and use a staged route that
does not cross it when one exists. **Failure line:** fail if the player crosses into the boundary
chunk or fails the safe route; chunk coordinates and final position prove the cutoff. **Code:** path
calculation boundary handling, `cutoffAtLoadBoundary`. Difficulty: needs-new-harness-capability.

### 147. `path-block-reach-distance`

Reducing `blockReachDistance` below the box’s click distance must make container opening fail safely,
while the normal reach control opens and completes a build. **Failure line:** fail if the short-reach
case transfers material or if it loops trying to open; player position, inventory, and control build
prove the interaction was attempted. **Code:** `RotationUtils.reachable`, `ContainerInteractionBehavior`;
`blockReachDistance`. Difficulty: moderate.

### 148. `path-right-click-arrival-off`

`rightClickContainerOnArrival=false` must stop the builder from opening an incidental container at its
goal, while true performs the arrival interaction. **Failure line:** fail if the off case changes a
container-adjacent block or opens the menu; arrival position plus subsequent build state prove the
goal was reached. **Code:** `BuilderProcess`, `rightClickContainerOnArrival`. Difficulty: moderate.

## BLOCKED-ON-T-01: proposals that require a trustworthy container oracle

These are deliberately not mixed with the runnable list. `ScenarioInventory.countContainer` cannot
reliably see inside a container and returns `-1` in the affected setup.
Do not implement these until T-01 is fixed and a positive/negative container-read control passes.

### B1. `container-exact-restock-source`

After a restock trip, assert the exact remaining count in the source box, not only the player’s gain.
**Failure line:** fail if the source remainder differs from the requested transfer while the player
inventory happens to be correct. **Code:** `ScenarioInventory.countContainer`; `RestockProcess.tickTransferring`.
Difficulty: BLOCKED-ON-T-01.

### B2. `container-exact-deposit-destination`

Assert that every deposited junk stack is in the intended destination box and not dropped or sent to
an adjacent box. **Failure line:** fail if the destination count is wrong despite the player returning
to work. **Code:** `ScenarioInventory.countContainer`; `RestockProcess.tickDepositing`. Difficulty:
BLOCKED-ON-T-01.

### B3. `container-partial-stack-remainder`

Verify that a partial stack is merged into the correct existing destination stack rather than creating
an incorrect number of slots. **Failure line:** fail if the destination’s per-item total or slot
packing is wrong. **Code:** `ScenarioInventory.countContainer`; `ContainerInteractionBehavior.quickMove`.
Difficulty: BLOCKED-ON-T-01.

### B4. `container-capacity-estimate-packing`

Compare `IRestockBox.estimatedFreeSlots()` with a box containing partial stacks of the same item and
ensure capacity-aware selection reflects packed reality. **Failure line:** fail if the wrong box is
selected solely because the estimate counts stacks incorrectly. **Code:** `RestockBox.estimatedFreeSlots`,
`RestockProcess.roomFor`. Difficulty: BLOCKED-ON-T-01.

### B5. `container-live-index-refresh-count`

Change a box’s contents between indexing and `#indexboxes all`, then assert the stored item count and
timestamp-derived estimate both change. **Failure line:** fail if the old count survives a successful
live refresh. **Code:** `RestockBoxCollection.index`, `ScenarioInventory.countContainer`. Difficulty:
BLOCKED-ON-T-01.

### B6. `container-wrong-menu-no-transfer`

Open box A, target box B, and assert no item from A is counted as a transfer from B. **Failure line:**
fail if B’s expected delta is satisfied only by A’s contents. **Code:** `ContainerInteractionBehavior.isContainerReadable`,
`RestockProcess.hasItemInContainer`. Difficulty: BLOCKED-ON-T-01.

### B7. `container-deposit-timeout-no-mutation`

After a deposit sync timeout, assert that the timed-out box’s contents did not change before the next
candidate is tried. **Failure line:** fail if a quick-move was sent to the stale menu despite the
timeout. **Code:** `RestockProcess.tickDepositing`, `ScenarioInventory.countContainer`. Difficulty:
BLOCKED-ON-T-01.

### B8. `container-persistence-round-trip`

Restart or reload the world and assert that a registered box’s item counts and missing/indexed flags
round-trip exactly. **Failure line:** fail if the persisted count, registry key, or missing state is
lost after reload. **Code:** `RestockBoxCollection.save/load`; `ScenarioInventory.countContainer`.
Difficulty: BLOCKED-ON-T-01 and needs-new-harness-capability (world reload).

## Ten to implement first

1. `restock-switch-cancels-old-path` — directly covers fixed M2, a temporary-process path bug that
   can waste an entire restock trip.
2. `restock-open-container-one-shot` — directly covers fixed H7 and protects against accidental
   repeated right-clicks around real containers.
3. `dump-deposit-timeout-quarantine` — directly covers fixed UB6, which otherwise regresses only on a
   later unload trip.
4. `restock-reindex-clears-giveup` — directly covers fixed M1 and validates that re-indexing can
   recover a long build after a previous shortage.
5. `shelter-preferred-rally-unload` — directly covers fixed UB3 and protects the safety-critical
   choice to unload at the box already reached under attack.
6. `shelter-sleep-attempt-cap` — directly covers the bounded retry behavior behind fixed UB1 and
   prevents a refused bed from becoming an unbounded loop.
7. `dump-active-substitute-kept` — directly covers fixed H10’s dangerous inventory predicate while
   using only player and build-state assertions.
8. `itemsaver-all-hotbar-spent` — covers the documented slot-zero fallback where itemSaver can
   silently break the last protected tool.
9. `builder-orient-timeout-independent-target` — ensures one impossible orientation does not poison
   otherwise buildable work, the remaining practical lesson from U-local-02/U-local-03.
10. `autoeat-mining-waits` — covers the fixed M4 interaction between eating and block breaking, once
    the harness can control hunger and observe use state.

## Untestable or capability-blocked with the current harness

- Any assertion about the contents, slot packing, or persisted item count of a container is
  **BLOCKED-ON-T-01**. Fix `ScenarioInventory.countContainer` first and add a known-positive read
  control before promoting B1–B8.
- Exact packet-order scenarios (`restock-quickmove-server-confirmed`, timeout races, and menu identity
  under delayed packets) need a deterministic packet-delay or server-lag fixture; a long tick budget
  is not a substitute for controlled ordering.
- Auto-eat scenarios need harness support for deterministic food level, item-use completion, and
  per-tick selected-slot/use-key snapshots. A final food count alone cannot prove that another process
  waited for the meal.
- Threat, bed, lava, portal, and avoidance scenarios need deterministic entity damage, time-of-day,
  dimension, and entity-lifecycle staging. `/summon` and `/damage` can cover the common case, but a
  portable seven-version fixture should not rely on mob AI timing.
- Dynamic re-index, terrain mutation, world reload, and chunk-unload scenarios need a scenario phase
  hook that can issue commands after `start`; current staging is intentionally front-loaded.
- Post-placement repeater-delay/note-block/redstone-state scenarios remain blocked on U-local-03:
  the builder has no post-placement interaction operation. Farming proposals remain out of scope
  because there is no farming process in this fork. These are feature/capability work, not missing
  assertions.
- ProcessScheduler’s pure arbitration edge cases (equal-priority registration order, null command
  rejection, and same-tick activation) are better covered by its existing unit-test seam than by a
  live harness scenario; wrapping them in a world scenario would add time without making the failure
  more observable.
