# Upstream Baritone bug backlog

Source: the official [cabaletta/baritone issue tracker](https://github.com/cabaletta/baritone/issues), reviewed 2026-07-28. This is deliberately **not** a claim that every open upstream issue is still reproducible: GitHub keeps many fixed reports open.

## How to use this file

Each heading below is a separately assignable task. An agent owns only its task section and the code necessary to complete it; do not mix unrelated fixes into the same change. Before editing code, mark the reproduction result in the task:

- `CONFIRMED`: reproduces on this branch's Minecraft `26.1.2` baseline and a minimal loader/mod set.
- `ALREADY FIXED`: the report does not reproduce and the responsible existing commit/test is recorded.
- `DUPLICATE`: a prior task fixed the same root cause; link its commit and re-run this report's scenario.
- `EXTERNAL`: minimal Baritone works and the failure is caused by an invalid/third-party registry or incompatible mixin; record the minimal conflicting set.
- `BLOCKED`: report lacks a usable reproducer; document exactly what evidence is needed.

Every completed task must include: the baseline commit, loader and mods, precise world/command setup, test evidence, changed files, and whether the official issue should be closed, cross-linked, or left open. Run focused tests plus the relevant loader build. Do not overwrite the existing uncommitted work in this checkout.

## Evidence already established

- All issues below were open on the official tracker when this backlog was assembled.
- This checkout is ahead of `upstream/26.1` and includes local builder-loop/reroute commits `1dd0a74f` and `5f86abfb` (2026-07-26/27). They are candidates for #5064, #5077, #4888, #4562, #2877, and #2173, but still need scenario-level verification.
- `c02114ad` (2026-06-19, `BlockOptionalMeta` game-registry loot resolution) is a candidate for the cluster of modded `#mine` registry failures; do not mark those fixed until tested with the reported loaders.
- Historical source changes plausibly cover #4319 (`2e32c63b`, `6a6d0642`) and #3142 (`3e3312f0`). Those are verification tasks, not presumed new fixes.

## P0 — crashes, command-wide failure, infinite actions, or player-loss risk

## T01 — #5064: bridge block is placed/broken forever

Issue: [#5064](https://github.com/cabaletta/baritone/issues/5064) · `#sel cleararea`/replace, bridge with a mineable block.

- Status: `[ ] verify existing fix`; local commits `1dd0a74f`/`5f86abfb` are direct candidates.
- Owns: builder target selection and temporary bridge preservation in `BuilderProcess`.
- Work: reproduce on a suspended clearing target; prove that a bridge remains protected until the inaccessible target is handled, then becomes removable. If the loop guards only hide the loop, implement explicit temporary-scaffold ownership/lifetime instead.
- Done when: no alternating place/break at one coordinate; clear/replace completes; legitimate temporary blocks are later removed; regression covers mineable scaffold material.

## T02 — #5077: ClearArea stalls after clearing a layer

Issue: [#5077](https://github.com/cabaletta/baritone/issues/5077) · current 1.21.6 Fabric report.

- Status: `[ ] verify existing fix`; overlaps local reroute changes.
- Owns: `BuilderProcess` layer advancement, failed-path recovery, `buildInLayers`.
- Work: reproduce the 160×55×55 selection and a minimized suspended-floor case; distinguish “no remaining reachable targets” from a stale goal/path. Repair the state transition that keeps a completed layer live.
- Done when: normal and layered clearing advance without manual breaking or restart, and unreachable work exits with a useful diagnostic rather than recalculating forever.

## T03 — #5009: selection clearing ignores walkWhileBreaking

Issue: [#5009](https://github.com/cabaletta/baritone/issues/5009).

- Status: `[ ] unverified`.
- Owns: selection-generated builder actions and `MovementTraverse`/break input coordination.
- Work: compare `#mine`, `#tunnel`, `#sel cleararea`, and `#sel replace` on the same linear fixture; route selection breaking through the same legal walk-and-break path where safe.
- Done when: selection work advances while breaking when the setting is enabled, still stands still where movement would be unsafe, and never holds attack on an already-failed block.

## T04 — #5051: mine cannot reach targets behind unbreakable blocks

Issue: [#5051](https://github.com/cabaletta/baritone/issues/5051).

- Status: `[ ] unverified`.
- Owns: `MineProcess` target pruning and path-goal construction.
- Work: make a minimal protected-block enclosure with `allowBreak=false` and `allowBreakAnyway=diamond_ore`; ensure target reachability considers legal approach paths and does not discard the target prematurely.
- Done when: reachable protected targets are mined without breaking prohibited blocks; genuinely sealed targets terminate as unreachable with a reason.

## T05 — #4909: typing/completing #mine freezes or crashes

Issue: [#4909](https://github.com/cabaletta/baritone/issues/4909).

- Status: `[ ] unverified`; likely shares the registry-stub cluster below.
- Owns: `ForBlockOptionalMeta`, `BlockOptionalMeta`, command completion.
- Work: reproduce both execution and trailing-space completion; remove command-thread world/registry construction that can deadlock or throw, and bound/cache only safe registry lookups.
- Done when: typing, completing, and executing block selectors remain responsive with valid and invalid selectors.

## T06 — #4967: Fabric modpack #mine initializer exception

Issue: [#4967](https://github.com/cabaletta/baritone/issues/4967).

- Status: `[ ] verify c02114ad`; tracker stack traces point to `BlockOptionalMeta.ServerLevelStub`.
- Owns: `BlockOptionalMeta` loot/drop resolution.
- Work: build a minimal Fabric reproduction from the issue’s failing registry data; verify whether game-registry lookup in `c02114ad` fixes it. If not, make drop resolution use the active client registry without constructing a server level.
- Done when: `#mine` works in the reduced pack and ordinary vanilla/modded block selectors retain correct drop matching.

## T07 — #4926: BlockOptionalMeta ServerLevelStub cannot initialize

Issue: [#4926](https://github.com/cabaletta/baritone/issues/4926).

- Status: `[ ] verify c02114ad`; same root-cause candidate as T06.
- Owns: `BlockOptionalMeta.ServerLevelStub` failure boundary.
- Work: reproduce `ExceptionInInitializerError`; ensure one failed registry load cannot permanently poison the class or all later commands.
- Done when: invalid external registries surface a scoped command diagnostic and a valid game can execute later block commands without restart.

## T08 — #4920: NeoForge mine command NoClassDefFoundError

Issue: [#4920](https://github.com/cabaletta/baritone/issues/4920).

- Status: `[ ] verify c02114ad`; depends on T06/T07.
- Owns: NeoForge registry access path.
- Work: reproduce on minimal NeoForge 1.21.1 and validate the same safe registry abstraction on NeoForge.
- Done when: `#mine iron_ore` works, and no class initialization error remains.

## T09 — #4897: Biomes O’ Plenty/TerraBlender mine registry crash

Issue: [#4897](https://github.com/cabaletta/baritone/issues/4897).

- Status: `[ ] verify c02114ad`; depends on T06/T07.
- Owns: modded registry isolation.
- Work: reduce the pack to the smallest failing mod set; classify as Baritone defect only if valid game registries still fail.
- Done when: either the command succeeds in the reduced valid pack or the issue records an external registry defect with exact evidence.

## T10 — #5072: current #mine command fails

Issue: [#5072](https://github.com/cabaletta/baritone/issues/5072).

- Status: `[ ] unverified`.
- Owns: mine command triage; may be a duplicate of T05–T09.
- Work: obtain the reporter’s stack trace/reproducer, run it on the current baseline, and route it to the exact command, selector, scanner, or registry root cause.
- Done when: classified as a distinct fix or closed as a tested duplicate; never leave a generic “unhandled exception” without its causal exception in the task record.

## T11 — #5040: 26.1.2 mine errors

Issue: [#5040](https://github.com/cabaletta/baritone/issues/5040).

- Status: `[ ] external-candidate`: supplied trace includes a third-party enchantment registry failure.
- Owns: diagnostic/error containment only unless minimal Baritone reproduces.
- Work: reproduce with only Fabric/Baritone, then add the reported mod. Do not patch around invalid third-party registry state; make Baritone’s message identify the failing registry/mod and avoid misleading issue generation.
- Done when: minimal Baritone works, and an external failure has actionable diagnostics without a crash loop.

## T12 — #5021: ATM10 crashes on new world/menu

Issue: [#5021](https://github.com/cabaletta/baritone/issues/5021).

- Status: `[ ] unverified`.
- Owns: NeoForge initialization/mixin compatibility.
- Work: reduce ATM10 to Baritone plus the first conflicting dependency; inspect the supplied crash cause before changing code.
- Done when: a minimal reproducible incompatibility is fixed or documented with a loader/version guard and a clear upstream handoff.

## T13 — #5065: Neo Origins load crash

Issue: [#5065](https://github.com/cabaletta/baritone/issues/5065).

- Status: `[ ] unverified`.
- Owns: NeoForge mixins and startup transforms.
- Work: reproduce with only Baritone and Neo Origins, compare transformed targets, and remove illegal assumptions about startup order or class visibility.
- Done when: both mods load together on the supported NeoForge version and a no-mod Baritone startup regression passes.

## T14 — #4621: pathing command crashes

Issue: [#4621](https://github.com/cabaletta/baritone/issues/4621).

- Status: `[ ] unverified`.
- Owns: command-to-pathing exception boundary.
- Work: obtain the exact stack trace, reproduce with `#goto`/`#path`, and add a narrow regression at the failing data boundary.
- Done when: command failure cannot crash the game; the original command succeeds or emits a specific recoverable error.

## T15 — #3315: any pathfind command crashes with invalid slice bounds

Issue: [#3315](https://github.com/cabaletta/baritone/issues/3315).

- Status: `[ ] historical verification`; report is 1.18.2.
- Owns: path history/rendering list bounds.
- Work: test a short path below the historic 50-node assumption; identify the commit that removed the invalid `subList` range or patch it if still present.
- Done when: zero/short path histories render safely and all path commands survive fresh startup.

## T16 — #3790: mining repeatedly attacks one block

Issue: [#3790](https://github.com/cabaletta/baritone/issues/3790).

- Status: `[ ] unverified`; likely overlaps T03/T17/T18.
- Owns: break-progress acknowledgement, path recalculation, desync recovery.
- Work: create a delayed-break/server-ack fixture; after bounded failed progress, release input, invalidate the target, and replan rather than retry indefinitely.
- Done when: no unbounded attack loop; successful slow mining remains uninterrupted.

## T17 — #4932: mining stalls at a block edge

Issue: [#4932](https://github.com/cabaletta/baritone/issues/4932).

- Status: `[ ] unverified`.
- Owns: reachability/standing-position selection while mining.
- Work: reproduce at a one-block edge with layered settings; validate feet position and raycast target before committing the break.
- Done when: mine either steps to a legal stand position or explicitly replans; it does not idle at the edge.

## T18 — #4930: #sel mining needs manual player movement

Issue: [#4930](https://github.com/cabaletta/baritone/issues/4930).

- Status: `[ ] unverified`; depends on T03/T17.
- Owns: selection failed-movement recovery.
- Work: add a no-user-input regression fixture and ensure selection work invalidates a stuck path when manual movement would formerly unblock it.
- Done when: selection makes progress autonomously or reports why it cannot.

## T19 — #4888: clear a selection containing a cave paths forever

Issue: [#4888](https://github.com/cabaletta/baritone/issues/4888).

- Status: `[ ] verify existing local builder reroute fix`.
- Owns: builder target prioritization across gaps/caves.
- Work: use a cave-in-selection fixture; verify the reroute guard actually changes selected targets rather than only delaying retries.
- Done when: completion or a bounded unreachable report, no endless recalculation.

## T20 — #4886: ClearArea cannot cross a gap or tall obstacle

Issue: [#4886](https://github.com/cabaletta/baritone/issues/4886).

- Status: `[ ] unverified`; overlaps T02/T19.
- Owns: clearing access path/scaffold policy.
- Work: test a gap and tall tree separately; define when temporary scaffolding is allowed for pure-air schematics.
- Done when: safe legal access is used and impossible targets are surfaced, not silently waited on.

## T21 — #4948: selection-to-air refuses required temporary placement

Issue: [#4948](https://github.com/cabaletta/baritone/issues/4948).

- Status: `[ ] unverified`; depends on T01/T20.
- Owns: pure-air schematic/scaffold accounting.
- Work: permit temporary blocks only when they enable a remaining target and track them independently from the desired air state.
- Done when: clear-area can bridge when configured/possible and leaves no owned scaffold after successful completion.

## T22 — #4562: builder stuck in a break/place loop

Issue: [#4562](https://github.com/cabaletta/baritone/issues/4562).

- Status: `[ ] verify existing local churn detector`.
- Owns: `BuilderProcess` churn detection.
- Work: run the reporter scenario plus a normal long break to prove alternation—not repeated mining ticks—triggers the detector.
- Done when: a real loop reroutes/diagnoses and ordinary builds do not false-positive.

## T23 — #2877: buildInLayers paths forever

Issue: [#2877](https://github.com/cabaletta/baritone/issues/2877).

- Status: `[ ] verify existing local reroute fix`.
- Owns: layered builder goal lifecycle.
- Work: reproduce with a small schematic and `buildInLayers=true`; test layer transition and no-path behavior independently.
- Done when: all layers complete or the failed layer is reported according to `skipFailedLayers`.

## T24 — #2173: schematic builder loops

Issue: [#2173](https://github.com/cabaletta/baritone/issues/2173).

- Status: `[ ] historical verification`; overlaps T22/T23.
- Owns: builder convergence and target replacement.
- Work: obtain/replace the linked schematic with a minimized fixture, then classify against current churn/reroute behavior.
- Done when: confirmed duplicate or fixed with a durable regression fixture.

## T25 — #3995: builder digs out its own support and can die

Issue: [#3995](https://github.com/cabaletta/baritone/issues/3995).

- Status: `[ ] unverified`; safety-critical.
- Owns: builder break ordering, safe standing position, fall/lava risk.
- Work: create suspended road repair fixtures over void/lava; prohibit breaking the supporting block until the player has a verified safe alternative.
- Done when: repair completes without a fall; a deliberately unsafe-only action is deferred with a reason.

## T26 — #4230: death while pathing softlocks behavior

Issue: [#4230](https://github.com/cabaletta/baritone/issues/4230).

- Status: `[ ] unverified`.
- Owns: player death/respawn pathing reset.
- Work: kill player during an active path, assert all forced inputs/path state clear, then issue a new command.
- Done when: respawn never retains locked movement, stale goals, or stale block actions.

## T27 — #848: goal completion at a cliff/jump causes death

Issue: [#848](https://github.com/cabaletta/baritone/issues/848).

- Status: `[ ] historical verification`; safety-critical.
- Owns: movement completion and momentum cancellation.
- Work: test cliff-edge and post-parkour completion; retain/force safe movement state until velocity and footing are safe.
- Done when: arrival cannot convert existing momentum into a fall.

## T28 — #161: low TPS causes block-intersection retry loop

Issue: [#161](https://github.com/cabaletta/baritone/issues/161).

- Status: `[ ] historical verification`.
- Owns: server-authoritative break acknowledgement and stuck recovery.
- Work: simulate delayed/rejected block updates; use bounded recovery before cancelling/replanning.
- Done when: no infinite recalculation inside a rejected block and normal high-efficiency mining remains performant.

## P1 — major movement, mining, farming, and compatibility defects

## T29 — #5053: path is found but player never moves

Issue: [#5053](https://github.com/cabaletta/baritone/issues/5053).

- Status: `[ ] unverified`.
- Owns: path executor/input override handshake.
- Work: log path selection versus forced inputs and control ownership; repair whichever state disconnects planning from movement.
- Done when: a valid path produces the expected movement inputs and `#stop` restores manual control.

## T30 — #4768: pathfinds but neither moves nor mines

Issue: [#4768](https://github.com/cabaletta/baritone/issues/4768).

- Status: `[ ] unverified`; compare with T29.
- Owns: executor state; may be a duplicate.
- Work: reproduce with the reporter loader/version and classify whether input override, rotation, or block action acknowledgement is responsible.
- Done when: independently verified duplicate or a separate fixed root cause.

## T31 — #4832: maze turns are ignored

Issue: [#4832](https://github.com/cabaletta/baritone/issues/4832).

- Status: `[ ] unverified`.
- Owns: rotation and directional input conversion.
- Work: add a compact multi-turn maze fixture and compare selected movement direction to path edges every tick.
- Done when: player follows each turn without walking straight into walls or dead ends.

## T32 — #4653: after a goal, player jumps into old target area

Issue: [#4653](https://github.com/cabaletta/baritone/issues/4653).

- Status: `[ ] unverified`.
- Owns: previous-goal cancellation and queued inputs.
- Work: inspect stale movement/jump intent after completion and clear it before accepting the next goal.
- Done when: completed path has no delayed movement toward its former target.

## T33 — #4425: pathing skips blocks/nonsensically routes

Issue: [#4425](https://github.com/cabaletta/baritone/issues/4425).

- Status: `[ ] unverified`.
- Owns: movement-cost/neighbor validity calculation.
- Work: obtain a minimal world/setting fixture, assert every planned edge is executable, and repair invalid cost or collision assumptions.
- Done when: renderer, planned edges, and executed movement agree.

## T34 — #3874: Baritone wanders when it has no usable route

Issue: [#3874](https://github.com/cabaletta/baritone/issues/3874).

- Status: `[ ] unverified`.
- Owns: no-path fallback and goal invalidation.
- Work: distinguish exploratory fallback from a user-directed goal; stop/replan deterministically when no route exists.
- Done when: no random wandering under a fixed unreachable goal and diagnostics identify the blocked condition.

## T35 — #4946: #goto/#mine ancient debris pathfinding bug

Issue: [#4946](https://github.com/cabaletta/baritone/issues/4946).

- Status: `[ ] unverified`.
- Owns: Nether scanner, target goal generation, mine/drop matching.
- Work: reproduce on a minimal Nether fixture; compare `#goto` and `#mine` target sets and their Y-level constraints.
- Done when: both commands find reachable ancient debris consistently or explain restrictions accurately.

## T36 — #2785: #mine cannot find ancient debris while #goto can

Issue: [#2785](https://github.com/cabaletta/baritone/issues/2785).

- Status: `[ ] historical verification`; compare with T35.
- Owns: mine scanner filtering.
- Work: use the same fixture as T35 and classify/fix only a remaining behavior difference.
- Done when: duplicate or a covered regression for mine-vs-goto parity.

## T37 — #4350: quantity-limited #mine keeps mining

Issue: [#4350](https://github.com/cabaletta/baritone/issues/4350).

- Status: `[ ] unverified`.
- Owns: `MineProcess` quantity accounting and item pickup matching.
- Work: test exact target count, stacked drops, delayed pickup, and multiple matching target types.
- Done when: process stops at the requested count without under/over-counting and resumes correctly only when reissued.

## T38 — #4658/#735: ice is not mined

Issues: [#4658](https://github.com/cabaletta/baritone/issues/4658), [#735](https://github.com/cabaletta/baritone/issues/735).

- Status: `[ ] unverified`; one task because both report the same behavior.
- Owns: block/drop matching and harvestability.
- Work: test ice, packed ice, blue ice, and required tool/enchantment combinations; ensure a valid target is not pruned as impossible.
- Done when: each legally harvestable ice variant is targeted and no invalid tool choice is made.

## T39 — #3452: blue-ice drops are not reliably picked up

Issue: [#3452](https://github.com/cabaletta/baritone/issues/3452).

- Status: `[ ] historical verification`; related to T38.
- Owns: pickup/drop association in `MineProcess`.
- Work: test delayed and displaced drops around blue ice; prevent completion before the expected item is observed or timed out with a reason.
- Done when: no false completion while an obtainable required drop remains nearby.

## T40 — #4913: mangrove farming stalls on vines

Issue: [#4913](https://github.com/cabaletta/baritone/issues/4913).

- Status: `[ ] unverified`; source contains a recent nether-vine fix, not proof for mangrove vines.
- Owns: `FarmProcess`, vine movement/break policy.
- Work: build a mangrove fixture with side/bottom vines; distinguish legal vine clearing from prohibited foliage damage.
- Done when: farm advances safely through supported vine arrangements.

## T41 — #4989: dense farms intermittently fail

Issue: [#4989](https://github.com/cabaletta/baritone/issues/4989).

- Status: `[ ] blocked until minimized`; report is intermittent.
- Owns: `FarmProcess` scan/retry lifecycle.
- Work: first build a deterministic fast-regrowth/dense-crop reproduction; then preserve retry state across transient crop updates instead of terminal failure.
- Done when: the minimized farm runs for a documented soak duration without false `Farming Failed`.

## T42 — #1773: cannot mine logs with vines below

Issue: [#1773](https://github.com/cabaletta/baritone/issues/1773).

- Status: `[ ] historical verification`; related to T40.
- Owns: tree/log target reachability with vines.
- Work: test the underside-vine case after T40; avoid treating passable foliage as a permanent collision obstacle.
- Done when: the log is mined with safe movement and no vine-induced deadlock.

## T43 — #1897: seagrass stalls water pathing

Issue: [#1897](https://github.com/cabaletta/baritone/issues/1897).

- Status: `[ ] historical verification`.
- Owns: water collision/passability rules.
- Work: test shallow water and seagrass variants with/without walk-on-water capability; align planner passability with execution.
- Done when: route traverses, detours, or reports a block—never bobs indefinitely.

## T44 — #4319: wrong tool for axe/shovel/hoe blocks

Issue: [#4319](https://github.com/cabaletta/baritone/issues/4319).

- Status: `[ ] verify existing fix`; source changes in 2023 replaced legacy tool selection with block-state-aware `ToolSet` logic.
- Owns: `ToolSet` and block-state break-time selection.
- Work: table-test affected axe, shovel, and hoe blocks with competing hotbar tools; only change code if the current checkout fails.
- Done when: fastest valid tool is selected and historical report is marked already fixed or receives a narrow regression.

## T45 — #3142: carpeted stairs block pathing

Issue: [#3142](https://github.com/cabaletta/baritone/issues/3142).

- Status: `[ ] verify existing fix`; `3e3312f0` replaced an obsolete carpet class.
- Owns: collision/passability handling for carpet.
- Work: reproduce stair ascent with modern carpet blocks and confirm both planner and executor see the same collision shape.
- Done when: verified fixed or corrected with a stair-and-carpet regression.

## T46 — #4321: C2ME plus #mine crashes

Issue: [#4321](https://github.com/cabaletta/baritone/issues/4321).

- Status: `[ ] unverified`.
- Owns: asynchronous world/registry assumptions.
- Work: reduce to Baritone + C2ME; identify thread access in the crash. Move unsafe world accesses onto the allowed thread or make scanning thread-safe.
- Done when: #mine runs with supported C2ME configuration and normal scanning performance is retained.

## T47 — #3225: backfill loop

Issue: [#3225](https://github.com/cabaletta/baritone/issues/3225).

- Status: `[ ] historical verification`.
- Owns: `BackfillProcess` action completion and target blacklisting.
- Work: create a finite backfill fixture, trace per-position state, and prevent revisiting work already completed/failed within one run.
- Done when: finite input terminates, and unavailable material has a bounded error path.

## T48 — #3399: air selected as a placeable throwaway item

Issue: [#3399](https://github.com/cabaletta/baritone/issues/3399).

- Status: `[ ] historical verification`.
- Owns: inventory throwaway filtering and placement candidate validation.
- Work: inject air into configured throwaway candidates and assert it can never enter placement selection, even if an empty hotbar slot exists.
- Done when: no right-click/placement loop and invalid configuration is ignored or diagnosed.

## T49 — #2534: clear-area plus vertical buildRepeat runs out of memory

Issue: [#2534](https://github.com/cabaletta/baritone/issues/2534).

- Status: `[ ] historical verification`.
- Owns: builder repeat bounds, target caches, world selection memory.
- Work: run a bounded vertical repeat under heap profiling; cap/stream position state and reject unbounded requests before allocation.
- Done when: memory use remains bounded for configured limits and unbounded work is rejected with a clear limit.

## T50 — #717: high render distance causes severe scanner lag

Issue: [#717](https://github.com/cabaletta/baritone/issues/717).

- Status: `[ ] historical performance verification`.
- Owns: world scanning and block lookup cache.
- Work: benchmark 16/32-chunk render distances and affected block queries; profile before changing algorithms, then eliminate the dominant repeated scan/allocation.
- Done when: benchmark records a material improvement with no target-discovery regression.

## T51 — #4119: no-collision grass causes mine stalls

Issue: [#4119](https://github.com/cabaletta/baritone/issues/4119).

- Status: `[ ] historical verification`; related to T16/T17.
- Owns: collision/passability versus target breakability.
- Work: test tall grass/no-collision target conditions; ensure a break target cannot be treated as both pass-through and permanently pending.
- Done when: task completes/advances and no repeated partial break remains.

## T52 — #3975: gets stuck inside a block after mining it

Issue: [#3975](https://github.com/cabaletta/baritone/issues/3975).

- Status: `[ ] historical verification`; related to T28.
- Owns: post-break collision update and stuck escape.
- Work: simulate delayed world update after a break and verify safe retreat/replan before entering the vacated space.
- Done when: no self-intersection loop, including under delayed server updates.

## T53 — #3192: crash near bedrock wall edge

Issue: [#3192](https://github.com/cabaletta/baritone/issues/3192).

- Status: `[ ] historical verification`.
- Owns: world-boundary movement/collision calculations.
- Work: reproduce at the reported bedrock-wall geometry and audit all neighbor/shape accesses for out-of-range coordinates.
- Done when: pathing safely rejects invalid edges rather than crashing.

## T54 — #2248: pathing NPE

Issue: [#2248](https://github.com/cabaletta/baritone/issues/2248).

- Status: `[ ] unverified`.
- Owns: pathing null-state handling.
- Work: extract the current stack trace and minimal command; eliminate the null state at its owner instead of catching it at command level.
- Done when: original scenario succeeds or returns a specific safe failure with a regression.

## T55 — #3055: server-only unhandled exception while mining

Issue: [#3055](https://github.com/cabaletta/baritone/issues/3055).

- Status: `[ ] historical verification`; may overlap registry/server-authority tasks.
- Owns: client/server world assumptions in mining.
- Work: reproduce in singleplayer and a controlled server; use the stack trace to classify command parsing, registry resolution, or server desync.
- Done when: documented duplicate or covered server-specific fix.

## T56 — #2977: 1.16.5 mine error and goto crash

Issue: [#2977](https://github.com/cabaletta/baritone/issues/2977).

- Status: `[ ] historical verification`.
- Owns: cross-version command compatibility.
- Work: audit later source history for the root cause; reproduce only if the fork still supports the affected version.
- Done when: marked already fixed/unsupported with evidence, or fixed in the maintained compatibility path.

## T57 — #3251: mining should stop cleanly on failure

Issue: [#3251](https://github.com/cabaletta/baritone/issues/3251).

- Status: `[ ] historical verification`; related to T16/T28.
- Owns: mine failure policy.
- Work: define bounded retry thresholds and terminal reasons for unreachable, protected, and desynced targets.
- Done when: mine neither silently loops nor abandons a transiently delayed target.

## T58 — #456: builder gets stuck in a loop

Issue: [#456](https://github.com/cabaletta/baritone/issues/456).

- Status: `[ ] historical verification`; compare with T22–T24.
- Owns: builder loop classification.
- Work: acquire/minimize the original schematic and confirm whether current churn/reroute handling resolves it.
- Done when: evidence ties it to an existing fix or a new targeted regression.

## T59 — #882: needless pillar/block placement path loop

Issue: [#882](https://github.com/cabaletta/baritone/issues/882).

- Status: `[ ] historical verification`.
- Owns: movement placement cost/validity.
- Work: recreate the geometry; ensure a planned scaffold is not immediately invalidated by the next movement and that lower-cost legal movement wins.
- Done when: no needless pillar loop and path cost remains admissible.

## T60 — #572: non-solid objects treated as solid

Issue: [#572](https://github.com/cabaletta/baritone/issues/572).

- Status: `[ ] historical verification`; related to T43/T51.
- Owns: `MovementHelper` collision classification.
- Work: table-test representative non-solid blocks and collision shapes against actual Minecraft passability.
- Done when: planner no longer avoids open space as solid and does not walk through truly blocking shapes.

## Shared acceptance checklist

- [ ] The official issue is still open and its present comments were checked before starting.
- [ ] Reproduction classification was recorded before any code modification.
- [ ] A regression test or retained minimal manual fixture covers the confirmed root cause.
- [ ] `./gradlew test` and the affected loader build pass.
- [ ] The task section records commit, verification evidence, and upstream disposition.
