# Wave 3 notes

`container-readable-positive-control` — the server-thread oracle returned staged white concrete=7 and absent red concrete=0; this verdict rests solely on container reads. The `/item replace block` command grammar is the flagged seven-version fixture assumption.

`container-readable-negative-control` — the real empty shulker returned non-empty slots=0 and white concrete=0 rather than “no block entity”; this verdict rests solely on container reads. The empty-shulker placement and `/item replace block` command grammar are flagged portability assumptions.

`container-exact-restock-source` — the build target was placed, source white-concrete count changed from 3 to exactly 2, and carried white returned to 0; the exact remainder is a container-read-only guard. The `/item replace block` staging command and one-block schematic are flagged fixture assumptions.

`container-exact-deposit-destination` — the target completed, destination white=0/red=1, and carried red=0; the destination assertion is the container-only distinction from a player-return pass. The 33-slot `RestockInventoryFixture` and its item-slot command syntax are flagged fixture assumptions.

`container-partial-stack-remainder` — destination red concrete measured 64 items in one slot after the four-item deposit, with no red carried; total and slot packing rest on container reads. The `/give` merge into the prefilled player slot, item-command syntax, and vanilla max stack size are flagged fixture assumptions.

`container-wrong-menu-no-transfer` — while A’s dirt menu was readable, the completed build reduced B’s white concrete from 1 to 0 and left A at 17 dirt; those source/destination counts are container-read evidence. The deliberate overlap between the indexing handoff and the builder start is a flagged packet-timing fixture assumption.

`container-deposit-timeout-no-mutation` — after the blocked candidate was visited, its white marker stayed at 1 and red deposit at 0 while the fallback received red rubble and the clear completed; the unchanged timed-out contents rest on container reads. The top-slab refusal, `restockOpenTimeoutTicks=20`, and server-side interaction timing are flagged fixture guesses.

`container-persistence-round-trip` — after `reloadFromDisk`, the same registry key, creation/index timestamps, white count=9, missing=true flag, and live white count=9 were measured; the persisted/indexed portion rests on collection and container reads. This is a cache-collection reload rather than a full Minecraft world restart, and the `/item replace block` staging grammar is flagged.

`restock-distance-filter-near-fallback` — the near box changed from white=2 to 1, the far box remained at 2, and the player never reached the far coordinates. The six-block radius versus x+10 layout and item-command staging are flagged distance/pathing fixture assumptions.

`restock-master-off-no-trip` — one of two white targets was placed, the registered box stayed at white=2, the player never reached it, and the builder stopped; these measurements distinguish the ordinary shortage from an accidental trip. The one-item shortfall and item-command box payload are flagged fixture assumptions.

`dump-whole-load-before-return` — the clear completed with the eligible rubble count carried=0 and the measured box count increased; the inherited base also records the work segment and protected inventory. The base’s fixed 33-item inventory and full-inventory trigger are flagged live-layout assumptions.

`builder-ignore-blocks-air` — the stone requested as air remained stone while the independent white-concrete neighbor was placed. The use of `buildIgnoreBlocks` with a stone floor and the one-item placement layout are flagged builder-fixture assumptions.

`builder-skip-current` — the skipped current stone remained unchanged while the neighboring concrete target was placed. The `buildSkipBlocks` list and the compact two-target schematic are flagged builder-fixture assumptions.

`mine-existing-quantity` — the mine started with requested quantity=2 already held, all three stone targets remained, cobblestone stayed at 2, and the mine process became inactive. The assumption that process startup performs the quantity check before pathing is flagged for live confirmation.

`path-right-click-arrival-off` — the player reached the chest’s goal area with `get-to` inactive and no container menu open. The empty chest, south-facing state, and goal-area radius are flagged interaction/pathing fixture assumptions.

## Wave 4 notes

`restock-index-off-speculative` — selected because the backlog’s index-off state is not registered yet; correctness is a first target placed before the player reaches the unindexed box, followed by the source changing from white=2 to 1 and carried white returning to 0.

`restock-negative-extra-stacks-clamped` — selected as the numeric-boundary companion to the existing zero-surplus case; correctness is all two white targets placed with the source reduced from white=2 to 0 and no white left carried, proving the negative value behaved as zero.

`dump-keep-throwaway-zero` — selected to cover the zero-reserve branch beside the existing one-stack reserve; correctness is the clear completing with eligible rubble absent from the player and present in the registered box while protected diamond, totem, and pickaxe counts remain unchanged.

`dump-full-depot-bounded` — selected as a live guard for fixed H9/full-capacity recovery; correctness is both clear targets becoming air, the measured 27-slot depot staying full, and nonzero rubble remaining carried after one depot visit and a stopped builder.

`shelter-unload-disabled-still-shelters` — selected to separate retreat from the optional unload handoff; correctness is hostile health loss followed by arrival at the rally box while its measured occupied-slot count remains zero.

`itemsaver-pillar-break` — selected because the movement-pillar break-above path is not covered by ordinary mining/item-saver tests; correctness is the overhead stone and the damage-50 wooden pickaxe both remaining unchanged when the pillar attempt stops.

`inventory-stationary-gate` — selected to cover the inventory/process arbitration setting; correctness is the diamond pickaxe absent from the hotbar during the approach, then present with positive damage only when the distant stone target is reached and broken.

`builder-ok-if-air` — selected as an unoccupied builder acceptance policy; correctness is the configured white target remaining air while the separate required white-concrete target becomes solid.

`builder-substitute-fallback-order` — selected to exercise ordered alternatives with a missing first item; correctness is the stone schematic position becoming dirt, with white concrete still absent from the player inventory.

`builder-ignore-properties` — selected to cover property-level comparison beyond direction; correctness is the facing-only east/straight stair staying unchanged while the east/inner-left stair is rebuilt to north/straight.

`builder-start-at-layer` — selected to add layer-offset coverage not present in the earlier builder cases; correctness is the lower stone support remaining stone while only the selected upper position becomes white concrete.

`backfill-parkour-incompatible` — selected as a direct backfill/process-handoff policy guard; correctness is the sealed gap remaining air while the player crosses it and reaches the goal by parkour.

`mine-explore-off-bounded` — selected to cover the no-target cancellation branch; correctness is zero stone targets, zero cobblestone, an inactive mine, and player feet still within four blocks of the staged start.

`mine-axe-tool-path` — selected because tool-interface coverage has pickaxe cases but no axe-required target; correctness is the oak log gone with positive diamond-axe damage and iron-pickaxe damage still zero.

`path-bottom-slab-off` — selected as an unoccupied movement-setting route choice; correctness is the player reaching the goal through the z=1 full-block route without entering x=1..4 of the intact bottom-slab shortcut.

The existing wave-2 registrations `dump-prefers-capacity` and `indexboxes-all-refresh` already cover B4 and B5, so they were not duplicated. All newly staged shulkers use an empty block followed by `/item replace block`; no item NBT or renamed gamerule is embedded in wave-3 fixtures. No Minecraft/in-game suite was run by instruction; the required Gradle test is the verification gate.
