# Defect ledger

Triage baseline: `db73df85` (HEAD), compared with `upstream/26.1`. The four source documents are
historical notes; this file is the current ledger. No Gradle task or substitute build was run.

Origin was checked mechanically against `upstream/26.1`: a fork-only symbol is FORK; an upstream
symbol changed by this fork is also FORK, with the responsible commit named below. All 37 reported
items resolve to FORK-origin defects or fork-document issues. The upstream primitives mentioned by
some findings do not change that result. `UB4` is retained as a separate row and marked DUPLICATE
of `H6`; it is the narrower command-level form of the same container-ownership problem.
Only U-local-01 through U-local-04 are taken from `UPSTREAM_BUG_BACKLOG.md`; T01-T60
are intentionally not triaged here.

## Summary

| ID | Source | Summary (<=60 chars) | Severity | Origin | Status | Test |
|---|---|---|---|---|---|---|
| H1 | sol-high-review.md | Churn blacklist survives reroute | high | FORK | FIXED | None |
| H2 | sol-high-review.md | Reroute clamp observes selected destination | high | FORK | FIXED | None for exact case |
| H3 | sol-high-review.md | Orientation timeout skips bad stand goal | high | FORK | FIXED | build-observers (not timeout) |
| H4 | sol-high-review.md | Shulker clearance uses lid direction/shape | high | FORK | PARTIAL | restock-from-box (up only) |
| H5 | sol-high-review.md | Missing one item does not poison a box | high | FORK | FIXED | restock-from-box (not cross-item) |
| H6 | sol-high-review.md | Container session ownership is unproven | high | FORK | PARTIAL | None |
| H7 | sol-high-review.md | Right-click is one-shot | high | FORK | FIXED | None |
| H8 | sol-high-review.md | Transfers settle before success is assumed | high | FORK | PARTIAL | restock-from-box (normal path) |
| H9 | sol-high-review.md | Capacity failure retries after dumping | high | FORK | FIXED | None for full-inventory case |
| H10 | sol-high-review.md | All configured substitutes count as wanted | high | FORK | FIXED | None |
| M1 | sol-high-review.md | Re-indexing clears stale give-ups | medium | FORK | FIXED | None |
| M2 | sol-high-review.md | Box switches cancel the old path | medium | FORK | FIXED | None (sketch recorded below) |
| M3 | sol-high-review.md | Restock pathing has a no-progress guard | medium | FORK | FIXED | None |
| M4 | sol-high-review.md | Eating coordinates with other work | medium | FORK | PARTIAL | None |
| M5 | sol-high-review.md | New settings and radius are bounded | medium | FORK | PARTIAL | RestockProcessTest (arithmetic only) |
| M6 | sol-high-review.md | Bulk registration saves safely | medium | FORK | FIXED | None |
| M7 | sol-high-review.md | Added API methods preserve compatibility | medium | FORK | FIXED | None |
| M8 | sol-high-review.md | CI and regression signal were restored | medium | FORK | FIXED | CI workflow; harness is manual |
| L1 | sol-high-review.md | Invalid indexboxes arguments are rejected | low | FORK | FIXED | None |
| L2 | sol-high-review.md | Unknown persisted items force re-indexing | low | FORK | FIXED | None |
| L3 | sol-high-review.md | Fork notes still omit auto-eat details | low | FORK | FIXED | None (text change) |
| L4 | sol-high-review.md | Reroute documentation contradicts itself | low | FORK | FIXED | None (text change) |
| L5 | sol-high-review.md | Runtime artifacts do not match HEAD | low | FORK | PARTIAL | None |
| L6 | sol-high-review.md | Ten-step lookahead is only a heuristic | low | FORK | INVALID | None |
| UB1 | UNTRACKED_BUG_REVIEW.md | Refused beds become retryable | high | FORK | FIXED | None |
| UB2 | UNTRACKED_BUG_REVIEW.md | Shelter handoff consumes completion tick | high | FORK | FIXED | None for shelter handoff |
| UB3 | UNTRACKED_BUG_REVIEW.md | Shelter unload stays at rally box | medium | FORK | FIXED | None |
| UB4 | UNTRACKED_BUG_REVIEW.md | addbox cannot borrow a nearby menu | medium | FORK | DUPLICATE | None |
| UB5 | UNTRACKED_BUG_REVIEW.md | Extra stacks use the item stack size | medium | FORK | FIXED | RestockProcessTest |
| UB6 | UNTRACKED_BUG_REVIEW.md | Deposit timeout excludes the box later | medium | FORK | FIXED | None |
| UB7 | UNTRACKED_BUG_REVIEW.md | Threat hit history has live capacity | low | FORK | FIXED | ThreatBehaviorTest |
| UB8 | UNTRACKED_BUG_REVIEW.md | Pickup binds one expected entity | low | FORK | FIXED | PickupBlocksProcessTest |
| U-local-01 | UPSTREAM_BUG_BACKLOG.md | Unreachable target is replanned too often | high† | FORK | OPEN | unreachable-build-target |
| U-local-02 | UPSTREAM_BUG_BACKLOG.md | Up-facing placement windows now agree | high† | FORK | FIXED | build-observers |
| U-local-03 | UPSTREAM_BUG_BACKLOG.md | Repeater delay needs post-placement interaction | medium† | FORK | OPEN | repeaters-delays |
| U-local-04 | UPSTREAM_BUG_BACKLOG.md | Hopper facing is falsely called missing | high† | FORK | FIXED | hoppers-facing (curated) |
| F1 | FORK-NOTES.md §8 | Shelter accepts a dangerously distant box | high† | FORK | OPEN | shelter-retreat-distance |

