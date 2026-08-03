Task: write in-game harness scenarios covering the `itemSaver` tool-protection feature.

Work in ~/Projects/baritone. **Read `FORK-NOTES.md` §2a and §7 first** — §2a describes the feature,
§7 describes how scenarios must be written (why verdicts read world state rather than chat, and why
staging goes through vanilla commands).

## Why this exists

`itemSaver` shipped in v0.1.1 with **no in-game coverage at all**, because the setting defaults off
and no scenario enables it. Two defects shipped as a direct result and were found by a human playing
the game within the hour:

1. With the pickaxe spent, the bot mined with a **sword** instead of requesting a replacement
   (`useSwordToMine` defaults true). Breaking blocks with a sword costs two durability per block, so
   it destroyed the more valuable item to protect the cheaper one.
2. With `useSwordToMine` off it mined with **bare hands** instead — same failure, quieter.

Both are now fixed on trunk. These scenarios exist so that class of failure cannot ship again.
**A scenario that passes without being able to fail is worse than no scenario** — see the
`PistonObserverPairScenario` note in ROADMAP.md Phase 0 for the precedent.

## Use Efficiency V wooden pickaxes

Chosen deliberately: wooden pickaxes have 59 max durability so they reach the threshold quickly, and
Efficiency V keeps the mining fast enough that scenarios finish in a sensible tick budget.

`itemSaverThreshold` defaults to 10, and the rule is `damage + threshold >= maxDamage`. So a wooden
pickaxe with **damage >= 49** counts as spent, and damage 50 leaves a comfortable margin.

**Verify the item component syntax against this Minecraft version before relying on it.** Do not
trust my spelling of it; check the registry or an existing scenario and confirm the item actually
arrives with the intended damage and enchantment. Staging that silently produces an undamaged
pickaxe would make every one of these scenarios pass while testing nothing, which is the exact
failure mode listed above. Assert the staged state in `stagingComplete`.

## Scenarios to write

Each is a separate class in `src/main/java/baritone/testing/scenario/`, registered in
`TestingBehavior`. Register them all with `registerUncurated` — a human promotes them to curated
after reviewing a real run.

1. **Tool is not broken.** `itemSaver true`, one spent Efficiency V wooden pickaxe, a stone wall to
   mine, no shulker box. Verdict: the pickaxe still exists in the inventory and its damage has not
   increased. The whole point is that it did not get spent to zero.

2. **Replacement fetched from a box.** `itemSaver true`, `restockFromBoxes true`, spent pickaxe, and
   a registered shulker box containing a fresh Efficiency V wooden pickaxe. Verdict: the player ends
   up holding/owning an undamaged pickaxe, the box no longer contains it, and mining progressed.
   Model the box staging on `RestockFromBoxScenario` and `AbstractBoxBuildScenario`.

3. **No box: stop, do not grind.** `itemSaver true`, spent pickaxe, no box that can supply one.
   Verdict: the mine stops within a bounded time and the spent pickaxe is still intact. It must not
   sit there mining at a hundredth speed.

4. **Sword is not used as a substitute.** `itemSaver true`, `useSwordToMine true` (the default),
   spent pickaxe **and a sword in the hotbar**. Verdict: the sword's damage is unchanged. This is
   the exact reported bug; it must fail against the pre-fix behaviour.

5. **Bare hands are not a substitute.** As above but `useSwordToMine false` and no sword. Verdict:
   the stone is not slowly mined by hand — either a replacement is fetched, or the job stops.

6. **Control: `itemSaver false` behaves like upstream.** Spent pickaxe, setting off. Verdict: mining
   proceeds normally and the pickaxe is allowed to break. This proves the other five are actually
   testing the setting rather than something incidental.

## Rules

- Verdicts read **world and inventory state**, never chat text.
- Stage through `arena.command(...)` / `arena.fill` / `arena.setBlock` / `arena.teleport`.
- Use `arena.note(...)` to record what was staged and what was observed — that is what makes a
  failure diagnosable. Include the pickaxe's damage value in the notes.
- Give each scenario a tick budget appropriate to its work; do not copy a large one blindly.
- `registerUncurated` for all six.
- LGPL header on every new file.
- Do not modify feature code. If a scenario exposes a bug, report it; do not fix it.
- Do not push, do not create pull requests, do not touch branches other than the one you create.

## Branch and gates

Work on a new branch off `shulker-restock` (trunk):

    git checkout -b test/itemsaver-scenarios shulker-restock

Gates: `./gradlew :test` green and `./gradlew build` compiles all four loaders. You cannot run the
in-game harness yourself — it needs a Minecraft client — so **compiling is not evidence the
scenarios work**. Say so plainly in your report rather than implying they are verified.

## Report

1. Each scenario: what it stages, what it asserts, and *how it would fail* if the bug returned.
2. The exact item syntax you used for a damaged, enchanted pickaxe, and how you verified it is
   correct for this Minecraft version.
3. Any scenario you are unsure can actually fail. That is the most valuable thing you can tell me.
4. Gate output.
5. Anything surprising.

Commit once on the branch if both gates pass. If not, leave the tree dirty and report what broke.
