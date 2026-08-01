# Handoff — 2026-08-02

State: **v0.1.1 released**. Trunk is `shulker-restock` at `35881fca`, clean. Supersedes the
2026-08-01 handoff.

## Naming history

The project was renamed twice on 2026-08-01: to **Continuo** in `137218c4`, then to **Tenor**.

**Releases `v0.1.0` and `v0.1.1` are published under the name Continuo** — that is what shipped, and
their notes and jar filenames say so. Do not retitle them. v0.2 is the first release under Tenor and
its notes should mention the rename, or users comparing them will reasonably think these are
different projects.

## Where things are

`ROADMAP.md` is still the plan. Phase 0 and 2a are done; Phase 1 (defect debt) is in progress.
`V0.2-PLAN.md` (on `fix/shelter-diagnostic`, not yet on trunk) is the new multi-version plan.

**The regression net, unchanged and still the thing to rely on:**

- `./gradlew :test` — 115 tests green (was 108; `ToolSetTest` added 7)
- Curated in-game suite — **33/33 across three clients, 205s** (baseline 208s, no drift)
- `scripts/testing/diff_baseline.py` compares a run to `scripts/testing/baseline.json`

## Released: v0.1.1

https://github.com/BlueMonkey262/baritone/releases/tag/v0.1.1 — pre-release, 12 jars, all four
loaders. Still named **Continuo**; Eli chose to defer the Tenor rename ("early access, just me and a
friend, building continuo is okay until the next release").

Headline change: **`itemSaver` now actually stops tools breaking.** Upstream's javadoc promised it
and the code only filtered slot selection. Enforced in `BlockBreakHelper#tick`, the single choke
point every block break passes through. See `FORK-NOTES.md` §2a for the full reasoning.

`26.1.2_copy` is running this build (`continuo-standalone-fabric-0.1.1.jar`, verified single jar,
md5 matches `dist/`).

## Branches

| Branch | State |
|---|---|
| `shulker-restock` | **Trunk.** `35881fca`. Clean. No `main` exists in this fork |
| `fix/shelter-diagnostic` | 2 commits, **local only**. Shelter message fix + V0.2 plan + ROADMAP entry |
| `phase-1/defect-triage` | Pushed. Holds `ee9107b0` (Tenor rename) **and duplicates** of the two itemSaver commits under old SHAs — backup only, do not merge as-is |
| `tenor/1.21.11` | Being created by Codex right now — see below |

**The rename, when you want it:** cherry-pick only `ee9107b0` onto a fresh branch off trunk. Do not
merge `phase-1/defect-triage`; it would replay the itemSaver change on top of itself.

## In flight: Codex is porting to 1.21.11

`codex-baritone-3` is running step 2 of `V0.2-PLAN.md` — port the fork to 1.21.11 on branch
`tenor/1.21.11`. Brief is in `CODEX-TASK-1.21.11.md` (untracked, repo root, delete when done).

Gates it must pass: `./gradlew :test` green, `./gradlew build` all four loaders. It was told not to
push, not to touch other branches, and not to change behaviour. **Verify its report against the
diff** — see the process note at the bottom.

The specific thing to check: our code was written for Java 25 and 1.21.11 builds on Java 21. Whether
we use any Java 22+ construct is unknown, and finding out is part of that task.

## v0.2 — multi-version

Full plan in `V0.2-PLAN.md`. The load-bearing measurements:

- Our fork vs `upstream/26.1`: 90 files, **+16,025 / −152** — almost entirely new files, which is
  why version ports barely touch us
- `upstream/1.21.11` → `26.1`: 35 files, +93/−97
- `upstream/26.1` → `26.2`: 23 files, +135/−168
- **Only 3 files collide per port**, and the changes are mechanical API renames

Scope: **1.21.11, 26.1.2, 26.2**. 26.3 is snapshot-only (Mojang manifest: latest release is 26.2,
latest snapshot 26.3-snapshot-6), so it gets a tracking branch and no tag.

**Standing decision: we do our own version ports**, rather than waiting on upstream branches or PRs.
Upstream's ports stay a reference we may read and copy freely — same LGPL, same project — but not a
dependency. Upstream PR #5076 is a complete four-loader 26.2 port and is worth diffing against as a
check. (An earlier worry that 26.2 Forge might not exist upstream was wrong.)

Two open questions in §9: the tag scheme (`v0.2.0-mc26.2` recommended for sortability, vs
`v0.2-26.2` as first proposed), and whether to fold the Tenor rename into step 1.

## Open work

- **`FarmProcess` gap** — it forces `CLICK_LEFT` and gets `itemSaver`'s suppression but not the
  fetch-or-stop escalation, so with the setting on it would stall quietly. Narrow (crops need no
  tool) but real, and shipped in v0.1.1.
- **No in-game coverage for `itemSaver`** — the 33/33 run proves no regression, but the setting
  defaults off and no scenario enables it, so the new path has never run in a world. A scenario
  staging a near-broken pickaxe plus a stocked box is the missing piece.
- **Raw ore and flint deposit** — requested from live play, recorded in `ROADMAP.md` Phase 4. Not a
  one-liner: the `BlockItem` test in `RestockProcess#isJunk` is what stops a deposit trip filing away
  diamonds and totems, so it needs an explicit bulk-item allowance, not a loosening.
- **`DEFECTS.md`** — M2, L3 and L4 were closed this session. Still open: H4 residual, H6/H8
  residuals, U-local-01, U-local-03, F1.

## Process notes worth keeping

**Do not paste multi-line prompts into Codex over tmux.** It killed the session outright — fresh
banner, new session id, prompt never ran. Write the brief to a file in the repo and send a one-line
prompt pointing at it, then send Enter as a separate keystroke. That works reliably. The old
`tmux load-buffer` + `paste-buffer -p` recipe is no longer dependable at this size.

**Watch for compound shell commands where an early step fails.** A `gh pr create` failed on a missing
`--body` and the `git push --delete` on the next line ran anyway, deleting the only branch holding
the full stack before anything had merged. Recovered only because the commit SHAs had been printed
moments earlier and nothing had run `git gc`. Print SHAs before destructive operations.

**Stacked PRs do not retarget unless you delete the base branch.** All three PRs merged
"successfully" and trunk still had only the first one's content — each merge landed in its own base
branch. Verify trunk after merging a stack rather than assuming.

**Verify what agents report.** Codex's sweep this session was accurate and genuinely useful — it
found the `BlockBreakHelper` choke point that turned an eight-call-site change into a single guard —
but every load-bearing claim was checked against the source before being acted on. Keep doing that.

## Housekeeping

- `CODEX-TASK-1.21.11.md` — untracked, delete once the port lands
- Stale PrismLauncher clones `baritone-testing(1)`, `(2)`, `-4` — still there, several hundred MB
- An orphaned Minecraft JVM was killed this session (27 hours old, 5.2 GB resident). Worth checking
  for others; they do not reliably exit
