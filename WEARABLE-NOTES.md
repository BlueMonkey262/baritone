# Wearable-block guard notes

The pre-component expression used in `isPlayerValuable` is:

```java
item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof Equipable
```

`isJunk` has already established that the item is a `BlockItem`, so this remains a narrow
block-only guard. It asks about the block represented by the item, which is the important
distinction here: a `BlockItem` itself is not `Equipable`, but the block behind a wearable block
item can be.

The concrete item this catches is `minecraft:carved_pumpkin`. In the cached 1.20.1 mapped Minecraft
classes, its block is an `EquipableCarvedPumpkinBlock`, whose equipment slot is `HEAD`. A concrete
item this must not catch is `minecraft:stone`: its block is an ordinary building block and does not
implement `Equipable`.

I verified this against the actual 1.20.1 classes in the Gradle cache rather than assuming an API
shape. `Equipable.get(ItemStack)` first checks the item, then checks `BlockItem.getBlock()` for
`Equipable`; `LivingEntity.getEquipmentSlotForItem(ItemStack)` delegates to that helper. The 1.20.1
`Item` class does not provide the guessed `getEquipmentSlot(ItemStack)` route, and
`Equipable#getEquipmentSlot` is a no-argument instance method.

The same expression works unchanged on 1.20.4 and 1.21.1. Their cached mapped classes have the
same `Equipable.get(ItemStack)` BlockItem fallback and the same `EquipableCarvedPumpkinBlock`
implementation. No edits were made to those branches.