† The source section did not assign a severity; the values shown are my triage views: U-local-01,
U-local-04, and F1 are high because they can cause repeated work, false shortages, or unsafe
retreats; U-local-02 is high because it blocked a whole placement orientation; U-local-03 is medium
because it affects a narrower post-placement state.

## H1 — Churn cooldown is deleted immediately

The source claimed that detection inserted a blacklist entry and the reroute immediately erased it.
At HEAD, detection still inserts an expiry at `BuilderProcess.java:618-623`, but the reroute clears
only the per-action `churn` map at `BuilderProcess.java:650-657`; expiry cleanup is separate at
`BuilderProcess.java:831-834`. The finding is FIXED by `5f86abfb` (the commit removes
`churnBlacklist.clear()` from the reroute). Origin is FORK: the churn detector is introduced by
`1dd0a74f` and has no symbol in `upstream/26.1`. Test coverage: None.

## H2 — Reroute detection watches the candidate set, not the selected route

The source correctly described the old comparison, but that code is gone. HEAD observes the live or
next path destination at `BuilderProcess.java:1135-1148`, counts repeated failed goals at
`BuilderProcess.java:1177-1195`, and emits a null carry-on command only when a live path or
calculation exists at `BuilderProcess.java:1196-1207`. `5f86abfb` replaced the `GoalComposite`
equality check and guarded the null command. Origin is FORK: `commitAwarePathingCommand` is fork
code, even though `GoalComposite.equals` remains upstream code at `GoalComposite.java:70-80`.
The exact selected-route clamp has no direct test; `unreachable-build-target` exercises a separate
remaining failure mode.

## H3 — Orientation timeout can strand a wrong-side goal

The source's timeout failure is no longer the control flow. A timed-out position is removed from the
orientation goal and not handed to `GoalAdjacent` at `BuilderProcess.java:1605-1621` and
`BuilderProcess.java:1663-1678`; the builder keeps a feet goal while the retry timer runs at
`BuilderProcess.java:1041-1045`. Timers start only for a selected destination or actual placement
attempt at `BuilderProcess.java:1684-1700`, rather than every nearby candidate. `5f86abfb` fixed
this. Origin is FORK: the orientation planner was added by `a045382f`. `build-observers` covers
orientation placement, but not an unreachable-side timeout.

## H4 — Shulker pathing checks the wrong lid clearance direction

