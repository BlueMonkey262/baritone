# Batch notes

- `mine-exact-quantity` — protects quantity overshoot; assumes stone drops are collected before the quantity check settles.
- `mine-visible-only` — protects legitMine from x-raying enclosed ore; the exposed-ore sightline still needs a real run.
- `mine-tool-selection` — protects fastest-tool selection; no uncertainty beyond normal inventory/tool timing.
- `mine-no-tool-fallback` — protects clean failure on an unbreakable target without a tool; the bounded stop is intentionally uncurated until observed.
- `mine-delayed-drop` — protects completion waiting for a water-displaced drop; water flow and pickup timing are unverified without the in-game run.
- `mine-stacked-drops` — protects item-count accounting for a four-snowball snow-block drop; assumes the current loot table remains four snowballs.
- `mine-ore-behind-water` — protects controlled traversal through a water opening; route choice around the long barrier is unverified.
- `mine-ice-variants` — protects normal, packed, and blue ice recognition; relies on the 26.1.2 Silk Touch component syntax used by the harness.
- `pickup-owned-drop-only` — protects binding pickup to the fresh drop rather than a nearby old entity; summon NBT and pickup-delay behavior are unverified.
- `pickup-glass-no-drop` — protects a no-drop glass break from claiming an old nearby item; the expectation list and its expiry remain unobservable through the public API.
- `large-build-performance` — adds the missing scaling signal with a 1,920-block slab and a 6,000-tick bound; the bound is deliberately generous and must be calibrated from a human run.

No backlog entries in 41–50 were excluded by the “Not yet buildable” section. `./gradlew compileJava` passes; the in-game suite was not run per task instructions.
