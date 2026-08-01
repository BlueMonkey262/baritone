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

package baritone.testing.scenario.gen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Expands the current vanilla block registry into stable, one-state placement cases.
 *
 * <p>This first catalog intentionally has no rules for slabs, doors, beds, fences, panes, walls,
 * or other families whose half/connection/open/lit/waterlogged state is neighbour-, event-, or
 * fluid-derived; those states will be added only with proven placement geometry.</p>
 */
public final class PlacementCatalog {

    private static final List<PlacementRule> RULES = List.of(
            new PlacementRules.StairsRule(),
            new PlacementRules.PillarsRule(),
            new PlacementRules.ObserversRule(),
            new PlacementRules.HoppersRule(),
            new PlacementRules.PistonsRule(),
            new PlacementRules.DispensersRule()
    );

    private PlacementCatalog() {}

    public static List<PlacementCase> all() {
        List<PlacementCase> cases = new ArrayList<>();
        for (PlacementRule rule : RULES) {
            BuiltInRegistries.BLOCK.stream()
                    .filter(rule::accepts)
                    .sorted(Comparator.comparing(PlacementCatalog::blockId))
                    .forEach(block -> cases.addAll(rule.enumerate(block)));
        }
        cases.sort(Comparator.comparing(PlacementCase::name));

        Set<String> names = new HashSet<>();
        for (PlacementCase placementCase : cases) {
            if (!names.add(placementCase.name())) {
                throw new IllegalStateException("duplicate generated placement case: " + placementCase.name());
            }
        }
        printSummary(cases);
        return List.copyOf(cases);
    }

    private static String blockId(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "" : id.toString();
    }

    private static void printSummary(List<PlacementCase> cases) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlacementRule rule : RULES) {
            counts.put(rule.family(), 0);
        }
        for (PlacementCase placementCase : cases) {
            counts.compute(placementCase.family(), (family, count) -> count + 1);
        }
        System.out.println("[testing] generated placement cases: " + cases.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            System.out.println("[testing] generated placement cases " + entry.getKey() + ": " + entry.getValue());
        }
    }
}