The directional half is fixed: `goalForBox` reads the live `FACING`, tests `pos.relative(facing)`,
and otherwise uses `GoalGetToBlock` at `RestockProcess.java:627-645`. This is fixed by `85bc8236`.
The source also called out partial collision shapes; that half remains because the check is still
`MovementHelper.isBlockNormalCube` at `RestockProcess.java:633-640`, not the actual lid collision
volume. Status is therefore PARTIAL. Origin is FORK: `RestockProcess` is absent from
`upstream/26.1`. The existing `restock-from-box` scenario uses the ordinary upward case only.

## H5 — A box missing one material is blacklisted for every material

HEAD separates operational failures from per-item misses: the state is split at
`RestockProcess.java:120-138`, candidate search excludes only the missing item for that box at
`RestockProcess.java:508-520`, and a live absence records that item at
`RestockProcess.java:717-722`. `85bc8236` implemented this separation. Origin is FORK because
the restock process and its failure sets are fork-only. `restock-from-box` covers stale-index
fallback, not the two-material same-box case.

## H6 — The restock process never proves the open menu is targetBox

The source's command-level eager indexing path is gone: `#addbox` now only registers the requested
position at `RestockBoxCommand.java:104-120`. The restock process also closes a pre-existing menu
and requires a targeted click before latching a menu ID at `RestockProcess.java:647-686`, then
requires that ID for sync and transfer at `RestockProcess.java:690-705` and `RestockProcess.java:746-754`.
That is only PARTIAL: the client still does not record the exact block position that produced the
new menu, so a concurrent/foreign menu appearing after `attemptedTargetOpen` can still be accepted
under `targetBox`. The command half was addressed by `b3c4f7bf`; the session changes were made by
`85bc8236`. Origin is FORK. Test coverage: None.

## H7 — Right-click remains forced through the whole container session

HEAD clears the opening input on every non-opening state at `RestockProcess.java:546-553`, clears
it before and after opening at `RestockProcess.java:647-664`, and clears it during failure, reset,
and control loss at `RestockProcess.java:1064-1072` and `RestockProcess.java:1171-1217`.
`85bc8236` made opening one-shot. Origin is FORK. Test coverage: None.

## H8 — Optimistic quick-moves are treated as server-confirmed

The immediate-success part is addressed but not eliminated. HEAD waits ten ticks after the last
quick-move before judging the predicted inventory at `RestockProcess.java:776-808`, and deposit
completion similarly settles before using the local inventory delta at `RestockProcess.java:969-984`.
However, there is still no authoritative server acknowledgement or configurable latency bound; a
late rollback can arrive after the process closes. Status is PARTIAL, with the partial change in
`85bc8236`. Origin is FORK. `restock-from-box` covers a normal transfer, not a rejected or delayed
click.

## H9 — A full inventory permanently gives up on material before dumping

HEAD distinguishes a wanted stack that could not fit from a box that lacks the item. A capacity
failure enters deposit/retry at `RestockProcess.java:785-808` and retries the same transfer after
room is made at `RestockProcess.java:993-1005`; it no longer marks the wanted state unobtainable
on the first full-inventory click. `85bc8236` fixed the finding. Origin is FORK. No current test
reproduces the full-inventory state machine.

## H10 — Junk detection can deposit a usable configured substitute

The palette scan now unions every configured substitute before scanning the schematic at
`BuilderProcess.java:1227-1249`, and `inventoryWants` consumes that conservative answer at
`BuilderProcess.java:1255-1263`. `5f86abfb` fixed the source's substitute-selection error.
Origin is FORK: `schematicWants` and the restock junk rule are fork additions. Test coverage: None.

## M1 — Re-indexing does not clear current-build give-ups

HEAD calls `clearUnobtainable()` after a nonempty indexing queue at `RestockProcess.java:400-439`;
that clears the per-build sets at `RestockProcess.java:284-291`. `#indexboxes all` also rechecks
loaded boxes marked missing at `RestockBoxCommand.java:126-143` and `RestockBoxCommand.java:212-231`.
The two halves were fixed by `85bc8236` and `b3c4f7bf`, respectively. Origin is FORK. Test
coverage: None.

## M2 — Switching boxes does not immediately rescope the old paused path

