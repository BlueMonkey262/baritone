# Adversarial review

I reviewed all 23 scenarios added on top of shulker-restock. The findings below are ordered by
the risk of a false pass, then by fixture/assertion defects. The shared-fixture fixes are called
out in each affected scenario.

## pickup-owned-drop-only

What is wrong: this does not prove that PickupBlocksProcess bound itself to the fresh drop. The
old cobblestone entity is summoned with PickupDelay:32767s, so even a broken ownership filter
cannot pick it up. The fresh stone drop appears at the break site and can be collected by ordinary
player proximity.

How it would show up: the target breaks, the fresh cobblestone reaches the inventory, the old
entity remains because vanilla pickup is disabled, and the scenario reports PASS even if the
pickup process selected the old entity or never exercised its UUID association.

Disposition: recommending a fixture in which both candidates are pickup-eligible but incidental
player pickup is prevented, or an observable hook proving which entity was bound. Not fixed because
that changes the scenario's live geometry and requires a product choice.

## pickup-expiry

What is wrong: the PASS condition only observes that the target broke, seven seconds elapsed, and
the pickup process is inactive. With no drop from glass, PickupBlocksProcess.isActive() has no
eligible live entity to target; the pre-existing entity is explicitly excluded. Thus there is no
evidence that an expectation was ever created and later expired.

How it would show up: a regression that never records the expected-drop entry still breaks the
glass, leaves the protected old entity alone, and passes after the same seven-second wait.

Disposition: recommending an expectation-state observation or a controlled delayed-drop variant
that makes the pickup process visibly active before expiry. Not fixed because the current public
API exposes neither the expectation list nor its expiry.

## mine-no-tool-fallback

What is wrong: the description calls the obsidian target unbreakable, but obsidian is breakable by
hand. With allowBreak=true and no tool, Baritone can legally attempt the very slow hand break; the
300-tick budget is shorter than that operation, so this is not a deterministic clean-stop fixture.

How it would show up: a correct run can remain active in front of the obsidian until the 15-second
budget and fail, while changing the tool-selection or hand-mining policy could change the result
without the scenario's claim being clear.

Disposition: recommending either a truly unbreakable target such as bedrock with a corresponding
description, or an assertion for the intended no-correct-tool hand-mining behavior. Not fixed
because those test different semantics.

## mine-delayed-drop

What is wrong: the verdict requires only that the target is gone and cobblestone eventually appears
in the inventory. It never proves that mining or pickup remained pending after the break.

How it would show up: a regression can declare the mine complete immediately after breaking the
stone; ordinary pickup can collect the water-displaced cobblestone later, and the scenario still
passes.

Disposition: recommending an assertion that observes the post-break pending pickup state before
accepting the later inventory count. Not fixed because the correct handoff timing between the mine
and pickup processes needs live validation.

## mine-visible-only

What is wrong: the scenario set allowBreak=false while its description and verdict required
mining the exposed ore. That setting makes MineProcess.filterFilter() reject the target before any
block can be broken.

How it would show up: the visible ore remains, no diamond is collected, and the scenario times out
regardless of whether legit-mine visibility is correct.

Disposition: fixed by removing the contradictory override; the shared mining settings already set
allowBreak=true.

## mine-ore-behind-water

What is wrong: this had the same allowBreak=false contradiction while requiring the ore beyond the
water to be mined.

How it would show up: the player may path toward the opening, but the target cannot be mined and
the scenario times out or reports incomplete work.

Disposition: fixed by removing the contradictory override. Separately, the stone barrier ends at
z=-8..8 while the arena remains open beyond it, so a live run may legally route around the end and
fail the crossedWater assertion. I recommend extending the barrier or making the water route
uniquely necessary; that geometry choice is not fixed here.

## dump-prefers-capacity

What is wrong: the shared stagingComplete() checked only that each shulker was a container and
reported a non-negative occupied-slot count. It did not verify the 26 distinct capacity items
that this scenario relies on.

How it would show up: a malformed or empty NBT payload could be accepted as staging; the nearer
box would no longer be nearly full, so the capacity-selection assertion would be testing a
different fixture.

Disposition: fixed in AbstractShulkerDumpScenario by checking the expected item counts and
expected occupied-slot count before starting.

## dump-spans-boxes

What is wrong: it had the same silent-capacity-fixture hole as dump-prefers-capacity.

How it would show up: if the three boxes were not actually nearly full, the run could deposit into
one box and still appear to test continuation across boxes.

Disposition: fixed by the shared staging assertion described above.

## dump-max-box-limit

What is wrong: it had the same silent-capacity-fixture hole as the other indexed dump scenarios.

How it would show up: if either capacity box staged empty or with the wrong items, the one-box cap
would not be exercised against the intended one-slot remaining capacity.

Disposition: fixed by the shared staging assertion described above.

## restock-low-stack-item

What is wrong: two stack-size-one blue shulker boxes were encoded as count:2 in one container slot.
That is not an authentic stack-size-one fixture and may be rejected or normalized by the server.

How it would show up: staging can wait forever for two items, or the live container can contain an
invalid stack whose transfer behavior does not represent two real source boxes.

Disposition: fixed by encoding two count:1 entries in slots 0 and 1.

## restock-sync-timeout-fallback

What is wrong: the blocked box was capped with a full stone cube while allowBreak=true. The
restock path deliberately targets a full-cube shulker obstruction as a breakable clearance block,
so the scenario could remove its own cap before attempting the open.

How it would show up: the first box opens and supplies material, leaving the fallback box
untouched; the intended timeout/fallback assertion then fails for the wrong reason.

Disposition: fixed by using a bottom stone slab for the obstruction and asserting that staged block
state. It is a non-full-cube collision obstruction, so pathing does not select it for the
clearance-breaking behavior.

## restock-full-inventory-swap

What is wrong: the shared crowded-inventory fixture checked only the number of occupied slots and
the protected items. A wrong valid block ID in one junk slot could therefore pass staging while
changing what the dump verdict actually measures.

How it would show up: the run starts with the wrong junk composition, potentially making the
dumped red concrete assertion fail or pass for an unintended reason.

Disposition: fixed in RestockInventoryFixture by checking every staged junk slot against its
declared registry ID and count.

## restock-preserves-tools

What is wrong: it shared the same under-specified crowded-inventory fixture as
restock-full-inventory-swap.

How it would show up: a typo or rejected junk command could leave the inventory numerically full
but not contain the intended block-item set, weakening the classifier assertion.

Disposition: fixed by the shared exact-slot staging check.

## large-build-performance

What is wrong: no fixture or verdict defect was found, but its budget is intentionally generous:
the performance bound is 600 ticks while the scenario's timeout budget is 900 ticks.

How it would show up: a slow build gets up to 45 seconds to produce a diagnostic, although PASS is
still rejected after the 30-second performance bound.

Disposition: leave unchanged. The author explicitly marked this bound for calibration from a real
run, and the extra diagnostic window is appropriate for that purpose.

## Scenarios reviewed with no issue found

dump-keep-throwaways

dump-single-shulker-trip

indexboxes-all-refresh

mine-exact-quantity

mine-ice-variants

mine-stacked-drops

mine-tool-selection

missing-box-loaded-vs-unloaded

restock-cancel-cleanup
