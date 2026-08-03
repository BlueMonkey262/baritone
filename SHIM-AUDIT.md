# 1.20.1 data-component shim audit

Compared the current worktree with `shulker-restock`, including the canonical
`RestockProcess#isJunk` rule and the source-side item/cache compatibility code.
This is a reading-only audit. No Gradle command, build, test, or Minecraft
process was run.

## The ten references

The canonical references are the ten `DataComponents` uses in five files. The
last column answers the required disagreement question explicitly.

| # | Reference | 1. What canonical tests | 2. What 1.20.1 tests | 3. Item disagreement or search result |
|---|---|---|---|---|
| 1 | `EatBehavior.java:209`, `FOOD` in `bestFoodSlot` | The stack's `FoodProperties` component: `stack.get(DataComponents.FOOD)`. | The registered item's `FoodProperties`: `stack.getItem().getFoodProperties()`, after `isEdible`. | No ordinary vanilla food found that differs; bread, steak, etc. follow the same registered-item food. A stack-level/custom FOOD override on an item such as bread would differ because the canonical test is stack-specific and the port is not. |
| 2 | `EatBehavior.java:222`, `FOOD` in `isEdible` | `stack` is edible exactly when `stack.get(DataComponents.FOOD) != null` (and it is non-empty). | `stack.getItem().isEdible()` (and it is non-empty). | Same vanilla result as #1; no ordinary vanilla item found. A component-overridden bread/custom food stack is a concrete out-of-tree disagreement: canonical follows the stack component, while the port follows the item class. |
| 3 | `InventoryBehavior.java:152`, `TOOL` in `bestToolAgainst` | The item’s default component map has `DataComponents.TOOL`. | `cla$$.isInstance(stack.getItem())`; the only caller passes `PickaxeItem.class`. | `minecraft:iron_axe` is a canonical TOOL item but is not a `PickaxeItem`, so the canonical search considers it and the port skips it. The same applies to shovels and hoes; the narrowing is observable when no pickaxe is available. |
| 4 | `InventoryBehavior.java:245`, `TOOL` in off-hand-safe throwaway selection | The item’s default component map has `DataComponents.TOOL`. | `item.getItem() instanceof DiggerItem`. | `minecraft:shears` is a canonical TOOL item but `ShearsItem` is not a `DiggerItem`; the canonical code may use it as the non-right-clicking main-hand fallback and the port will not. |
| 5 | `RestockProcess.java:920`, `FOOD` in `isJunk` | A stack carrying the FOOD component is never junk. | `isPlayerValuable` asks `item.isEdible()`, not the stack. | No vanilla `BlockItem` with FOOD was found; `minecraft:cake` is rejected by the later explicit `CakeBlock` check in both versions. A modded/component-overridden food `BlockItem` (for example an oak-planks item carrying FOOD) differs: canonical protects it, the port can deposit it. |
| 6 | `RestockProcess.java:921`, `EQUIPPABLE` in `isJunk` | A stack carrying the EQUIPPABLE component is never junk. | `isPlayerValuable` asks `item instanceof Equipable`. | **`minecraft:carved_pumpkin` disagrees.** It is a `BlockItem` carrying the canonical wearable semantics, but the 1.20.1 `BlockItem` is not `Equipable`; it passes the current helper and can be deposited. This is the same wearable-block hole called out in the task. |
| 7 | `RestockProcess.java:922`, `TOOL` in `isJunk` | A stack carrying the TOOL component is never junk. | `item instanceof DiggerItem`. | No vanilla `BlockItem` with TOOL was found. The current check is also structurally ineffective for ordinary vanilla items because the outer `BlockItem` gate and the `DiggerItem` class family are disjoint. A modded `BlockItem` whose only marker is TOOL is a disagreement. |
| 8 | `RestockProcess.java:923`, `WEAPON` in `isJunk` | A stack carrying the WEAPON component is never junk. | `SwordItem`, `TridentItem`, or `ProjectileWeaponItem` family checks. | No vanilla `BlockItem` with WEAPON was found. Under the outer `BlockItem` gate these class checks do not protect ordinary vanilla weapons; a modded component-only weapon `BlockItem` is a disagreement. |
| 9 | `ElytraBehavior.java:941`, `FIREWORKS` in `isFireworks` | Gets the Fireworks component and accepts only when `fw.explosions().isEmpty()`. | Reads the legacy `Fireworks` compound and rejects whenever the key `Explosions` exists, regardless of list length; a missing compound is accepted. | A `minecraft:firework_rocket` stack with `Fireworks.Explosions: []` is accepted canonically but rejected by the port. No ordinary vanilla serialized rocket found with that explicit empty key. |
| 10 | `ElytraBehavior.java:950`, `FIREWORKS` in `getFireworkBoost` | Reads `fw.flightDuration()` from the non-exploding Fireworks component. | Reads a `Flight` byte only when a legacy `Fireworks` compound and that key are present. | A bare/default `minecraft:firework_rocket` is a disagreement: the canonical item default supplies a Fireworks component with flight duration 1; the 1.20.1 stack normally has no `Fireworks` compound, so the port returns no boost duration. |