FIXED in `edc086c7`. Box transitions returned `SET_GOAL_AND_PATH`, which
`PathingBehavior.secretInternalSetGoalAndPath` declines outright while a path is live
(`if (current != null) return false`), so the process could retarget while the pathing layer kept
executing the route to the previous box — walking to the wrong box, or standing still, after one was
rejected. Origin is FORK: the callers and `RestockProcess` are fork code.

Test coverage: None. A scenario sketch is recorded below; it was not written because the fix landed
against a reasoned trace rather than a reproduction, which is the weaker of the two kinds of
evidence this ledger accepts.

## M3 — PATHING has no progress watchdog

The absence is fixed, though the implementation reuses the container timeout setting. HEAD records
feet movement and resets the no-progress clock at `RestockProcess.java:1154-1168`; `tickPathing`
fails the current box once that clock exceeds `restockOpenTimeoutTicks` at
`RestockProcess.java:580-599`. `85bc8236` added the watchdog. Origin is FORK. Test coverage: None.

## M4 — Auto-eat does not make other Baritone work wait

This is PARTIAL. Eating now avoids mining while an item is in use because `BlockBreakHelper` exits
early at `BlockBreakHelper.java:66-76`, and a failed food start restores the prior slot at
`EatBehavior.java:109-123`; `2afe035b` and `35b69368` fixed those subissues. But `EatBehavior`
remains a behavior that holds the slot/use key while processes continue at `EatBehavior.java:88-94`
and `EatBehavior.java:145-167`, while block placement only checks `isHandsBusy` at
`BlockPlaceHelper.java:38-54`. The setting's promise that all other work waits is still unmet.
Origin is FORK. Test coverage: None.

## M5 — New numeric settings and radius input are insufficiently bounded

This is PARTIAL. Radius is rejected/clamped at `RestockBoxCommand.java:164-180`; fetch arithmetic
uses item stack size and saturates at `RestockProcess.java:536-544`, with unit coverage in
`RestockProcessTest.java:26-66`. The remaining settings are not validated: a stand distance above
four still becomes a stand goal at `BuilderProcess.java:1676-1681`, and a negative open timeout is
still directly used by the timeout comparisons at `RestockProcess.java:580-599` and
`RestockProcess.java:669-733`. `b3c4f7bf` addressed the radius and arithmetic portions, not all
setting validation. Origin is FORK. The test covers arithmetic only.

## M6 — Bulk registration and persistence are crash-fragile

The finding is FIXED. Radius registration batches additions and saves once at
`RestockBoxCollection.java:222-235`; persistence writes a temporary file, syncs it, keeps a backup,
and atomically replaces the primary at `RestockBoxCollection.java:153-207`, while load errors are
logged and the backup is tried at `RestockBoxCollection.java:83-149`. `b3c4f7bf` implemented these
changes. Origin is FORK. Test coverage: None.

## M7 — Public API additions are compatibility-breaking

The added methods now have compatibility defaults: `IBaritone.getRestockProcess` at
`src/api/java/baritone/api/IBaritone.java:67-76`, `IWorldData.getRestockBoxes` at
`src/api/java/baritone/api/cache/IWorldData.java:40-47`, and builder extensions at
`src/api/java/baritone/api/process/IBuilderProcess.java:69-96`. `b3c4f7bf` added those defaults.
Origin is FORK: these methods were added or changed by the fork. Test coverage: None.

## M8 — CI and regression coverage were removed while risk grew

The historical removal was reversed by `db73df85`: the current workflow installs Java 25 and runs
unit tests at `.github/workflows/build.yml:22-48`, and compiles all four loaders at
`.github/workflows/build.yml:50-77`. The in-game harness is still intentionally manual, but it is
registered and reports scenario verdicts through `TestingBehavior.java:113-143`. Status is FIXED
for the source finding's central claim that no CI/regression signal existed. Origin is FORK. Test
coverage: the CI workflow plus the named harness scenarios; it does not cover every state machine.

## L1 — `#indexboxes anything` is silently accepted

The command now rejects any token other than `all` at `RestockBoxCommand.java:126-143`, and offers
`all` in tab completion at `RestockBoxCommand.java:299-312`. `b3c4f7bf` fixed the finding. Origin is
FORK. Test coverage: None.

