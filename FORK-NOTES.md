# Continuo notes

Continuo is a private fork of [cabaletta/baritone](https://github.com/cabaletta/baritone), branched from `26.1`
(Minecraft 26.1.2). Everything below is additional to upstream.

The headline feature is **automatic restocking**: `#build` can fetch materials from shulker boxes
you've registered near the build site instead of stalling when it runs out. Along the way a few
upstream bugs that made long builds painful are also fixed.

## What Continuo is trying to be

Three goals, in priority order. They are listed here so a change can be judged against them.

1. **Autonomy over long jobs.** A build or a mine should survive running out of materials, filling
   its inventory, or being attacked, without a human watching it. Restocking, unloading trips and
   sheltering all exist for this.
2. **Correctness on the things that stall.** Upstream's builder/backfill conflicts, placement loops
   and orientation handling are what turn a long job into a babysitting exercise. Every fix here is
   expected to arrive with a test that reproduces it — see §7.
3. **Performance.** Baritone spends real CPU per tick on pathfinding, chunk scanning and placement
   search, and the cost shows up as stutter on exactly the long jobs goal 1 is about. Two rules:
   *measure before and after*, and *treat a slowdown as a defect*. The harness makes this concrete —
   it reports per-scenario tick counts and suite wall clock, so a regression is visible rather than
   inferred. It has already earned its keep: removing one spurious stand-position constraint took
   the curated suite from 427s to 203s, which no amount of code reading would have revealed.
   `UPSTREAM_BUG_BACKLOG.md` T50 (render-distance scanner lag) and T49 (clear-area memory
   exhaustion) are the standing entries under this goal.

Non-goals: combat, anything that fakes a server-side effect (see the packet rule in the repo
conventions), and supporting more than one Minecraft version at a time.

---

## 1. Shulker box restocking

### Commands

| Command | Effect |
|---|---|
| `#addbox` | Register the shulker box you're looking at |
| `#addbox <radius>` | Register every shulker box within `<radius>` blocks (loaded chunks only) |
| `#addbox <x> <y> <z>` | Register the box at a position |
| `#removebox` / `#removebox <x> <y> <z>` | Deregister a box |
| `#listboxes` | List registered boxes, nearest first, with last-known contents |
| `#indexboxes` | Visit boxes whose contents are unknown and record them |
| `#indexboxes all` | Re-check every registered box |

A single numeric argument to `#addbox` means radius; a coordinate always needs all three, so there
is no ambiguity.

### How it works

When `BuilderProcess` finds it has nothing it can place or break, it hands the list of materials
it's short of to `RestockProcess`. That process walks to a registered box, opens it, takes what's
needed, closes it, and hands control back.

**All container access uses genuine client→server packets**, so the server's own range check on
opening a container applies exactly as it would for a human:

- Opening forces a real right-click through `InputOverrideHandler` → `BlockPlaceHelper` →
  `MultiPlayerGameMode#useItemOn`, gated by `RotationUtils.reachable(...)` against
  `blockReachDistance`.
- Transfers are `ContainerInput.QUICK_MOVE` via `IPlayerController#windowClick` →
  `MultiPlayerGameMode#handleContainerInput`, i.e. real `ServerboundContainerClickPacket`s.
- Closing dismisses the container screen, sending a real `ServerboundContainerClosePacket`.

Nothing is injected into the client's inventory behind the server's back.

**Reading contents correctly.** Two distinct signals are involved, and conflating them is the main
trap: `containerMenu` changes as soon as `ClientboundOpenScreenPacket` lands, but every slot is
still empty at that instant. Contents are only readable once
`ClientboundContainerSetContentPacket` arrives. `ContainerInteractionBehavior` tracks both via the
existing `PacketEvent` / `onReceivePacket` hook — no new mixin — and reading is gated on the
second.

**Box selection** is index-first, verify-live. Contents recorded from a previous visit pick a
plausible box; on arrival the *live* slots decide. If the box turns out not to have the material,
its index is corrected to reality and the next candidate is tried without returning to the build.

**Running out** no longer stalls the build. A material no registered box can supply is marked
unobtainable, and positions needing it stop counting as incorrect in `fullRecalc` /
`recalcNearby`. The builder therefore plans around them automatically and finishes via the normal
`"Done building"` path once only unobtainable work remains. If the shortage isn't a materials
problem at all, upstream's pause behaviour is preserved unchanged.

**Missing boxes are flagged, not deleted.** An unloaded chunk is indistinguishable from a broken
box by position lookup, so the check only runs when the chunk is genuinely loaded, and a box that's
gone is marked `MISSING` in `#listboxes` rather than removed. Cleaning up is a manual
`#removebox`.

**Junk disposal.** While at a box, if free inventory slots drop below a threshold, blocks the
schematic has no use for are deposited *into the box* (never dropped). Deliberately narrow: only
block items, only ones absent from the schematic's palette, never anything on
`acceptableThrowawayItems`, never shulker boxes. Tools, weapons, armour, food and all non-block
items are always kept. Schematics over 8M blocks skip the palette scan and treat everything as
wanted rather than guess.

**Unloading trips (`shulkerDump`) are capacity-aware.** When the inventory fills during a build or
mine, a dedicated trip is made to unload. Two things make that trip worth the walk:

- *The index picks the box.* Candidates are scored by `IRestockBox#estimatedFreeSlots()` — derived
  from the recorded contents, assuming everything is packed into as few stacks as it will go —
  against how many stacks we are actually carrying. A box that can swallow the whole load wins,
  then the box that takes the most, with distance only as the tie-break. Walking to the nearest box
  regardless of what the index says is in it is how a trip ends up putting two stacks into an
  almost-full box. The estimate is a hint, in the same sense as the rest of the index: the live
  container still decides on arrival, and a deposit re-indexes the box it just filled, so the next
  trip already knows it has no room.
- *It unloads everything, not a token amount.* The trip continues to the next box while rubble
  remains and `shulkerDumpMaxBoxesPerTrip` allows, rather than stopping the moment
  `shulkerDumpWhenFreeSlotsBelow` is satisfied again — two free slots was enough to end a trip and
  nowhere near enough to keep working. That setting is now purely the *trigger* for leaving the
  work. Hitting the box cap is logged rather than passed off as "the depot is full".

What we're carrying is surveyed *before* setting off, using the same junk rule the deposit itself
uses, so a trip is never started only to discover there was nothing to unload.

### Storage

Registrations persist per world **and per dimension**, alongside waypoints, at
`baritone/<server>/<dimension>/restock/boxes.mp4`. Same format conventions as `WaypointCollection`
(magic header, `DataOutputStream`). Items are stored by registry key rather than numeric id, so the
file survives Minecraft updates; entries that no longer resolve are dropped on load rather than
failing the file.

### Settings

| Setting | Default | Meaning |
|---|---|---|
| `restockFromBoxes` | `true` | Master switch. Off = exact upstream behaviour |
| `restockMaxDistance` | `768` | Ignore boxes further than this |
| `restockExtraStacks` | `1` | Extra stacks to grab beyond the immediate shortfall |
| `restockOpenTimeoutTicks` | `100` | Ticks to wait for a container to open / sync |
| `restockIndexBeforeBuild` | `true` | Index unknown boxes before starting a build |
| `restockDumpJunk` | `true` | Deposit unwanted blocks while at a box |
| `restockDumpWhenFreeSlotsBelow` | `4` | Only dump once free slots drop below this |
| `shulkerDump` | `false` | Make a dedicated trip to unload when the inventory fills |
| `shulkerDumpWhenFreeSlotsBelow` | `2` | Free slots that trigger that trip (not what ends it) |
| `shulkerDumpKeepThrowawayStacks` | `1` | Stacks of scaffolding blocks held back when unloading |
| `shulkerDumpMaxBoxesPerTrip` | `4` | Boxes one unload trip may open before going back to work |

### Requires `allowInventory`

Restocked items land in the main inventory, and the builder can only place from the hotbar. With
`allowInventory false` (the upstream default) it will fetch materials and then be unable to use
them. A one-time warning is logged when this is detected. Set `#set allowInventory true`.

---

## 2. Sheltering from mobs

Nothing in upstream Baritone reacts to the player being hurt — the only mob awareness is the
pathfinder's `avoidance` cost multiplier, which is off by default and never looks at damage. A long
`#sel cleararea` out in the open in survival therefore carries on regardless while a zombie beats on
you. `shelterOnAttack` (off by default) adds the missing reaction.

**Detecting it.** `ThreatBehavior` watches `hurtTime` rise and attributes each hit through
`getLastDamageSource()`. Only hostiles count (`Enemy`); fall damage, lava and drowning are things
running away makes worse, and they are exactly what a naive health-drop check fires on. It takes
`shelterMinHits` hits inside `shelterThreatMemoryTicks` to trigger, so one arrow on the way past
isn't enough. This is a *behavior* rather than process state because processes only tick while in
control, and every hit worth reacting to lands while the builder or miner is driving.

**Reacting to it.** `ShelterProcess` (priority `6.0`, temporary) is offered control by the builder
and miner through `requestShelter(worthKeeping)`, the same shape as `requestDeposit` — so the
process that knows what its materials are stays the one that decides, and sheltering only ever
interrupts Baritone's own work, never someone playing by hand. It then:

1. walks to the nearest registered shulker box, which is where a base tends to be;
2. unloads into it, by handing off to `RestockProcess` — it sits below us in priority, so returning
   `DEFER` is all it takes, and none of the container logic is duplicated;
3. if it's night or thundering, and beds aren't bombs in this dimension, scans for a bed within
   `shelterBedSearchRadius` (same chunk scanner `#addbox <radius>` uses, both halves normalised to
   the head), walks to it, and enters it with a genuine forced right-click;
4. on refusal — "there are monsters nearby" is normal, not exceptional — goes *back* to the box,
   waits `shelterRetryDelayTicks`, and tries again, up to `shelterMaxSleepAttempts` times;
5. failing all that, waits at the box until nothing has hit it for `shelterThreatMemoryTicks`, or
   `shelterMaxWaitTicks` elapses — standing still while something still reaches you is no safer
   than working.

The refusal is detected by timeout, not by parsing the chat message: servers rewrite it, and it is
not the only reason entering a bed can silently fail.

`isTemporary()` is `true` for the same reason `RestockProcess`'s is — the interrupted build must
survive being preempted.

### Settings

| Setting | Default | Meaning |
|---|---|---|
| `shelterOnAttack` | `false` | Master switch. Off = exact previous behaviour |
| `shelterMinHits` | `2` | Hostile hits within the memory window before retreating |
| `shelterThreatMemoryTicks` | `200` | How long a hit counts as "under attack" |
| `shelterUnloadOnRetreat` | `true` | Unload into the box we retreated to |
| `shelterSleepInBeds` | `true` | Look for a bed once retreated |
| `shelterBedSearchRadius` | `64` | Blocks; loaded chunks only |
| `shelterMaxSleepAttempts` | `5` | Bed attempts before settling for waiting |
| `shelterRetryDelayTicks` | `100` | Wait at the box between bed attempts |
| `shelterMaxWaitTicks` | `1200` | Cap on waiting it out before going back to work |
| `shelterRetreatTimeoutTicks` | `100` | Ticks without moving before a walk counts as stuck |

---

## 2a. `itemSaver` actually saves items

Upstream ships `itemSaver` / `itemSaverThreshold`, whose javadoc reads *"Stop using tools just before
they are going to break."* Upstream does not do that. It consults the threshold in exactly two places
(`ToolSet#getBestSlot`, `InventoryBehavior#bestToolAgainst`) and both only **filter which slot gets
selected**. Nothing stops the swing.

That leaves three ways to break the tool you asked it to protect:

- Two `CLICK_LEFT` sites never select a tool at all — `MovementTraverse`'s "something in the way"
  branch and `MovementPillar`'s break-above — so they swing whatever is held.
- `switchToBestToolFor` is gated on `autoTool && !assumeExternalAutoTool`; with either set against
  it, the filter never runs.
- `getBestSlot` initialises `best = 0` and skips with a bare `continue`, so when every hotbar slot
  holds a spent tool it returns slot 0 and breaks it.

Tenor enforces it in `BlockBreakHelper#tick` instead. **Every** block break passes through that one
method — the eight places that force `CLICK_LEFT` only raise a flag — so a single guard there covers
all of them and cannot be bypassed by `autoTool`. It mirrors the neighbouring `isUsingItem`
suppression exactly, including the throttled report, for the same reason: a bot that has silently
decided not to mine looks identical to one that has finished.

Suppression alone would only convert a broken tool into a stalled job, so mining and building
escalate. `IRestockProcess#requestTool(Item, Predicate)` fetches a replacement from a registered
box — it is keyed by `Item` rather than `BlockState` because a pickaxe has no block form, and reuses
the whole box-walking state machine since everything below `requestRestock`'s first few lines was
already item-keyed. If no box can supply one, the job stops and says why.

**This is a behaviour change to an inherited setting, not a new one.** `itemSaver true` now means
"walk to a box for a fresh pickaxe, or stop the job", where upstream meant "pick a different slot".
It stays `false` by default, so the master switch still reproduces upstream. Deliberately *not* done:
a disconnect-on-exhaustion escalation (considered and rejected — too surprising for a setting named
this), armour, and the elytra, which keeps its own separate `elytraMinimumDurability` machinery
because its behaviour is swap-and-land rather than skip.

`ToolSet#isSpent(int, int, int)` is split out as pure arithmetic so it is testable without a
Minecraft bootstrap (`ToolSetTest`); `isBlockedByItemSaver` answers "is the rule costing us speed
right now", which catches the quiet failure where a spare empty hotbar slot means the bot chews
stone barehanded at a hundredth speed instead of standing still.

---

## 2b. Auto-eat

`EatBehavior` eats when hunger drops to `autoEatFoodLevel` (default 14) and there is food on the
hotbar. It exists for the same reason as sheltering: a long job should not end because nobody was
watching the food bar.

The awkward part is that vanilla only keeps an item in use while the use key is held — release it
and the client tells the server to cancel, so the meal is thrown away having fed nothing, because
the food is actually consumed server-side in `LivingEntity#completeUsingItem`. So the behaviour
starts the use through the player controller like any other right click and then **holds the key
down itself for the whole meal**.

That is only safe because of tick ordering: Baritone's tick event fires before
`Minecraft#handleKeybinds`, so the first tick on which the use is seen to have ended is also a tick
where the key is released before the game reads it. The game therefore never observes the use key
held with nothing in use — the state that would right-click whatever is under the crosshair.
`EatBehavior` is registered **last**, after the processes, so the slot it selects is the one the
player tick sees; otherwise the builder reselecting its material every tick would eat the schematic.

`BlockBreakHelper#tick` returns early while an item is in use, so mining waits for the meal rather
than cancelling it by switching slots mid-bite (see the comment there; it is the same interaction
that DEFECTS.md tracks as M4).

| Setting | Default | Notes |
|---|---|---|
| `autoEat` | `true` | The one piece of Tenor behaviour that is **not** off by default |
| `autoEatFoodLevel` | `14` | Hunger points at or below which it eats |
| `autoEatExclude` | poison/golden foods | Never eaten: the ones that hurt you and the ones you're saving |

---

## 3. Upstream bug fixes

### Builder filled in its own path

`searchForPlacables` places a block at any nearby position the schematic wants and which is
currently replaceable, with no awareness of the path being walked. Descending through your own
build meant: movement breaks a block → builder immediately replaces it → movement breaks it again,
forever. Reported as "it breaks a block below it and instantly replaces it".

Now skips positions the current path needs open, checking `toBreakAll` over the next 10 movements.
Positions aren't abandoned, just deferred until you're no longer walking through them.

> This guard is deliberately **not** applied in `assemble()`. Those are goals to path towards, not
> immediate placements, and filtering there can empty the placement list entirely while a path runs
> through the build — which reads as "nothing is placeable", skips the restock hook, and wrongly
> pauses the build.

### Backfill fought the builder

`BackfillProcess` refilled holes inside an active build with rubble; the builder then broke the
rubble because it wasn't the block the schematic asked for. New
`IBuilderProcess#managesPosition(BlockPos)` marks everything inside the active schematic's bounds
as the builder's alone, and backfill neither records nor fills those positions.

### Backfill refilled holes the path still needed

`partOfCurrentMovement` only protected the movement being executed *right now*, so a hole dug for a
movement a couple of steps ahead was filled back in and had to be dug out again. Now looks ahead 10
movements.

### Backfill spent build materials

Backfill placed whatever throwaway block was to hand, which could be your build material. New
`backfillBlocks` setting restricts it to rubble — cobblestone, stone, andesite, diorite, granite,
tuff, deepslate, cobbled deepslate, dirt, gravel, netherrack. Implemented via a throwaway
restriction on `InventoryBehavior` that is set and cleared within a single tick.

Note `backfill` force-disables itself when `allowParkour` is `true`; that's upstream behaviour.

### Water cost exactly as much as dry land

`CalculationContext` works out how far the player's enchantments take them towards dry-land speed in
water, and initialised that figure to **1.0** — fully efficient — overwriting it only if a depth
strider enchantment was found. Vanilla registers `water_movement_efficiency` as a `RangedAttribute`
with default `0` and maximum `1`, so 1.0 is the *enchanted* value. Every player without depth
strider, which is nearly all of them, was modelled as moving through water at full walking speed:

```java
waterWalkSpeed = WALK_ONE_IN_WATER_COST * (1 - 1.0) + WALK_ONE_BLOCK_COST * 1.0  // == walking
```

So the pathfinder saw no difference between swimming and walking, and would cross a pool rather than
walk around it because the two genuinely cost the same. This is the cause of "it loves slowly
bouncing through water instead of taking the much faster side route". Fixed by initialising to `0`.

Found by the `water-detour` scenario in §7, and worth noting *how*: the scenario was run at
`waterCostMultiplier` 2, 8 and 30 in one suite, and all three produced byte-identical paths and the
same 220 nodes considered. A setting that changes nothing at 30x is not a setting that needs tuning,
it is a setting whose input is being multiplied by zero. The three-variant sweep is what turned an
inconclusive failure into a one-line fix.

### Water was priced as if you could walk through it

`WALK_ONE_IN_WATER_COST` is `20 / 2.2` — the speed of walking along the bottom of a pool. That is
right for a straight line on a flat floor and optimistic about everything else: at the surface the
player bobs, entering and leaving costs momentum, and none of it can be sprinted. At roughly twice
the cost of walking, a pool reads as barely worse than a detour of the same length, so Baritone
swims across things it could have walked around faster. Observed as it "slowly bouncing through
water instead of taking the much faster side route".

New `waterCostMultiplier` (default `2.0`) scales it. At the default a block of water costs about
four blocks of walking, so it will go around anything it can get around in less than four times the
distance. `1.0` is exactly upstream.

The multiplier applies only to the portion of the cost actually spent swimming — the interpolation
towards walking speed that depth strider provides is left alone, because a player who really does
move at walking speed through water is not bobbing at the surface and should not be charged as
though they were. Nonsense values (zero, negative, non-finite) fall back to `1.0` rather than being
believed: a negative edge cost is not a cheap path, it is a search that does not terminate.

Covered by `ActionCostsWaterTest`, and by the `water-detour` scenario in §7, which is sized so that
upstream's costs pick the swim and the new default picks the detour.

### Builder had no loop detection

The builder reassembles its goal set every tick and returns
`FORCE_REVALIDATE_GOAL_AND_PATH`, so an unreachable target can be re-picked indefinitely while it
stands still recalculating. Upstream's `blacklistClosestOnFailure` only covers `GetToBlockProcess`
and `MineProcess`, not the builder.

New `builderMaxReroutes` (default 5): if the chosen goal changes that many ticks in a row, stop
re-planning and let the current path run for `builderRerouteCommitTicks` (default 100), then
resume normal planning. The commit is temporary on purpose — pinning the goal permanently would
stop the builder reacting to anything. Set `builderMaxReroutes` to 0 to disable.

This detects goal *churn*. A stable goal that simply can't be reached won't trip it.

---

## 4. New/changed files

**New:**

```
src/api/java/baritone/api/cache/IRestockBox.java
src/api/java/baritone/api/cache/IRestockBoxCollection.java
src/api/java/baritone/api/process/IRestockProcess.java
src/api/java/baritone/api/process/IShelterProcess.java
src/main/java/baritone/behavior/ContainerInteractionBehavior.java
src/main/java/baritone/behavior/EatBehavior.java
src/main/java/baritone/behavior/ThreatBehavior.java
src/main/java/baritone/cache/RestockBox.java
src/main/java/baritone/cache/RestockBoxCollection.java
src/main/java/baritone/command/defaults/RestockBoxCommand.java
src/main/java/baritone/process/RestockProcess.java
src/main/java/baritone/process/ShelterProcess.java
```

**Modified:** `Settings.java`, `IBaritone.java`, `IWorldData.java`, `IBuilderProcess.java`,
`Baritone.java`, `WorldData.java`, `DefaultCommands.java`, `InventoryBehavior.java`,
`BackfillProcess.java`, `BuilderProcess.java`, `MineProcess.java`, `ToolSet.java`,
`BlockBreakHelper.java`.

`RestockProcess` must be registered **before** `BuilderProcess` in `Baritone.java`:
`PathingControlManager#registerProcess` calls `onLostControl()` immediately, and
`BuilderProcess#onLostControl` asks the restock process to reset. Accessors are null-guarded too,
but the ordering comment should stay.

`RestockProcess.isTemporary()` **must** remain `true`. `BuilderProcess#onLostControl` nulls the
schematic, so a non-temporary process taking control would destroy the in-progress build. The same
applies to `ShelterProcess`, which outranks it.

---

## 5. Building

```
./gradlew build
```

Output in `dist/`. The release tasks use a JDK's `jmods/` when present, or extract the required
modules from its runtime image when they are packaged separately.

**Never overwrite the jar while Minecraft is running.** Fabric loads classes lazily from it all
session; replacing the file mid-run causes `ZipException: invalid LOC header` and a
`Network Protocol Error` disconnect that looks like a server problem but isn't.

---

## 6. Testing status

The restocking path has been exercised in game on a real server: registering boxes, indexing,
pathing, opening, transferring, stale-index fallback to another box, and resuming the build all
work. The builder/backfill fixes have been confirmed over repeated descents through a solid
structure.

Less exercised: junk disposal, `#indexboxes all`, the reroute clamp, disconnect mid-transfer, and
the full-inventory path. There are no automated tests — Baritone's suite doesn't cover processes.

**Not yet exercised in game at all:** the capacity-aware unload trip (§1) and everything in §2.
Both compile and both are off by default, but neither has been run against a real server. Things to
watch for on first use:

- whether `estimatedFreeSlots()` is pessimistic enough in practice — a box holding many partial
  stacks of the same item reads as emptier than it is, which would send us to a box that then
  refuses the load. The `freed <= 0` check catches that and moves on, but it costs a walk;
- whether `isBrightOutside()`/`isThundering()` matches the server's own view of when sleeping is
  allowed. A mismatch only costs a wasted walk to a bed, since a refusal is detected by timeout
  regardless;
- whether `hasCeiling()`/`hasFixedTime()` is an adequate stand-in for the `bedWorks` flag that no
  longer exists on `DimensionType`. Getting this wrong means a bed explodes, so it errs towards not
  trying;
- right-click hygiene after a refused bed — the same failure mode as commit `85bc8236`.

---

## 7. In-game test harness

`#testing` runs scenarios against a live world and writes a report. It exists because the things
Continuo changes — restocking, sheltering, builder/backfill arbitration — are exactly the things
the JUnit suite cannot reach: they only mean anything with a server, a world and a process holding
control for several thousand ticks.

### Running it

```
#testing list                  list the scenarios
#testing all                   run all of them, in order
#testing <name> [<name> ...]   run specific ones
#testing cancel                stop
#testing autorun               run the whole suite on next world join, then quit the game
```

### What gates what

Two gates, and the split is deliberate because one of them cannot be automated.

**CI** (`.github/workflows/build.yml`) runs the unit suite and compiles all four loaders on every
push and pull request. It is fast and it is the whole of what a machine can check unattended. A
green tick there says the code compiles and the pure logic holds; it says nothing about whether the
builder still builds.

**The in-game suite is a manual gate**, run before a release or a risky merge, because it needs a
real client, a world, and several thousand ticks of sustained control:

```bash
python3 scripts/testing/parallel_run.py -n 3 --timeout 1800 --curated \
  --jar dist/continuo-unoptimized-fabric-<version>.jar
python3 scripts/testing/diff_baseline.py            # what changed vs the committed baseline
```

`diff_baseline.py` compares against `scripts/testing/baseline.json` and reports verdict changes,
per-scenario tick changes, suite wall clock, and scenarios whose instances disagreed with each
other. It exits non-zero on a verdict regression, so it can gate a merge; paste its output into the
PR. Refresh the baseline with `--update` when a change is meant to move it, and say so in the commit
message.

Read the timing output, not just the verdicts. Performance is a goal of Continuo, and the one
regression that has actually slipped through so far was visible in wall clock (203s → 427s) while
the verdicts showed only an unrelated scenario flaking.

Singleplayer with cheats only, and it will rewrite terrain, clear your inventory and change your
gamemode. **Use a world you do not care about.** It refuses to start on a server rather than
finding out the hard way which of its commands the server allows.

Reports go to `<instance>/minecraft/baritone/testing/`: `report-<timestamp>.md` to read,
`report-<timestamp>.json` to parse, and `latest.md`/`latest.json` overwritten each run so a reader
doesn't have to guess a filename.

`#testing autorun` writes a flag file that the next world join picks up. The run deletes the flag
*before* starting — a crash mid-suite must not turn into a boot loop — then saves, leaves the world
and quits. Combined with `prismlauncher --launch <instance> --world <world>`, that makes an
unattended build → run → read-the-report loop possible.

### How a scenario works

Four phases, all tick-driven, because there is no thread to block on: **stage** the arena in
creative, **arm** by switching to survival, **run** the real process API, **assert** against world
state. Each scenario gets its own arena 512 blocks from the last, so nothing one leaves behind can
reach the next.

Two deliberate choices:

- **Staging goes through vanilla commands**, not through the integrated server's level object. The
  client can reach that object in singleplayer and writing to it would be faster, but it would mean
  arenas exist on a path no player could take — the one thing the container code in §1 is careful
  never to do. Commands also keep the harness honest about chunk loading: `/fill` refuses an
  unloaded chunk, which is why staging waits for the arena's chunks to reach the client first.
- **Verdicts come from the world, not from chat.** Baritone logs `"Done building"` when it has
  nothing left it *can* do, which includes having given up — the exact state a materials bug leaves
  it in. Scenarios count placed blocks, read the inventory and check the player's position instead.

`TestingBehavior` is a behavior, not a process, for the reason `ThreatBehavior` is one only more
so: it supervises processes. Registering it with `ProcessScheduler` would put it in competition
with the code it is observing, and the first scenario to hand control to the builder would be the
last tick the harness ran.

### Current scenarios

| Scenario | What it proves |
|---|---|
| `pathing-course` | Walls, a trench and water to a raised platform, with `allowBreak`/`allowPlace` off so it must actually navigate rather than mine through |
| `water-detour` | An 11-block pool with a dry way around it. Sized so upstream's costs pick the swim and `waterCostMultiplier`'s default picks the detour, so it holds that default honest |
| `build-schematic` | A 5x5x3 ring with materials in hand. The control case: if this fails, a restock failure afterwards means nothing |
| `restock-from-box` | The same ring starting 16 blocks short, with a stocked box registered nearby. Finishing is not sufficient — the restock process must also have taken control, or the material came from somewhere unintended |

### Known-fragile bits

- **The shulker box `/setblock` in `restock-from-box`** is the most version-sensitive line in the
  harness. Block entity item NBT has changed format before. If that scenario reports the box as
  missing, check the command against the current format before suspecting Baritone.
- **First run, 2026-07-31.** The loop worked unattended end to end — staged, ran, judged, reported,
  quit. `build-schematic` and `restock-from-box` passed; the latter is the first repeatable
  confirmation that restocking works, having previously only been checked by hand (§6).
  `pathing-course` failed on a fault in its own geometry, not in Baritone: the staircase's top step
  sat directly under the goal platform, burying it and leaving a two-block wall that cannot be
  climbed with placing disabled. An unreachable goal reads in a report as Baritone giving up, which
  is worth remembering when writing the next scenario.
- **`restock-from-box`'s first assertion was too weak to mean what it claimed.** It required only
  that the restock process take control at some point, which the pre-build indexing trip satisfies
  whether or not the builder ever runs short — it passed at `t=0s`. It now requires a trip taken
  after at least one block is placed.
- **Restock-box registrations are harness state, not arena terrain.** They persist in world data
  across scenarios and even across runs, so inheriting one makes box-touching tests order-dependent.
  `TestingBehavior` clears every registration before staging and after teardown; a scenario must
  register every box it expects to use itself.
- Scenario timeouts are generous on purpose. A budget tight enough to catch a slow run is tight
  enough to fire on a chunk load, and a suite that cries wolf gets ignored.

---

## 8. Open fork issues

### Shelter retreat has no target-distance bound

Status: **reproduced in the live session on 2026-07-31; not fixed.**

With `shelterOnAttack=true`, a player clearing around `(-367..-368, 63..67, 26..31)` was hit and
`ShelterProcess` selected the registered box at `BetterBlockPos{x=-314,y=63,z=208}` from
`(-362, 63, 26)`. The target is about 188 blocks away (`dx=48`, `dz=182`): the log said
`Under attack; retreating to the box ...` at 15:57:03 and again at 16:09:11. Breaking was allowed,
so the retreat tunnelled through unrelated trial-chamber terrain roughly 60 blocks from the work.

`shelterBedSearchRadius` does not constrain this walk; it applies only after reaching a box and
searching for a bed. `ShelterProcess#nearestBox` needs a bounded retreat policy that declines a
far box rather than treating it as shelter. The default must remain upstream-equivalent (shelter
disabled) if a new setting is needed. `shelter-retreat-distance` is the pending failing harness
scenario; do not change retreat behavior until it reproduces this safely in the arena.