The first two FOOD substitutions are therefore equivalent for ordinary
vanilla items but not for arbitrary stack-level component overrides. The
restock substitutions are more constrained: `isJunk` first requires a
`BlockItem`, which is why the carved pumpkin is the important vanilla failure.

## Ranked divergences worth acting on

1. **High — restock can deposit a carved pumpkin.** `isPlayerValuable` must
   include the pre-component wearable-block case. This directly violates the
   stated purpose of the canonical EQUIPPABLE guard.

2. **High — inventory TOOL handling is narrower than canonical.** The port
   changes “any TOOL” to pickaxe-only in `bestToolAgainst`, and to
   `DiggerItem`-only in the off-hand fallback. At minimum this loses axes,
   shovels, hoes, and shears from the first path, and shears from the second.

3. **Medium — FIREWORKS does not preserve the canonical component defaults.**
   The explicit-empty `Explosions` list is treated as dangerous by the port,
   and a default rocket loses its canonical flight duration. The first is a
   safe false negative; the second changes boost prioritisation.

4. **Medium — the WorldData compatibility edit changes Nether cache
   semantics.** The canonical dimension key reaches `CachedChunk`, which
   applies its `y < dimension.minY() + 5` guard to both the Overworld and
   Nether IDs. In a normal Nether, the adjusted cache coordinate y=4 meets
   that guard; the port drops the key and uses `y < -59 && dimension.natural()`.
   Because Nether `natural()` is false, the same cached solid block is returned
   as Netherrack by the fallback instead of Obsidian. The port also hard-codes
   -59 for custom dimensions.

5. **Low/medium — ToolSet material tie-breaking changed.** Canonical orders
   material tags as wood, stone, iron, gold, diamond, netherite. The port uses
   `Tier.getLevel()`, where vanilla gold has level 0, diamond 3, and netherite
   4. Thus a golden pickaxe is cost 0 in the port but cost 3 canonically (and
   diamond/netherite are each one lower than their canonical priority). This
   only affects equal-speed tie-breaking, but it is not equivalent.

6. **Low, modded-content scope — component-only custom items are not
   representable by the family checks.** FOOD, TOOL, EQUIPPABLE, and WEAPON
   can be attached to an item stack or custom item without the corresponding
   1.20.1 Java superclass/interface. No such custom item exists in this
   repository, so the exact mod compatibility impact cannot be enumerated
   here.

## Suggested fixes (not applied)

These are report-only diffs. They are intentionally left uncompiled because
the task forbids builds and tests.

### Protect the vanilla wearable block

```diff
diff --git a/src/main/java/baritone/process/RestockProcess.java b/src/main/java/baritone/process/RestockProcess.java
--- a/src/main/java/baritone/process/RestockProcess.java
+++ b/src/main/java/baritone/process/RestockProcess.java
@@
 import net.minecraft.world.item.Item;
 import net.minecraft.world.item.ItemStack;
+import net.minecraft.world.item.Items;
@@
         return item.isEdible()
                 || item instanceof Equipable
+                || item == Items.CARVED_PUMPKIN
                 || item instanceof DiggerItem
                 || item instanceof SwordItem
                 || item instanceof TridentItem
                 || item instanceof ProjectileWeaponItem;
```

### Preserve the pre-component tool families in InventoryBehavior

```diff
diff --git a/src/main/java/baritone/behavior/InventoryBehavior.java b/src/main/java/baritone/behavior/InventoryBehavior.java
--- a/src/main/java/baritone/behavior/InventoryBehavior.java
+++ b/src/main/java/baritone/behavior/InventoryBehavior.java
@@
 import net.minecraft.world.item.PickaxeItem;
+import net.minecraft.world.item.ShearsItem;
@@
-        int pick = bestToolAgainst(Blocks.STONE, PickaxeItem.class);
+        int pick = bestToolAgainst(Blocks.STONE);
@@
-    private int bestToolAgainst(Block against, Class<? extends DiggerItem> cla$$) {
+    private int bestToolAgainst(Block against) {
@@
-            if (cla$$.isInstance(stack.getItem())) {
+            if (stack.getItem() instanceof DiggerItem || stack.getItem() instanceof ShearsItem) {
@@
-                if (item.isEmpty() || item.getItem() instanceof DiggerItem) {
+                if (item.isEmpty()
+                        || item.getItem() instanceof DiggerItem
+                        || item.getItem() instanceof ShearsItem) {
```

The unused `PickaxeItem` import should then be removed. This still cannot
recognise an arbitrary modded item whose only signal is a custom TOOL
component; 1.20.1 has no generic component API to substitute for that.

### Restore dimension identity through the cache

The source-level fix is to retain the existing canonical `ResourceKey<Level>`
argument through `WorldData -> CachedWorld -> CachedRegion -> CachedChunk`, use
`dimension.minY() + 5` for the bottom heuristic, and retain the canonical
`Level.OVERWORLD` / `Level.NETHER` guard:

```diff
diff --git a/src/main/java/baritone/cache/CachedChunk.java b/src/main/java/baritone/cache/CachedChunk.java
--- a/src/main/java/baritone/cache/CachedChunk.java
+++ b/src/main/java/baritone/cache/CachedChunk.java
@@
+import net.minecraft.resources.ResourceKey;
+import net.minecraft.world.level.Level;
@@
-    public final BlockState getBlock(int x, int y, int z, DimensionType dimension) {
+    public final BlockState getBlock(int x, int y, int z, DimensionType dimension,
+                                     ResourceKey<Level> dimensionId) {
@@
-            if (y < -59 && dimension.natural()) {
+            if ((dimensionId == Level.OVERWORLD || dimensionId == Level.NETHER)
+                    && y < dimension.minY() + 5) {
@@
-        return ChunkPacker.pathingTypeToBlock(type, dimension);
+        return ChunkPacker.pathingTypeToBlock(type, dimension, dimensionId);
```

The corresponding constructor/signature propagation is mechanical. The
canonical `ChunkPacker.pathingTypeToBlock(type, dimension, dimensionId)` also
avoids replacing dimension identity with heuristics such as `natural()` and
`effectsLocation()`.

### Normalize the FIREWORKS edge cases

At minimum, the legacy NBT adapter should treat an explicitly empty
`Explosions` list as safe (with the corresponding `net.minecraft.nbt.Tag`
import). Mapping the absent `Fireworks`/`Flight` fields to
the canonical default flight duration of 1 should be confirmed against the
1.20.1 firework entity before applying:

```diff
diff --git a/src/main/java/baritone/process/elytra/ElytraBehavior.java b/src/main/java/baritone/process/elytra/ElytraBehavior.java
--- a/src/main/java/baritone/process/elytra/ElytraBehavior.java
+++ b/src/main/java/baritone/process/elytra/ElytraBehavior.java
@@
         // If it has NBT data, make sure it won't cause us to explode.
         final CompoundTag compound = itemStack.getTagElement("Fireworks");
-        return compound == null || !compound.getAllKeys().contains("Explosions");
+        return compound == null
+                || !compound.contains("Explosions", Tag.TAG_LIST)
+                || compound.getList("Explosions", Tag.TAG_COMPOUND).isEmpty();
@@
         if (isFireworks(itemStack)) {
             final CompoundTag compound = itemStack.getTagElement("Fireworks");
-            if (compound != null && compound.getAllKeys().contains("Flight")) {
-                return OptionalInt.of(compound.getByte("Flight"));
-            }
+            return OptionalInt.of(compound == null || !compound.contains("Flight", Tag.TAG_BYTE)
+                    ? 1 : compound.getByte("Flight"));
         }
```

This suggested NBT diff needs a runtime/API check for the exact 1.20.1 tag
constants and firework default; it is included as a proposed fix, not as a
verified patch.

## ToolSet and WorldData compatibility notes

### ToolSet

`shulker-restock` imports `DataComponents` in `ToolSet`, but that import is
unused; there is no additional component reference there to translate. The
material-priority and enchantment changes are still behaviorally relevant:

- `getMaterialCost` changed from the canonical material-tag priority to
  `TieredItem.getTier().getLevel()`. The gold/wood and gold/diamond ordering
  examples above prove this is not a pure API rename.
- `hasSilkTouch` is a legacy `EnchantmentHelper` query in the port. No vanilla
  item disagreement was found; custom post-component enchantment holders are
  outside 1.20.1.
- `calculateSpeedVsBlock` hard-codes the vanilla Efficiency formula instead of
  scanning canonical enchantment attribute effects. No vanilla disagreement
  was found, but a canonical custom enchantment that contributes mining
  efficiency would be ignored by the port.

### WorldData and its cache chain

The port removes `ResourceKey<Level> dimensionId` from `WorldData`,
`CachedWorld`, `CachedRegion`, and `CachedChunk`. The resulting cache path is
still dimension-separated by `WorldProvider`, and the fallback block mapping
matches the three vanilla dimensions in ordinary locations. The bottom-five
Nether case above does not match, however, and the hard-coded -59 also does
not generalize to custom dimensions. This is a pathing/cache divergence, not
an item-deposit divergence.

## What remains unverified

No compilation or runtime verification was performed, by explicit task
instruction. In particular, the following remain unverified until a safe
session can compile or run a small 1.20.1 harness:

- loader/mapping resolution for the proposed `ShearsItem`, `Items.CARVED_PUMPKIN`,
  and legacy NBT constants;
- the exact 1.20.1 runtime default flight duration for a firework rocket whose
  `Fireworks` compound is absent;
- behavior of modded items that encode FOOD/TOOL/EQUIPPABLE/WEAPON only in
  component data rather than Java item families.

`SHIM-AUDIT.md` is the only file added for this task; source files were left
unchanged.
