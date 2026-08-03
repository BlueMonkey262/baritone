# Scenario notes

## `itemsaver-tool-swap-back`

- Added an uncurated live scenario for the `RestockProcess` tool swap-back path.
- It starts mining with one spent Efficiency V wooden pickaxe and two fresh matching pickaxes in
  the registered box. `restockExtraStacks=1` makes the fetch obtain both replacements.
- The verdict requires the wall to have been mined, exactly one damage-qualified spent pickaxe to
  be in the source box, no fresh pickaxes to remain there, and a replacement pickaxe to be in the
  player's main hand. The damage-qualified container count distinguishes swap-back from ordinary
  junk deposit.

## `itemsaver-tool-swap-back-no-replacement`

- Added an uncurated stale-index control. The live box is empty, while the registered index claims
  it contains one wooden pickaxe. The bot must walk to and inspect that box before the verdict can
  check the worn tool.
- It requires the spent pickaxe to remain in inventory and no wooden pickaxe to appear in the box;
  this pins the `tookAnything`/replacement-arrival qualifier and catches an eager swap-back after
  a failed fetch.

## Bulk-deposit allowlist ordering

- No new scenario was needed. `dump-keep-throwaways` already exercises the relevant branch of
  `RestockProcess#isJunk`: cobblestone is a `BlockItem`, so it is eligible before the non-block
  `depositableBulkItems` allowlist branch, and the scenario checks that one configured throwaway
  stack stays with the player while surplus rubble is deposited. `shulkerDumpKeepThrowawayStacks`
  is therefore covered independently of the allowlist.

## Verification

- The permitted Gradle gate could not be run in this sandbox. Both
  `GRADLE_USER_HOME=/tmp/scen-gradle-3 ./gradlew :test --no-daemon --offline` and the same
  command without `--offline` stopped in the wrapper before Gradle started: the isolated home did
  not contain Gradle 8.14.4, and the sandbox blocked the wrapper's download socket. The gate is
  therefore unverified; the user will compile it separately.
- The in-game scenario suite was not run, per `SCEN-TASK.md`; live pass/fail remains unverified.
