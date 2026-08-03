# Wave 1 notes

`restock-switch-cancels-old-path` — covers M2 by requiring a failed first candidate, a fallback visit, material entering player inventory, and resumed placement; live verification was not run, and the slab refusal plus shulker NBT are flagged fixture guesses.

`restock-open-container-one-shot` — covers H7 by proving a real restock and completed placement without opening the adjacent door; live verification was not run, and the door adjacency is a flagged interaction-fixture guess.

`dump-reindex-after-deposit` — replacement for the timeout-race entry, covers live index refresh by requiring two cleared work segments, two inventory reductions, and visits to both depots; live verification was not run, and the 26-stack shulker payload/NBT and seeded index are flagged version-sensitive guesses because container contents are not read.

`restock-extra-stacks-zero` — replacement for the unavailable re-index phase, covers exact non-stack-aligned fetching by requiring exactly three items ever carried and zero surplus after placement; live verification was not run, and the shulker NBT is a flagged version-sensitive guess.

`shelter-preferred-rally-unload` — covers UB3 by requiring hostile damage, arrival at the selected rally box, an inventory reduction there, and resumed work without visiting the alternate depot; live verification was not run, and zombie timing is a flagged determinism guess.

`shelter-sleep-attempt-cap` — covers UB1 by counting genuine refused-bed use attempts and requiring exactly two followed by quiet bounded waiting; live verification was not run, and mob proximity/refusal timing is a flagged determinism guess.

`dump-active-substitute-kept` — covers H10 by requiring a dirt substitute needed by both targets to survive unloading while unrelated rubble is deposited; live verification was not run, and the assumption that the dedicated unload is reached before placement is flagged.

`itemsaver-all-hotbar-spent` — covers the all-hotbar-spent slot-zero fallback by requiring all nine tools and the mine target to remain unchanged while the mine stops; live verification was not run.

`builder-orient-timeout-independent-target` — covers the independent-target half of the orientation timeout fix by requiring a correct neighboring stair while the sealed target remains air; live verification was not run, and the bedrock cage’s impossibility is a flagged pathing-fixture guess.

`itemsaver-traverse-obstacle` — replacement for the unavailable auto-eat mining phase, covers the next feasible item-saver movement branch by requiring a detour to the goal with the obstruction and spent tool unchanged; live verification was not run, and successful detour selection is a flagged pathing-fixture guess.

Skipped from the ten-item list: `dump-deposit-timeout-quarantine` needs deterministic delayed open/sync packets; `restock-reindex-clears-giveup` needs a post-start command or phase transition; and `autoeat-mining-waits` needs deterministic hunger, item-use completion, and per-tick use/attack snapshots. The harness has none of those capabilities, so each was replaced by the next feasible case in its functional section. No Minecraft or in-game scenario suite was run; the Gradle compile was attempted with a temporary cache but stopped during project configuration on the environment disk quota before Java compilation.
