# Scenario repair notes

## `pickup-owned-drop-only`

The pre-existing cobblestone is now summoned with `PickupDelay:0s`, so it is a genuine pickup
candidate. It is diagonally within the pickup association radius but outside ordinary player
pickup. The stone target now sits above a water run, keeping its fresh cobblestone away from
incidental player pickup; the verdict requires that fresh item in the inventory and the original
entity alive.

I could not run the in-game suite. The side-offset geometry and the 26.1.2 command NBT are therefore
still a live-run guess.

## `pickup-glass-no-drop` (formerly `pickup-expiry`)

The scenario no longer claims to observe expectation expiry. It now honestly checks that the glass
break happened, that the pre-existing glass entity was not claimed, that no glass entered the
inventory, and that pickup is inactive after the settling interval. The public API exposes neither
the expectation list nor its expiry timestamp, so expiry itself remains unverified.

## `mine-no-tool-fallback`

The target is now bedrock, which is genuinely unbreakable, and the description and diagnostics say
bedrock rather than obsidian. This makes the clean-stop claim about an actually unbreakable target.

I could not run the in-game suite, so the live cancellation timing remains unverified.

## `mine-delayed-drop`

The water run is longer and staging verifies both ends of it. The verdict records a pending state
only after the target is gone, cobblestone is still absent from the inventory, a cobblestone entity
is visible in the world, and the pickup process is active. It accepts completion only after that
state was observed and the cobblestone is in the inventory while the mine process has stopped.

I could not run the in-game suite. Whether the water geometry reliably creates a long enough pending
window is the remaining flagged guess.

## Verification gate

The permitted `GRADLE_USER_HOME=/tmp/scen-gradle-5 ./gradlew :test --no-daemon --offline` command
could not start in the sandbox: the wrapper attempted to download Gradle 8.14.4 and the sandbox
denied the socket. No unit-test result is claimed. The in-game suite was not run.
