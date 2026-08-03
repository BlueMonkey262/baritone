/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testing.scenario;

/** The opportunistic restock dump may make room, but must preserve schematic material. */
public final class DumpJunkOnlyScenario extends AbstractRestockDumpScenario {

    @Override
    public String name() {
        return "dump-junk-only";
    }

    @Override
    public String description() {
        return "Deposit unwanted blocks during restocking while keeping white-concrete build material";
    }

    @Override
    protected boolean keepShulker() {
        return false;
    }
}
