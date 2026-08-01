# Harness scenario backlog

100 proposed in-game scenarios, generated 2026-08-01 by `gpt-5.6-terra` and recorded here verbatim
in substance. This is a **backlog, not a plan** — nothing here is committed to, and several entries
describe capabilities Continuo does not have yet (see "Not yet buildable" below).

Scenarios are added under `src/main/java/baritone/testing/scenario/` and registered in
`TestingBehavior`. New ones start `registerUncurated` and are promoted to curated only after a green
run — see FORK-NOTES.md §7.

## How to choose the next one

Not top-to-bottom. Two things make an entry worth doing now:

1. **It protects a defect the ledger records as FIXED with no test.** `DEFECTS.md` has a Test column
   for exactly this; a fix nobody can regress-test is a fix waiting to be undone. This is the
   retroactive form of the standing rule that no fix lands without a test.
2. **It closes a measurement gap.** Every build scenario today is tiny, so nothing would detect a
   build-scaling regression — which matters now that performance is a stated goal.

Prefer scenarios whose failure mode is unambiguous. A scenario that cannot fail for the right reason
is worse than no scenario: `piston-observer-pair` is registered uncurated for precisely that reason.

## Technique: `/tick` for time-dependent scenarios

Anything that waits on game time — item despawn, crop growth, hopper transfer rate, furnace smelting,
redstone clocks — should raise the tick rate with the vanilla `/tick` command rather than burn real
seconds. The arena already issues vanilla commands through `TestArena#command`, so this needs no new
plumbing.

This makes a whole class of entry below feasible that otherwise would not be: 7, 15, 17, 19, 20, 38,
45, 50, 51–60. Without it a crop-growth scenario is either flaky or takes minutes.

Watch for two things when using it: restore the rate afterwards so it cannot leak into the next
scenario, and remember that a raised tick rate changes the *ratio* between game time and Baritone's
per-tick CPU budget, so a scenario using it is not a valid performance measurement.

---

## Item sorters and filters (1–6)

1. `build-item-sorter-single` — one-slice hopper/filter sorter; insert mixed items, verify only the intended chest receives its item.
2. `build-item-sorter-multi` — four adjacent sorter slices; verify each of four item types reaches its corresponding output.
3. `sorter-overflow-protection` — fill a filter nearly to capacity; verify excess passes to an overflow chest rather than breaking the filter.
4. `sorter-filter-maintenance` — start with correctly primed filter hoppers; verify the builder preserves required filter contents while completing the structure.
5. `sorter-unstackable-bypass` — route named tools/non-stackables through a bypass line while normal stackables sort.
6. `sorter-rename-sensitive` — verify a renamed item does not incorrectly enter an ordinary item filter.

## Redstone contraptions (7–20)

7. `build-hopper-clock` — build and start a hopper clock; assert repeated pulses.
8. `build-piston-door` — 2×2 piston door; assert the input closes and reopens it.
9. `build-hidden-staircase` — piston staircase/flush entrance; verify open and closed states.
10. `build-observer-pulse-generator` — observer one-shot pulse circuit; assert a lamp pulses once.
11. `build-redstone-lamp-line` — lever, dust line, lamps; toggle and verify every lamp changes state.
12. `build-repeater-lock` — locked repeater circuit; verify the signal is held while the side input is powered.
13. `build-comparator-subtractor` — container/comparator subtraction; verify output strength changes with inventory contents.
14. `build-daylight-switch` — daylight-sensor lamp circuit; verify day/night behaviour.
15. `build-note-block-sequencer` — timed note-block sequence; assert all intended blocks fire.
16. `build-dispenser-trigger` — dispenser circuit; verify exactly one item dispensed on a pulse.
17. `build-dropper-elevator` — vertical dropper elevator; verify an item reaches the top container.
18. `build-water-flush-harvester` — water-flush crop harvester; verify crops are collected downstream.
19. `build-sugar-cane-farm-module` — one observer/piston cane module; verify a grown cane is harvested.
20. `build-melon-pumpkin-farm-module` — observer/piston farm module; verify a spawned crop is broken and collected.