## L2 — Loading a stored index preserves a stale timestamp

When an item key is invalid or no longer resolves, load marks the record incomplete and writes
`lastIndexed` as zero at `RestockBoxCollection.java:124-143`; the error is also logged rather than
silently swallowed at `RestockBoxCollection.java:147-149`. `b3c4f7bf` fixed it. Origin is FORK. Test
coverage: None.

## L3 — Fork notes omit changes added after the old review

FIXED. `FORK-NOTES.md` gained an auto-eat section (§2b) covering why the use key is held for the
whole meal, why the tick ordering makes that safe, and why `EatBehavior` is registered last;
`EatBehavior` is now listed among the new files, along with the two files §2a changed. README's
promise that the notes cover everything fork-specific (`README.md:3-6`) therefore holds again.
Origin is FORK. Test coverage: None; this is a text change.

## L4 — Reroute documentation is internally inconsistent

FIXED as a documentation defect. `builderMaxReroutes` counts goal *changes*, so the fork notes were
correct and the setting's javadoc — which is also its `#help` text — was not: a stably chosen
unreachable target never changes the goal and so never trips the clamp. The javadoc now says so, and
points at the separate bounded-result handling for the unreachable case (`d0ba7b6a`, U-local-01),
which is what actually covers it. Origin is FORK. Test coverage: None; this is a text change.

## L5 — Ignored artifacts are stale and runtime evidence is missing

This is PARTIAL at HEAD, and is evidence hygiene rather than a production-code defect. `dist/` and
`logs/` remain ignored by `.gitignore:6` and `.gitignore:22`; the newest visible artifact is stamped
with `e0487ced`, older than HEAD `db73df85`, while `logs/latest.log` is now nonempty rather than
empty. Exact-HEAD runtime evidence is still absent, so the source's empty-log observation is stale
but its provenance concern remains. Origin is FORK. Test coverage: None.

## L6 — The fixed ten-movement lookahead is a heuristic

The source explicitly reported no new correctness defect here, only a possible future failure. That
does not meet the ledger's definition of a defect, so status is INVALID. The current heuristic is
visible at `BuilderProcess.java:660-687`; it is not evidence of a present failure. Origin is FORK.
Test coverage: None for the hypothetical beyond-horizon case.

## UB1 — A refused bed is permanently excluded

The timeout/refusal path now calls `rejectBed(..., false)` at `ShelterProcess.java:435-449`, while
structural path/unreachable failures pass `true` at `ShelterProcess.java:412-422`; only the latter
adds the bed to `rejectedBeds` at `ShelterProcess.java:492-498`. A refused bed is therefore eligible
again after regrouping, subject to the attempt count at `ShelterProcess.java:338-344` and
`ShelterProcess.java:399-404`. `07284bdd` fixed the finding. Origin is FORK. Test coverage: None.

## UB2 — Restock completion lets another process run mid-shelter

The handoff now pauses on entry at `ShelterProcess.java:361-386`, and `RestockProcess.finishTrip`
returns `REQUEST_PAUSE` when shelter is still active at `RestockProcess.java:1090-1100`. The
completion tick is therefore consumed. `85bc8236` fixed the handback gap. Origin is FORK. Test
coverage: None for the shelter/restock integration.

## UB3 — Shelter unloading can walk away from its rally box

Shelter now passes its selected rally box as a preferred target at
`ShelterProcess.java:361-368`; restock sorts that target first at `RestockProcess.java:303-380`.
`07284bdd` added the preferred-target handoff. Origin is FORK. Test coverage: None.

## UB4 — `#addbox` can index a nearby box under the wrong coordinates

This remains a distinct ledger row, but it is the narrower duplicate of `H6`: both are menu
ownership errors. The old `indexIfOpen` path is no longer called; `addBox` only validates and stores
the requested position at `RestockBoxCommand.java:104-120`. The remaining session-identity concern is
tracked in `H6`, so this row is DUPLICATE → H6 rather than silently merged. Origin is FORK. Test
coverage: None.

## UB5 — “Extra stacks” always means 64 items

