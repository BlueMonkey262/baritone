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

package baritone.cache;

import baritone.api.cache.IRestockBox;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single registered shulker box. Identity is its position, so a box can never be registered
 * twice at the same coordinates.
 *
 * @see IRestockBox
 */
public class RestockBox implements IRestockBox {

    private final BetterBlockPos location;
    private final long creationTimestamp;

    private long lastIndexed;
    private Map<Item, Integer> contents;
    private boolean missing;

    public RestockBox(BetterBlockPos location, long creationTimestamp) {
        this(location, creationTimestamp, 0, new HashMap<>(), false);
    }

    public RestockBox(BetterBlockPos location, long creationTimestamp, long lastIndexed, Map<Item, Integer> contents, boolean missing) {
        this.location = location;
        this.creationTimestamp = creationTimestamp;
        this.lastIndexed = lastIndexed;
        this.contents = new HashMap<>(contents);
        this.missing = missing;
    }

    @Override
    public BetterBlockPos getLocation() {
        return this.location;
    }

    @Override
    public long getCreationTimestamp() {
        return this.creationTimestamp;
    }

    @Override
    public long getLastIndexed() {
        return this.lastIndexed;
    }

    @Override
    public Map<Item, Integer> getContents() {
        return Collections.unmodifiableMap(this.contents);
    }

    @Override
    public boolean isMissing() {
        return this.missing;
    }

    void setContents(Map<Item, Integer> contents, long timestamp) {
        this.contents = new HashMap<>(contents);
        this.lastIndexed = timestamp;
        // successfully reading the contents proves the box exists, so clear any stale missing flag
        this.missing = false;
    }

    void setMissing(boolean missing) {
        this.missing = missing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RestockBox)) {
            return false;
        }
        return this.location.equals(((RestockBox) o).location);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.location);
    }

    @Override
    public String toString() {
        return String.format("RestockBox{%s, %d items, %s}", this.location, this.contents.size(), this.missing ? "missing" : "ok");
    }
}