## Restocking (21–30)

21. `restock-two-materials` — build needing two depleted materials from one box; verify both are fetched.
22. `restock-multiple-boxes` — material spread across two boxes; verify the second is used after the first empties.
23. `restock-nearest-sufficient-box` — equivalent boxes at different distances; verify the nearer adequate one is selected.
24. `restock-low-stack-item` — fetch a stack-size-one material; assert no over-fetching.
25. `restock-stack-size-16` — fetch a 16-stack material; verify extra-stack accounting.
26. `restock-empty-indexed-box` — index claims stock, live box is empty; verify fallback to another box.
27. `restock-sync-timeout-fallback` — first container fails to synchronise; verify it is skipped and a later box succeeds.
28. `restock-full-inventory-swap` — material needed while inventory is full; verify safe slot management rather than losing protected items.
29. `restock-preserves-tools` — restock amid a crowded inventory; assert tools, food, armour and weapons remain.
30. `restock-cancel-cleanup` — cancel mid-container-interaction; verify the menu closes and control is released.

## Unloading and box management (31–40)

31. `dump-single-shulker-trip` — fill inventory during a build; verify a dedicated deposit trip occurs.
32. `dump-prefers-capacity` — nearby nearly-full box vs farther empty one; verify capacity-aware selection.
33. `dump-spans-boxes` — deposit more than one box holds; verify continuation to the next.
34. `dump-max-box-limit` — reach the per-trip box limit; assert a bounded, intelligible outcome.
35. `dump-junk-only` — verify rubble is deposited while build materials remain.
36. `dump-keep-throwaways` — verify configured throwaway items are retained.
37. `dump-never-stows-shulkers` — verify shulker boxes stay in inventory during unloading.
38. `indexboxes-all-refresh` — change contents after indexing, run the all-box refresh, verify the estimate updates.
39. `addbox-wrong-open-menu` — open box A, register nearby B; verify B is not indexed with A's contents.
40. `missing-box-loaded-vs-unloaded` — distinguish a broken loaded box from an unloaded chunk; only the former becomes missing.

## Mining (41–50)

41. `mine-exact-quantity` — stop exactly at the requested item count.
42. `mine-visible-only` — with `legitMine`, mine exposed ore but leave enclosed ore untouched.
43. `mine-tool-selection` — competing tools; verify the fastest valid tool is selected.
44. `mine-no-tool-fallback` — block requiring an absent tool; assert clean failure rather than repeated attempts.
45. `mine-delayed-drop` — delay or displace a drop; verify completion waits for pickup or a reasoned timeout.
46. `mine-stacked-drops` — blocks yielding multiple items; verify accounting uses item count, not block count.
47. `mine-ore-behind-water` — target whose path requires controlled water traversal.
48. `mine-ice-variants` — normal, packed and blue ice; verify no variant is incorrectly rejected.
49. `pickup-owned-drop-only` — old drop beside a fresh one; verify only the new expected drop is pursued.
50. `pickup-expiry` — expected drop unreachable until association expires; verify pursuit stops.

## Farming (51–60)

51. `farm-mature-wheat` — harvest mature wheat and replant.
52. `farm-mixed-growth` — leave immature crops intact.
53. `farm-nether-wart` — harvest and replant, including soul-sand navigation.
54. `farm-cocoa` — harvest from jungle logs and replant on the proper face.
55. `farm-sweet-berries` — handle slowdown/damage while harvesting.
56. `farm-beehive-safety` — harvest with smoke protection; verify bees are not provoked.
57. `farm-composter-loop` — route excess seeds through a composter; verify bone meal production.
58. `farm-auto-smelter` — furnace feed/output; verify harvested material is smelted.
59. `farm-replant-material-shortage` — insufficient seeds; verify harvesting stops or reports rather than destroying future yield.
60. `farm-chunk-edge` — field crossing a chunk boundary; verify all mature crops are considered.

## Pathing (61–80)

