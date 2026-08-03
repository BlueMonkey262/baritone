# Fix notes

Applied the seven Class B and Class C triage fixes from `TRIAGE.md`.

- `backfill-no-rubble-no-placement`: made the corridor wall two blocks high (six stone blocks),
  updated the wall counter and staging assertion, so ordinary ascent cannot bypass the wall.
- `builder-orient-timeout-independent-target`: replaced the one-block cross with a complete
  bedrock shell over `x=2..6`, `z=-2..2`, `y=0..1`, leaving only the oriented target air. Staging
  now verifies the complete shell and the independent target.
- `restock-sync-timeout-fallback`: changed the blocked box's breakable slab to bedrock and made
  `stagingComplete` verify it, so `allowBreak=true` cannot repair the blocked candidate.
- `container-exact-restock-source`: staged three separate one-item white-concrete slots and
  asserts that layout before starting. The source total can therefore be checked for the expected
  two-item remainder after one complete source-slot transfer.
- `dump-active-substitute-kept`: replaced the unreliable feet-radius observation with a
  server-thread count of occupied destination slots, and records that measurement with the junk
  reduction supporting the verdict.
- `dump-reindex-after-deposit`: corrected the clear schematic from one column/two blocks high to
  two columns/one block high, matching both staged targets.
- `dump-whole-load-before-return`: made the shared dump occupancy helper use the server-thread
  container oracle, records destination occupied slots, and raises the strict free-slot trigger
  from 2 to 3 so the initial two-free-slot fixture exercises the temporal unload assertion.

No scenario was narrowed; each description still matches the measurement it checks. All affected
scenarios remain registered with `registerUncurated`.

The Class A scenarios `container-exact-deposit-destination` and `mine-existing-quantity` were left
untouched, as required.
