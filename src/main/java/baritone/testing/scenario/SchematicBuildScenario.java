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

package baritone.testing.scenario;

import baritone.testing.TestArena;

import java.util.HashMap;
import java.util.Map;

/**
 * The control case for everything else that builds: enough material in hand, nothing to fetch.
 * <p>
 * Worth running first even though it tests no fork-specific code. If this fails, a restock failure
 * afterwards says nothing about restocking.
 */
public final class SchematicBuildScenario extends AbstractBoxBuildScenario {

    @Override
    public String name() {
        return "build-schematic";
    }

    @Override
    public String description() {
        return "Build a 5x5x3 ring from materials already in the inventory";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        // No boxes are registered in this arena, but leaving restocking on would mean a failure
        // here could be either the builder's or the restock hook's. Off, it can only be one.
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    protected void stageSupplies(TestArena arena) {
        // Comfortably more than the 48 needed: this scenario is not about running out.
        arena.command("give @s " + MATERIAL_ID + " 128");
    }
}
