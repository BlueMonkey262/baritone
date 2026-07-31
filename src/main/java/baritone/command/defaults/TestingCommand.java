/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.testing.TestingBehavior;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Drives the in-game test harness. See {@link TestingBehavior} for what it will do to your world.
 */
public class TestingCommand extends Command {

    public TestingCommand(IBaritone baritone) {
        super(baritone, "testing");
    }

    private TestingBehavior harness() {
        return ((Baritone) this.baritone).getTestingBehavior();
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        TestingBehavior harness = harness();
        String action = args.hasAny() ? args.getString().toLowerCase() : "status";

        switch (action) {
            case "status" -> {
                if (harness.isRunning()) {
                    logDirect("Running: " + harness.currentScenarioName());
                } else {
                    logDirect("Idle. Reports go to " + harness.reportDirectory());
                }
                Path last = harness.lastReport();
                if (last != null) {
                    logDirect("Last report: " + last);
                }
            }
            case "list" -> {
                logDirect("Scenarios:");
                for (String name : TestingBehavior.curatedNames()) {
                    logDirect("- " + name + ": " + TestingBehavior.describe(name));
                }
                logDirect(String.format(
                        "- fuzz-1 .. fuzz-%d: seeded obstacle courses, run by name or via a shard file",
                        TestingBehavior.fuzzCount()));
            }
            case "cancel" -> harness.cancel();
            case "autorun" -> {
                args.requireMax(0);
                try {
                    Path flag = harness.reportDirectory().resolve(TestingBehavior.AUTORUN_FLAG);
                    Files.createDirectories(flag.getParent());
                    Files.write(flag, new byte[0]);
                    logDirect("Armed. The next world you join runs the whole suite and then quits the game.");
                    logDirect("Flag file: " + flag);
                } catch (IOException e) {
                    throw new CommandInvalidStateException("could not write the autorun flag: " + e);
                }
            }
            case "all" -> {
                args.requireMax(0);
                startOrThrow(harness, TestingBehavior.curatedNames());
            }
            default -> {
                List<String> names = new ArrayList<>();
                names.add(action);
                while (args.hasAny()) {
                    names.add(args.getString().toLowerCase());
                }
                startOrThrow(harness, names);
            }
        }
    }

    private void startOrThrow(TestingBehavior harness, List<String> names) throws CommandException {
        String error = harness.start(names, false);
        if (error != null) {
            throw new CommandInvalidStateException(error);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            List<String> options = new ArrayList<>(Arrays.asList("all", "list", "status", "cancel", "autorun"));
            options.addAll(TestingBehavior.curatedNames());
            return options.stream();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Run the in-game test suite (singleplayer only)";
    }

    @Override
    public List<String> getLongDesc() {
        return Collections.unmodifiableList(Arrays.asList(
                "Runs scenarios against a live world: stages an arena with vanilla commands, turns",
                "Baritone loose on it in survival, checks the resulting world state, and writes a",
                "report to baritone/testing/.",
                "",
                "This rewrites terrain, clears your inventory and changes your gamemode. Use a world",
                "you do not care about. Singleplayer with cheats only.",
                "",
                "Usage:",
                "> testing - Show what the harness is doing",
                "> testing list - List the scenarios",
                "> testing all - Run every scenario in order",
                "> testing <name> [<name> ...] - Run specific scenarios",
                "> testing cancel - Stop the current run",
                "> testing autorun - Run the whole suite on next world join, then quit the game"
        ));
    }
}
