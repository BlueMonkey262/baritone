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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Convenience wrapper over the {@code torchGrid} and {@code torchGridSpacing} settings, which are
 * also reachable through {@code set} like any other setting.
 */
public class TorchGridCommand extends Command {

    private static final int MAX_SPACING = 64;

    public TorchGridCommand(IBaritone baritone) {
        super(baritone, "torchgrid");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny()) {
            logStatus();
            return;
        }
        String arg = args.getString();
        if (arg.equalsIgnoreCase("on") || arg.equalsIgnoreCase("true")) {
            Baritone.settings().torchGrid.value = true;
        } else if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("false")) {
            Baritone.settings().torchGrid.value = false;
        } else if (arg.equalsIgnoreCase("toggle")) {
            Baritone.settings().torchGrid.value = !Baritone.settings().torchGrid.value;
        } else {
            final int spacing;
            try {
                spacing = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                throw new CommandInvalidStateException("Expected a spacing, or on/off/toggle, not " + arg);
            }
            if (spacing < 1 || spacing > MAX_SPACING) {
                throw new CommandInvalidStateException("Spacing must be between 1 and " + MAX_SPACING);
            }
            // setting a spacing is only ever done because you want the grid, so save the second command
            Baritone.settings().torchGridSpacing.value = spacing;
            Baritone.settings().torchGrid.value = true;
        }
        logStatus();
    }

    private void logStatus() {
        if (Baritone.settings().torchGrid.value) {
            logDirect(String.format(
                    "Torch grid is on, every %d blocks. cleararea will light the floor.",
                    Baritone.settings().torchGridSpacing.value
            ));
        } else {
            logDirect(String.format(
                    "Torch grid is off (spacing would be %d).",
                    Baritone.settings().torchGridSpacing.value
            ));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append("on", "off", "toggle")
                    .filterPrefix(args.getString())
                    .stream();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Place torches while clearing an area";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Controls the grid of torches that sel cleararea leaves behind on the floor of the",
                "region it clears, so you don't hollow out a room and then have to light it by hand.",
                "",
                "The grid is centred in each selection, so a region narrower than the spacing still",
                "gets a torch in the middle. You need torches in your inventory; the builder will",
                "pause and say so if you run out.",
                "",
                "These are the torchGrid and torchGridSpacing settings, so 'set' works too.",
                "",
                "Usage:",
                "> torchgrid - Show whether the grid is on, and its spacing.",
                "> torchgrid <spacing> - Set the spacing in blocks (1-" + MAX_SPACING + ") and turn the grid on.",
                "> torchgrid on/off - Turn the grid on or off, keeping the spacing.",
                "> torchgrid toggle - Flip it."
        );
    }
}
