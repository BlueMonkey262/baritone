# Fork notes

Private fork of [cabaletta/baritone](https://github.com/cabaletta/baritone), branched from `26.1`
(Minecraft 26.1.2). Everything below is additional to upstream.

The headline feature is **automatic restocking**: `#build` can fetch materials from shulker boxes
you've registered near the build site instead of stalling when it runs out. Along the way a few
upstream bugs that made long builds painful are also fixed.

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

### Requires `allowInventory`

Restocked items land in the main inventory, and the builder can only place from the hotbar. With
`allowInventory false` (the upstream default) it will fetch materials and then be unable to use
them. A one-time warning is logged when this is detected. Set `#set allowInventory true`.

---

## 2. Upstream bug fixes

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

## 3. New/changed files

**New:**

```
src/api/java/baritone/api/cache/IRestockBox.java
src/api/java/baritone/api/cache/IRestockBoxCollection.java
src/api/java/baritone/api/process/IRestockProcess.java
src/main/java/baritone/behavior/ContainerInteractionBehavior.java
src/main/java/baritone/cache/RestockBox.java
src/main/java/baritone/cache/RestockBoxCollection.java
src/main/java/baritone/command/defaults/RestockBoxCommand.java
src/main/java/baritone/process/RestockProcess.java
```

**Modified:** `Settings.java`, `IBaritone.java`, `IWorldData.java`, `IBuilderProcess.java`,
`Baritone.java`, `WorldData.java`, `DefaultCommands.java`, `InventoryBehavior.java`,
`BackfillProcess.java`, `BuilderProcess.java`.

`RestockProcess` must be registered **before** `BuilderProcess` in `Baritone.java`:
`PathingControlManager#registerProcess` calls `onLostControl()` immediately, and
`BuilderProcess#onLostControl` asks the restock process to reset. Accessors are null-guarded too,
but the ordering comment should stay.

`RestockProcess.isTemporary()` **must** remain `true`. `BuilderProcess#onLostControl` nulls the
schematic, so a non-temporary process taking control would destroy the in-progress build.

---

## 4. Building

```
./gradlew build
```

Output in `dist/`. The release tasks use a JDK's `jmods/` when present, or extract the required
modules from its runtime image when they are packaged separately.

**Never overwrite the jar while Minecraft is running.** Fabric loads classes lazily from it all
session; replacing the file mid-run causes `ZipException: invalid LOC header` and a
`Network Protocol Error` disconnect that looks like a server problem but isn't.

---

## 5. Testing status

The restocking path has been exercised in game on a real server: registering boxes, indexing,
pathing, opening, transferring, stale-index fallback to another box, and resuming the build all
work. The builder/backfill fixes have been confirmed over repeated descents through a solid
structure.

Less exercised: junk disposal, `#indexboxes all`, the reroute clamp, disconnect mid-transfer, and
the full-inventory path. There are no automated tests — Baritone's suite doesn't cover processes.
