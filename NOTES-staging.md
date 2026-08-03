# Scenario staging repair notes

The only new code change is in `RestockInventoryFixture`. Minecraft 26.1.2 maps
`hotbar.0` through `hotbar.8` to player slots 0 through 8, while
`inventory.0` through `inventory.26` starts at player slot 9. The old fixture
used `inventory.0` for the protected hotbar items and eventually emitted
`inventory.27` through `inventory.31`, which are invalid. The helper now chooses
the command slot name from the absolute player-inventory index.

## Per-scenario classification

- `dump-prefers-capacity`: oracle victim; no scenario-specific change. The
  shared dump staging assertion already verifies all 26 capacity items, and the
  `Items`/`count`/`Slot` spelling matches the known-good shulker fixture.
- `dump-spans-boxes`: oracle victim; no scenario-specific change for the same
  reason.
- `dump-max-box-limit`: oracle victim; no scenario-specific change for the same
  reason.
- `indexboxes-all-refresh`: oracle victim; its initial and replacement shulker
  payloads already use the known-good form. Container assertions now read the
  integrated server through `ScenarioInventory`.
- `itemsaver-replacement-box`: oracle victim in the reported staging result;
  `countContainerMatching` now reads the server copy, and its `item replace
  block ... container.N` plus item-component spelling were left unchanged.
- `restock-cancel-cleanup`: oracle victim; its stocked shulker uses the same
  known-good payload as `RestockFromBoxScenario`, so no staging rewrite was
  warranted.
- `restock-preserves-tools`: genuine fixture defect fixed through
  `RestockInventoryFixture`; its player item-replacement commands now target
  the intended slots and no longer emit out-of-range inventory slots. The old
  container oracle was also a contributing failure in the reported run.
- `restock-full-inventory-swap`: the same genuine shared-fixture defect and fix
  as `restock-preserves-tools`; the old container oracle also contributed.
- `restock-low-stack-item`: no new change. The current tree already contains
  two real `count:1` shulker-box entries in slots 0 and 1; the earlier packed
  `count:2` form had already been repaired. Its staging container count was
  also dependent on the old oracle.
- `restock-sync-timeout-fallback`: no new change. The current tree already uses
  the bottom-slab obstruction rather than the breakable full stone cube, and
  the stocked boxes use the known-good payload. Its container staging counts
  were also dependent on the old oracle.

## Verification

`GRADLE_USER_HOME=/tmp/scen-gradle ./gradlew :test --no-daemon` passed.

The in-game scenario suite was not run, as required by `SCEN-TASK.md`. The
Minecraft command-slot mapping was checked against the 26.1.2 `SlotRanges`
implementation, but live command acceptance and scenario verdicts remain
unverified until the human run.