`requestRestock` now passes `item.getDefaultMaxStackSize()` into `fetchTarget` at
`RestockProcess.java:473-487`; `fetchTarget` uses widened arithmetic and saturation at
`RestockProcess.java:536-544`. `07284bdd` fixed it, and `RestockProcessTest.java:26-66` covers
stack sizes and overflow. Origin is FORK.

## UB6 — Deposit sync timeout is retried on later trips

Deposit/session failures now go through `failCurrentBox`, which adds the box to both operational
failure sets for deposit-only work at `RestockProcess.java:1060-1083`; deposit candidates exclude
failed and full boxes at `RestockProcess.java:331-343`. `85bc8236` fixed the repeated-trip path.
Origin is FORK. Test coverage: None.

## UB7 — `shelterMinHits` above 16 disables sheltering

The hit history is now an unbounded deque at `ThreatBehavior.java:43-50`, and threshold handling
clamps invalid minima to at least one at `ThreatBehavior.java:122-147`. `07284bdd` fixed the fixed
16-entry capacity. `ThreatBehaviorTest.java:31-76` covers thresholds above three and invalid
settings. Origin is FORK.

## UB8 — Pickup can claim unrelated nearby drops

Each expected drop snapshots pre-existing entity UUIDs and binds only one new entity at
`PickupBlocksProcess.java:51-69` and `PickupBlocksProcess.java:111-147`; a bound expectation remains
sticky until that entity disappears at `PickupBlocksProcess.java:151-170`. `07284bdd` fixed the
spatial-reuse problem. `PickupBlocksProcessTest.java:29-75` covers pre-existing, radius, and
one-time binding cases. Origin is FORK.

## U-local-01 — Builder tight-replans an unreachable placement goal

This remains OPEN. The current commit-aware code still returns a fresh
`FORCE_REVALIDATE_GOAL_AND_PATH` on the measured path at `BuilderProcess.java:1196-1207`; the
uncurated scenario counts repeated identical searches and fails above its bound at
`UnreachableBuildTargetScenario.java:124-140`. The exact evidence needed for closure is a bounded
result without repeated identical searches, while reachable builds remain green. Origin is FORK:
the relevant `commitAwarePathingCommand` is fork-added by `5f86abfb`, not an upstream symbol. Test
coverage: `unreachable-build-target` currently reproduces the defect.

## U-local-02 — Up-facing placement was planned from an impossible click

The source's original mechanism was invalid: current placement simulation calls vanilla placement,
and the real fix was the mismatch between the stand window and placement scan. HEAD now scans through
`dy <= 2` at `BuilderProcess.java:528-538` and accepts UP feet at `targetY - 2` at
`BuilderProcess.java:1857-1865`. `6031da9f` fixed the actual fork defect. `build-observers` covers
all six observer facings. Origin is FORK: `GoalPlaceOriented` and the orientation planner were
added by `a045382f`; the source's upstream classification was wrong.

## U-local-03 — Builder cannot satisfy post-placement repeater delay states

This remains OPEN as a capability gap, not an orientation or material-shortage bug. The material
predicate waives only `ORIENTATION_PROPS` through `couldProduce` at `BuilderProcess.java:2135-2152`;
there is no post-placement interaction for `BlockStateProperties.DELAY`, and the no-goal path still
pauses at `BuilderProcess.java:1041-1049`. The `repeaters-delays` scenario reproduces the default
repeater plus missing delay 2–4 states. Origin is FORK: `couldProduce` is not in `upstream/26.1`
and was added by `a045382f`, even though it consults an upstream property set.

## U-local-04 — Hopper facing is absent from `ORIENTATION_PROPS`

**FIXED 2026-08-01.** `HopperBlock.FACING` added to `ORIENTATION_PROPS`
(`BuilderProcess.java:100-106`), so `couldProduce` now waives a hopper's facing and a plain hopper
counts as material for any of them.

The trap was that membership is by `Property` *instance*, not by name: `HopperBlock.FACING` resolves
to `BlockStateProperties.FACING_HOPPER`, a different object from the `BlockStateProperties.FACING`
that `DirectionalBlock.FACING` resolves to, so listing the latter did nothing for hoppers. That is
now stated in the field's javadoc, since the next one-off facing property will look identical.

