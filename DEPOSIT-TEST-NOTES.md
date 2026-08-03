deposit-bulk-allowlist — catches an empty `depositableBulkItems` setting being ignored; the mined stone and boxed rubble prove an unload trip ran, while raw iron and flint remain; not verified in-game because the task forbids launching Minecraft.
deposit-bulk-guards — catches `worthKeeping` being bypassed for active raw-iron drops and FOOD/TOOL/WEAPON/EQUIPPABLE guards being evaluated after the bulk allowlist; the real iron-ore mine and boxed flint/rubble prove the dedicated `shulkerDump` trip ran; not verified in-game because the task forbids launching Minecraft.

Angle 3 is covered because the scenario settings map can assign a `List<Item>` directly; the
fixture names all four guarded item types in `depositableBulkItems` and checks their inventory and
box counts.

Angle 4 needs no separate scenario: `deposit-bulk-guards` sets `shulkerDump` on, disables
opportunistic `restockDumpJunk`, and triggers `requestDeposit` from `MineProcess`, so it exercises
the dedicated unload path while sharing `isJunk`.

Skipped angles:

- Angle 5 is skipped: the dedicated unload fixture uses ordinary rubble and explicit allowlisted/guarded items, but does not create a meaningful `acceptableThrowawayItems` interaction; a throwaway-only case would not add coverage of the bulk branch.
