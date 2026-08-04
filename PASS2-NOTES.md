# Pass 2 notes

This pass treats an in-game report as evidence about the fixture and verdict, not as proof that the
bot completed a job. The seven entries in `FAILURES.md` were not rerun here; the source changes were
compiled and `GRADLE_USER_HOME=/home/eli/.gradle-isolated/pass2 ./gradlew :test --no-daemon` passed.

## `builder-orient-timeout-independent-target`

The sealed stair target is genuinely unreachable, but the independent stair is not: it is outside
the bedrock shell and has its own material. The run left both targets air while the builder stayed
active for all 3,600 ticks. That is not an unachievable fixture completion; it is the same
unreachable-target starvation/replanning defect already tracked as U-local-01.

No scenario change was made. Its name, description and assertion still test the useful claim that a
timed-out oriented target must not prevent independent work. The failure is a product defect, not a
reason to weaken this scenario.

## `container-deposit-timeout-no-mutation`

The two clear targets became air at tick 125 before the deposit process had reached either box. The
old verdict treated that intermediate observation as final and failed for missing visits, even
though the fallback operation could still have been pending.

The verdict now records that early observation, waits for any active restock process and a bounded
40-tick settling window, then checks the durable measurements: the blocked box still has its white
marker and no red deposit, the fallback box gained red, and the player no longer carries red. The
scenario still proves a timed-out box is not mutated before fallback; it does not claim that target
clearing itself is the end of the deposit handoff.

## `dump-reindex-after-deposit`

The clear targets also became air before the unload trips had completed. In addition, the old
`playerFeet()` radius samples were not a reliable proof that a box had received a deposit; the run
reported both visits as false even though the assertion was checking a transient position sample.

The scenario now waits for the restock handoff to settle and records a box as used only when its
server-thread container oracle shows an occupied-slot increase above the staged 26-slot index. It
still requires two independent inventory reductions and both boxes, so the live-index refresh claim
has not been weakened.

## `dump-whole-load-before-return`

The one box was not capable of receiving the staged load. It contained 27 occupied slots after the
first unload while four eligible rubble items were still carried. The inventory had 31 distinct
junk stacks before the two clear drops, so a 27-slot shulker could never satisfy “whole load”.

The shared fixture gained small hooks for target/drop and eligible-item setup. This scenario now
stages 24 junk stacks, uses distinct stone and dirt targets, and sets the strict trigger so the
initial junk and the two later drops make one 26-stack load in the same 27-slot box. The carried
rubble oracle now includes dirt as well as cobblestone, and the verdict waits for the second unload
to settle before checking zero carried rubble. The scenario still claims that all eligible rubble
is unloaded before work resumes.

## `pickup-dropped-stack-merge`

The fixture staged a stone at y=0 and another at y=1, while water began one block away. The miner
could select the obstructed floor stone, and the y=1 drop did not actually fall into the staged
water. That made the timeout unrelated to the intended merge assertion.

The fixture now has one target at y=1 directly above a water run from x=5 through x=11. Its existing
claim remains unchanged: the fresh cobblestone must merge into the held 63-stack and the protected
diamond must remain.

## `restock-master-off-no-trip`

One carried white concrete was staged for two white targets. With `restockFromBoxes=false`, the
second target is unachievable; waiting for the build to complete or for the builder to become
inactive guaranteed a timeout. The observed state was the meaningful one: first target placed,
second target air, source box unchanged, carried white consumed, and no box visit.

The description now names that shortage state rather than claiming process termination. The verdict
keeps the no-trip and source-immutability checks and passes on the falsifiable staged shortage state;
it no longer claims that the builder stops, because that was not necessary to test the master switch.

## `shelter-preferred-rally-unload`

The rally box at (2, 0, 4) is nearer to the initial work position than the alternate at (10, 0, 4),
and the current shelter/restock handoff explicitly carries the selected rally target through the
deposit request. The original verdict nevertheless counted any depot crossed before the zombie’s
first recorded hit, so ordinary pre-shelter movement could produce the “alternate first” failure.

Depot sampling is now limited to the post-damage interval, and the harness directly checks the
selected rally position while shelter is active. The alternate-depot negative guard remains in
place after damage. This is a measurement correction, not a behavior change; if a rerun selects or
visits the alternate after the attack before rally unloading, that is a product defect under UB3.

## `restock-sync-timeout-fallback` (not in `FAILURES.md`)

The earlier slab rationale is correct. `RestockProcess.goalForBox` redirects only for a full-cube
lid obstruction, returning a `GoalBlock` on that obstruction. With `allowBreak=true`, a bedrock lid
can therefore strand the path before the intended open/sync timeout is exercised. A bottom slab is
not a normal cube, so the bot can reach the box, attempt the real interaction, time out, and try the
later box. The obstruction is restored to a slab and staging now checks the slab.

## Registration and verification

All affected scenarios remain registered with `registerUncurated`. No Minecraft run was performed
in this pass. The prescribed unit suite passed after the edits.
