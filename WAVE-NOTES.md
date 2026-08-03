# Wave 2 notes

I picked these twelve because they cover seven subsystems and include two fixed, untested defects:
M3 (`restock-no-boxes-bounded`) and H10 (`dump-active-substitute-kept`). The remaining cases are
small live-world policy and filter fixtures whose verdicts can be based on blocks, player inventory,
position, and process state. I did not run Minecraft, so none of the scenarios is claimed to pass.

- `restock-no-boxes-bounded` — catches repeated no-source restock attempts (M3); the report will measure placed targets, builder activity, and player feet; I could not verify the live shortage latch or command-side registration cleanup.
- `dump-active-substitute-kept` — catches H10 depositing a configured active substitute as junk; the report measures both dirt target states, carried red rubble/dirt, free slots, and arrival at the empty shulker; I could not verify the destination’s container contents (T-01), and the `item replace entity` slot syntax is a fixture portability assumption.
- `builder-ignore-existing` — catches `buildIgnoreExisting` breaking an existing block while doing unrelated work; the report measures the preserved stone and the separately placed concrete; I could not verify the live builder path.
- `builder-valid-substitute` — catches `buildValidSubstitutes` replacing an accepted dirt state or suppressing another target; the report measures dirt remaining and the independent concrete placement; I could not verify the live state comparison.
- `builder-ignore-direction` — catches orientation checking overriding `buildIgnoreDirection`; the report measures the existing stair’s east facing and the separately placed stair; I could not verify the live orientation planner, and the default oak-stair shape is a fixture assumption.
- `backfill-no-rubble-no-placement` — catches backfill consuming an arbitrary/build material when no configured rubble is carried; the report measures the opened corridor wall, final goal position, and zero rubble inventory; I could not verify the temporary throwaway restriction on a live tick.
- `mine-allow-break-anyway` — catches the miner dropping unlisted filter blocks when `allowBreak` is false; the report measures stone removal, intact deepslate, and cobblestone; I could not verify the live filter-pruning order.
- `mine-y-range` — catches Y-bound pruning selecting an out-of-range target; the report measures lower-target removal, upper-target preservation, and the resulting drop; I could not verify the seven-version dimension-height conversion in-game.
- `pickup-dropped-stack-merge` — catches pickup losing a protected item or failing to merge a fresh displaced drop; the report measures target removal, cobblestone 63→64, zero loose cobblestone entities, and diamond=1; water displacement and vanilla pickup timing are fixture assumptions I could not verify live.
- `path-parkour-on` — catches parkour being ignored or changing terrain while crossing a sealed gap; the report measures the goal position and unchanged gap block; I could not verify that the live path used the intended parkour movement rather than another route.
- `itemsaver-traverse-obstacle` — catches `MovementTraverse` swinging a protected tool through its obstruction branch; the report measures approach to the wall, wall state, pickaxe damage, and pathing state; treating a fresh wooden pickaxe as spent via threshold 59 is a portability assumption I could not verify across all loaders.
- `inventory-main-tool-swap` — catches inventory arbitration mining bare-handed when the best tool is outside the hotbar; the report measures target removal, pickaxe hotbar presence/damage, and preserved dirt slots; I could not verify the live inventory transaction, and `/give` placing into main inventory after nine exact slot replacements is a fixture assumption.

No selected case was swapped after implementation. I excluded the existing “Ten to implement first”
registrations, all scenarios already occupied by `TEST_BACKLOG.md`, container-oracle cases blocked by
T-01, and dynamic-phase/packet-order cases requiring harness capabilities that are not present.

Verification performed: `GRADLE_USER_HOME=/home/eli/.gradle-isolated/wave2 ./gradlew :compileJava --no-daemon --console=plain` completed successfully. The in-game suite was not run by instruction.
