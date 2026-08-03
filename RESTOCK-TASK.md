# Task: diagnose why restocking fails on 1.20.1 (analysis only, no builds)

**Do not run Gradle, Minecraft, or the in-game suite.** Another session is building and memory is
tight; this machine went down from exhaustion last night and cannot be restarted remotely. This task
is reading and reasoning. If you need something compiled to be sure, say so in the report.

## The evidence

`restock-from-box` — a **curated** scenario, one of the eleven that gate every release — fails
deterministically on 1.20.1, on all three clients:

    builder stopped with 16 of 48 blocks missing

It passes on 26.1.2, 1.21.11, 1.21.1, 1.21.4 and 26.2. Only 1.20.1 fails. The full report is
`report-1.20.1.json` in this directory; read the scenario's notes in it.

Both build gates were green on this branch. So a build gate cannot see this, and the in-game suite is
the only thing that caught it.

## Where to look first

This branch is our port to a Minecraft version predating data components. An audit of that port
(branch `review/shim-1.20.1`, file `SHIM-AUDIT.md` — read it) already found three divergences from
canonical behaviour, and two of them touch material handling directly:

- Tool detection was narrowed. Canonical treats anything carrying `DataComponents.TOOL` as a tool;
  this port asks `PickaxeItem.class` in `bestToolAgainst` and `instanceof DiggerItem` in the
  throwaway-safe selection, so axes, shovels, hoes and shears drop out.
- `isJunk`'s FOOD/EQUIPPABLE/TOOL/WEAPON tests became item-class checks.

`RestockProcess` decides what to fetch and what to keep through `isJunk` and the `worthKeeping`
predicate supplied by `BuilderProcess#inventoryWants`. "16 of 48 blocks missing" means the builder
ran out of material and could not obtain more — so the interesting question is whether the port
causes the restock to fetch the wrong thing, keep the wrong thing, or conclude a box is exhausted
when it is not.

Compare against canonical throughout:

    git diff shulker-restock -- src/main/java/baritone/process/RestockProcess.java
    git diff shulker-restock -- src/main/java/baritone/process/BuilderProcess.java
    git diff shulker-restock -- src/main/java/baritone/behavior/InventoryBehavior.java

Do not assume the shim is the cause just because it is the obvious suspect. Other things differ on
1.20.1 — inventory accessors, stack-size handling, container menu slot layout. Rule candidates in or
out with evidence.

## Output

`RESTOCK-DIAGNOSIS.md`:

1. Your single best hypothesis, stated so it could be proven wrong.
2. Evidence: file and line, quoted, comparing port against canonical.
3. Evidence against it, or what you could not rule out. Not optional.
4. The one observation that would settle it in a single in-game run — I can run that.
5. A suggested fix as a diff, but **do not apply it**. A plausible unverified patch has cost this
   project real time before.

Rank confidence honestly. "I could not determine this" is a useful answer.
