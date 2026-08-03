# 1.20.1 restock diagnosis

## 1. Best hypothesis

The highest-confidence explanation is a test-fixture version mismatch, not a
`RestockProcess` production regression: `RestockFromBoxScenario` writes the
shulker's item entry with the post-1.20.5 key `count`, while Minecraft 1.20.1
expects the legacy byte field `Count`. The box block therefore stages
successfully but does not reliably contain 64 usable white-concrete items.

This is falsifiable. If a 1.20.1 run records the live box contents immediately
before the first restock attempt and the box contains exactly 64 white
concrete, this hypothesis is false. If the item is absent, has a zero/non-64
count, or the player receives no box supply, it is confirmed.

Confidence: medium-high (about 0.8). The numerical failure is unusually
specific, but the supplied report does contain one observation that does not
fit the strictest version of this hypothesis; see section 3.

The format boundary is documented in the [pre-1.20.5 item format](https://theminecraftwiki.com/wiki/item-format/before-1205/)
and in [Mojang's 24w09a format-change notes](https://www.minecraft.net/es-es/article/minecraft-snapshot-24w09a):
the old form uses `Count`, while the newer form uses lowercase `count`.

## 2. Evidence

### Scenario and report

- `src/main/java/baritone/testing/scenario/RestockFromBoxScenario.java:46`
  gives the player 32 blocks.
- `src/main/java/baritone/testing/scenario/RestockFromBoxScenario.java:80-85`
  constructs the shulker entry as:

  ```text
  {id:"minecraft:white_concrete",count:64,Slot:0b}
  ```

  This is the modern item syntax on a 1.20.1 target.
- `RestockFromBoxScenario.java:89-90` only checks that the block at the box
  position is a `ShulkerBoxBlock`; it does not check the block entity's item
  count.
- `RestockFromBoxScenario.java:101-104` records “holding 64” as a hardcoded
  note. That note is not an observation of the box inventory.
- The report says the ring needs 48 blocks but the builder stops with 16
  missing. `48 - 32 = 16`, exactly matching a run in which the initial grant
  was consumed and no usable box supply was added.
- The same scenario source is present on the canonical modern branch, where
  lowercase `count` is appropriate; that explains why the same scenario can
  pass there while failing on 1.20.1.

### Production-path comparison

The port's restock path does read the live menu before transferring anything:

- `src/main/java/baritone/process/RestockProcess.java:737-755` reads the
  container, updates the box contents, and refuses a fetch when the requested
  item is not present.
- `RestockProcess.java:546-560` excludes an already-indexed box whose recorded
  count for the wanted item is zero.
- `RestockProcess.java:794-842` scans the container slots and uses quick-move;
  it stops only after the player's count reaches the computed fetch target or
  after the container has no matching stack.
- `src/main/java/baritone/process/BuilderProcess.java:1486-1489` keeps a
  requested schematic block. White concrete is therefore not a junk item that
  should be discarded during this scenario.

The relevant source differences against `shulker-restock` are API adaptations,
plus the already documented shim differences:

```text
Canonical RestockProcess.java:920-923:
  stack.get(DataComponents.FOOD) != null
  stack.get(DataComponents.EQUIPPABLE) != null
  stack.get(DataComponents.TOOL) != null
  stack.get(DataComponents.WEAPON) != null

1.20.1 RestockProcess.java:921-924:
  isPlayerValuable(stack.getItem())

Canonical InventoryBehavior.java:152:
  bestToolAgainst(Blocks.STONE)

1.20.1 InventoryBehavior.java:152:
  bestToolAgainst(Blocks.STONE, PickaxeItem.class)
```

Those differences are real, but they do not explain this inventory: the
scenario starts with `clear @s` and gives only white concrete. No tool, food,
weapon, wearable item, or shears is involved. The port's `.items` access is the
1.20.1 main-inventory list, the direct legacy equivalent of the canonical
`getNonEquipmentItems()` accessor. For white concrete, the max-stack-size
adaptation also remains 64 in both versions.

## 3. Evidence against, and what remains open

The report notes restock control at time zero and again at about one second,
with one block placed by the latter note. That is evidence that the restock
process became active; it is not evidence that the box held 64 items or that a
quick-move transferred any items. If indexing had read the malformed entry as
an indexed empty box, `findCandidates` would normally exclude it. This leaves
three unresolved variants:

1. The legacy loader treats the malformed entry as a non-64 stack rather than
   a fully empty entry, so the box remains eligible in some path.
2. The box was left unindexed and tried speculatively, then yielded no usable
   supply.
3. The fixture is valid after all, and the 1.20.1 click/prediction path failed
   to apply or account for the quick-move.

The supplied evidence does not distinguish these. There are no live box-NBT,
container-count, player-count, or packet/menu-state observations in the JSON.
Per the task constraints, no build, game, or in-game suite was run.

The following alternatives are lower confidence:

- The `isJunk` component-to-class shim is not implicated by the scenario's
  contents.
- The inventory accessor is unlikely to be wrong: the target's `Inventory.items`
  is the ordinary main-inventory list, and the same accessor is used by the
  scenario's count helper.
- The dynamic `containerSlotCount` logic does not hardcode a modern slot
  layout; it finds the boundary at the player's inventory. A vanilla shulker
  menu should therefore expose the expected container slots. Runtime click
  semantics remain unverified, however.

## 4. One observation that settles it

Capture one before/after pair at the first mid-build restock attempt:

```text
(white-concrete count in the live shulker, white-concrete count in player inventory)
```

Record it immediately before the first quick-move and immediately after the
settle tick. This can be done with a temporary scenario note or a one-run
`/data get block ... Items` plus player-inventory observation.

- Box count absent/zero/non-64 before the click: the fixture hypothesis is
  confirmed.
- Box count 64 before the click and unchanged, with no player-count increase:
  the fixture is exonerated and the 1.20.1 container-click/prediction path is
  the next target.
- Box count 64 before the click and player count increases by 64, but the
  builder still stops: the restock transfer worked and the bug is in post-fetch
  accounting or builder resumption.

## 5. Suggested fix (not applied)

For the 1.20.1 branch, use the legacy item-count field in this fixture. If the
scenario source is shared across modern and legacy branches, make this one
version-specific rather than changing the shared modern fixture blindly.

```diff
diff --git a/src/main/java/baritone/testing/scenario/RestockFromBoxScenario.java b/src/main/java/baritone/testing/scenario/RestockFromBoxScenario.java
--- a/src/main/java/baritone/testing/scenario/RestockFromBoxScenario.java
+++ b/src/main/java/baritone/testing/scenario/RestockFromBoxScenario.java
@@
-                "minecraft:shulker_box[facing=up]{Items:[{id:\"" + MATERIAL_ID + "\",count:64,Slot:0b}]}");
+                "minecraft:shulker_box[facing=up]{Items:[{id:\"" + MATERIAL_ID + "\",Count:64b,Slot:0b}]}");
```

The substantive change is `count:64` to `Count:64b`. A follow-up harden-
ing change should make `suppliesStaged` assert the actual container count, so a
future NBT-format mismatch cannot be reported as a successful staging step.
