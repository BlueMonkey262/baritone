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

/** Configured throwaway material must retain one stack during a dedicated unload. */
public final class DumpKeepThrowawaysScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-keep-throwaways";
    }

    @Override
    public String description() {
        return "Unload rubble while retaining one configured cobblestone throwaway stack";
    }

    @Override
    protected int[] boxXs() {
        return new int[]{2};
    }

    @Override
    protected int keepThrowawayStacks() {
        return 1;
    }

    @Override
    protected boolean requiresKeptThrowaway() {
        return true;
    }
}
