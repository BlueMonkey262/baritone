/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testing.scenario;

/** A shulker box is a block item, but it is equipment/storage rather than rubble. */
public final class DumpNeverStowsShulkersScenario extends AbstractRestockDumpScenario {

    @Override
    public String name() {
        return "dump-never-stows-shulkers";
    }

    @Override
    public String description() {
        return "Deposit unwanted blocks during restocking without ever stowing a carried shulker box";
    }

    @Override
    protected boolean keepShulker() {
        return true;
    }

    @Override
    protected String successMessage(int dumped, int white) {
        return "deposited " + dumped + " unwanted block items, retained " + white
                + " white concrete, and kept the blue shulker box in inventory";
    }
}
