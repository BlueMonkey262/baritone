`dump-single-shulker-trip` — protects the dedicated unload trigger and requires rubble to reach a registered box while work remains; unsure only about live-run path timing.
`dump-prefers-capacity` — protects capacity-first box selection over distance, with indexed nearly-full and empty boxes; unsure about the version-sensitive shulker item-NBT syntax already used by neighboring scenarios.
`dump-spans-boxes` — protects continuing one unload trip across multiple boxes when one box has no room left; unsure about live container update timing.
`dump-max-box-limit` — protects the per-trip box cap and verifies rubble remains carried after the cap; unsure whether the two-block clear gives the harness enough time to observe the bounded outcome on every run.
`dump-keep-throwaways` — protects retaining one configured cobblestone throwaway stack during unloading; unsure about the exact drop timing between the two clear targets.
`indexboxes-all-refresh` — protects replacing stale indexed contents and the derived free-slot estimate after `#indexboxes all`; unsure about the version-sensitive `/setblock` item-NBT form.
`missing-box-loaded-vs-unloaded` — protects flagging a broken loaded registration without flagging a distant unloaded chunk; unsure whether the fixed 1024-block control position remains outside the client’s loaded-chunk radius in every manual setup.

Entries 35, 37, and 39 were already present and registered uncurated, so they were left unchanged. No entries in 31–40 are excluded by the backlog’s “Not yet buildable” section. The in-game suite was not run, per task instructions; only `./gradlew compileJava` was used.
