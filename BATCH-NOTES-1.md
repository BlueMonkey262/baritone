`restock-low-stack-item` — protects stack-size-aware fetch-target arithmetic by requiring one of two stack-size-one shulker-box items to be fetched and placed; the live command NBT form is version-sensitive.

`restock-sync-timeout-fallback` — protects fallback after a first shulker cannot open/synchronise by placing it under a solid block and requiring the later box to supply the material; this uses blocked-shulker behavior as the deterministic timeout substitute and needs human validation in-game.

`restock-full-inventory-swap` — protects safe capacity recovery when every non-equipment inventory slot is occupied, requiring junk deposition, material transfer, and intact protected items; the exact 26.1.2 `item replace entity` command behavior is unverified here.

`restock-preserves-tools` — protects the junk classifier from depositing a tool, food, weapon, or helmet during a crowded restock; the scenario shares the full-inventory command fixture and has not been run in-game.

`restock-cancel-cleanup` — protects cancellation cleanup for an open restock menu and lingering movement; the verdict uses menu closure, inventory conservation, and stable player position as world-state evidence, but has not been run in-game.

Entries 21–23, 25, and 26 were already present as uncurated scenarios and were left unchanged. None of entries 21–30 is excluded by `TEST_BACKLOG.md`'s “Not yet buildable” section. The referenced `ItemSaver*` family is present in repository history but not on this worktree branch; it was read there for conventions and the independent-control pattern.