61. `path-ladder-ascent` · 62. `path-ladder-descent` · 63. `path-vine-climb`
64. `path-door-interaction` · 65. `path-fence-gate-interaction`
66. `path-ice-vs-land` — route by slipperiness cost, not straight-line distance.
67. `path-soul-sand-choice` — short soul-sand vs longer normal route under different cost settings.
68. `path-cobweb-avoidance` · 69. `path-powder-snow-recovery`
70. `path-parkour-gap` — solvable jump gap, no block placement.
71. `path-fall-safety` — longer route rather than a lethal drop.
72. `path-scaffold-bridge` — assert only allowed scaffold blocks are used.
73. `path-breakable-shortcut` / 74. `path-no-break-detour` — identical world, breaking on vs off.
75. `path-avoid-cactus` · 76. `path-water-exit` — leave a water column without oscillating.
77. `path-boat-channel` · 78. `path-elytra-landing`
79. `path-goal-moved` — move the destination mid-path; verify replanning without permanent churn.
80. `path-changed-terrain` — obstruct the planned route mid-run; verify recovery.

## Sheltering (81–90)

81. `shelter-on-hostile-hits` — configured hits, then retreat to the nearest rally box.
82. `shelter-ignores-fall-damage` · 83. `shelter-ignores-lava-damage`
84. `shelter-sleep-at-night` — walk to a valid nearby bed and enter it.
85. `shelter-refused-bed-retry` — sleep temporarily invalid; verify retry after the configured delay.
86. `shelter-restock-handoff` — full inventory at the rally box; verify lower-priority work cannot run during the deposit handoff.
87. `shelter-rally-box-preference` — more capacious but farther box; verify the selected rally box is used first.
88. `shelter-high-hit-threshold` — `shelterMinHits` above 16; verify sustained attacks still trigger.
89. `shelter-mob-lost` — threat removed during retreat; verify a stable documented state.
90. `shelter-unreachable-rally` — nearest rally box unreachable; verify a reachable fallback or bounded failure.

## Builder (91–100)

91. `builder-layered-house` — small multi-material house with `buildInLayers`; assert layer transitions and completion.
92. `builder-air-clearing` — replace an obstructing block with schematic air before building around it.
93. `builder-incorrect-block-repair` — wrong block/state present; verify correction.
94. `builder-protected-container` — build around a supply chest/shulker; verify it is never mined as schematic air.
95. `builder-descent-through-build` — descent through a partial structure; verify no break/place loop.
96. `builder-backfill-boundary` — verify backfill never fills holes inside active schematic bounds.
97. `builder-backfill-lookahead` — hole needed by a future movement is not prematurely refilled.
98. `builder-backfill-material-policy` — backfill uses rubble only, never build materials.
99. `builder-unobtainable-one-block` — one unavailable material among available ones; verify the completed portion is reported without an infinite stall.
100. `builder-cancel-resume` — cancel a partial build and restart; verify resume from world state without duplicating or breaking correct blocks.

---

## Not yet buildable

Several entries assume behaviour Continuo does not have, and would fail as feature requests rather
than as regression tests. They are kept because they describe wanted behaviour, but they are not
candidates until the capability exists:

- **7, 12, 15** and anything needing a block's state to be reached by *interaction after placement*
  (repeater delay, note block pitch) are blocked on U-local-03 — the builder can place a component
  but has no operation to place-then-interact until a requested state is reached.
- **51–60** assume a farming process. There is none.
- **77** assumes boat support; FEATURES.md lists boats as a probable never.
- **1–6** are large multi-block contraptions. Worth attempting only after the single-component
  placement families in the generated catalog are all green, since a sorter that fails tells you
  almost nothing about *which* component was misplaced.

## Missing from this list

A **large-build performance scenario**. Every build scenario in the suite is tiny — a 5×5×3 ring,
six observers — so `placeable` never grows enough for a build-scaling regression to show. Two
optimizations landed on 2026-08-01 with no measurable effect for exactly this reason. Performance is
a stated goal; nothing currently measures it.