Placement itself needed no change: `possibleToPlace` already iterates every candidate support face,
and a hopper takes its facing from the clicked face, so the correct face was always reachable — the
builder just refused to believe it had the material. Verified in game: `hoppers-facing` passes in
33s and is **promoted to curated**, per the rule that a defect's fix promotes its reproduction in
the same commit. Origin is FORK: `couldProduce` is fork-only, and the source's UPSTREAM
classification was wrong.

## F1 — Shelter retreat has no target-distance bound

This remains OPEN. `ShelterProcess.nearestBox` limits only against the general restock distance at
`ShelterProcess.java:568-581`, so a registered box roughly 188 blocks away is still selected. The
uncurated `shelter-retreat-distance` scenario stages that geometry and fails when the selected
distance exceeds its bound at `ShelterRetreatDistanceScenario.java:123-149`. Origin is FORK:
`ShelterProcess` is absent from `upstream/26.1`.

## What to do first

Open items ordered by severity, then approximate fix cost:

1. **U-local-04** — add the hopper-specific orientation property and keep the existing
   `hoppers-facing` reproduction green; it is an immediate false material shortage with a narrow
   code change.
2. **U-local-01** — make the failed-first-movement path reach a bounded state instead of starting
   identical searches; it is the clearest remaining CPU/chat flood.
3. **F1** — define and enforce a shelter-specific retreat bound; the current behavior can tunnel
   through unrelated terrain while under attack.
4. **M2** — cancel or force-revalidate the old restock segment on every target switch; the likely
   fix is small, but the handoff needs a focused scheduler/path test.
5. **U-local-03** — add bounded post-placement repeater interaction without letting one unsupported
   state poison unrelated build work.
6. **L4** — reconcile the setting documentation with the actual reroute behavior.

The best cheap, untested candidates are **M2** (old path rescoping) and **L4** (documentation
correction). `U-local-04` also looks cheap, but it already has the `hoppers-facing` harness
scenario.

## Performance observations

Both of the below were **applied 2026-08-01**. Neither produced a measurable change in the curated
suite (208s -> 214s, inside noise), and that is the honest result: every build scenario in the suite
is tiny -- a 5x5x3 ring, six observers -- so `placeable` holds a handful of positions and the
quadratic term never bites. The changes are correct and strictly cheaper; this suite simply cannot
show it.

- **`assemble` was quadratic in the size of the build.** `placeable.contains(pos.below())` and
  `.contains(pos.below(2))` ran once per placeable position over the same `ArrayList`, so a
  schematic with a few thousand placeable positions turned one `assemble()` into millions of
  `BlockPos` comparisons. Now a `HashSet` built once per call
  (`BuilderProcess.java:1480-1486`).

  A hazard worth recording: `BetterBlockPos.hashCode()` is *deliberately* different from
  `BlockPos.hashCode()` (see the comment in `BetterBlockPos`), so a set of `BetterBlockPos` queried
  with a plain `BlockPos` key silently misses every time. That would not have failed loudly -- it
  would have quietly disabled the guard and reintroduced the break/place loop it exists to prevent.
  It is safe here only because `BetterBlockPos` overrides `below()` and `below(int)` to return
  `BetterBlockPos`.

- **`pathNeedsOpen` allocated a list wrapper per movement.** It is called once per candidate
  position by the per-tick placement scan -- hundreds of positions a tick -- and each call wrapped
  up to ten `toBreakAll()` arrays in `Arrays.asList` purely to call `contains`. Now scanned
  directly (`BuilderProcess.java:692-701`).

### The measurement gap this exposed

Performance is a stated goal of Continuo, but **no scenario in the suite would detect a
build-scaling regression.** The suite proves correctness on small builds and says nothing about
cost on large ones. A scenario that builds something big enough for the quadratic terms to matter --
and asserts on tick count, which the harness already records -- is the missing instrument. Until it
exists, "measure before and after" can only catch regressions that happen to slow down small builds.
