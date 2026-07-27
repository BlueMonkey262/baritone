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
import baritone.api.cache.IRestockBoxCollection;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Restock boxes for a world, persisted to disk in the same style as {@link WaypointCollection}.
 * <p>
 * Items are stored by registry key rather than numeric id, so the file stays valid across
 * Minecraft updates and mod changes. An entry whose item no longer resolves is dropped on load
 * instead of failing the whole file.
 *
 * @see IRestockBoxCollection
 */
public class RestockBoxCollection implements IRestockBoxCollection {

    /**
     * Magic value to detect invalid restock box files
     */
    private static final long RESTOCK_MAGIC_VALUE = 121977993585L; // one more than the waypoint one

    private final Path directory;
    private final Map<BetterBlockPos, RestockBox> boxes;

    RestockBoxCollection(Path directory) {
        this.directory = directory;
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException ignored) {}
        }
        this.boxes = new HashMap<>();
        load();
    }

    private Path file() {
        return this.directory.resolve("boxes.mp4");
    }

    private synchronized void load() {
        Path fileName = file();
        if (!Files.exists(fileName)) {
            return;
        }

        try (
                FileInputStream fileIn = new FileInputStream(fileName.toFile());
                BufferedInputStream bufIn = new BufferedInputStream(fileIn);
                DataInputStream in = new DataInputStream(bufIn)
        ) {
            long magic = in.readLong();
            if (magic != RESTOCK_MAGIC_VALUE) {
                throw new IOException("Bad magic value " + magic);
            }

            long length = in.readLong();
            while (length-- > 0) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                long creationTimestamp = in.readLong();
                long lastIndexed = in.readLong();
                boolean missing = in.readBoolean();

                Map<Item, Integer> contents = new HashMap<>();
                int entries = in.readInt();
                while (entries-- > 0) {
                    String key = in.readUTF();
                    int count = in.readInt();
                    // an item that no longer exists (removed mod, renamed block) is simply skipped;
                    // the box stays registered and will be re-indexed the next time we open it
                    Identifier id = Identifier.tryParse(key);
                    if (id == null) {
                        continue;
                    }
                    Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                    if (item != null) {
                        contents.put(item, count);
                    }
                }

                BetterBlockPos pos = new BetterBlockPos(x, y, z);
                this.boxes.put(pos, new RestockBox(pos, creationTimestamp, lastIndexed, contents, missing));
            }
        } catch (IOException ignored) {}
    }

    private synchronized void save() {
        Path fileName = file();
        try (
                FileOutputStream fileOut = new FileOutputStream(fileName.toFile());
                BufferedOutputStream bufOut = new BufferedOutputStream(fileOut);
                DataOutputStream out = new DataOutputStream(bufOut)
        ) {
            out.writeLong(RESTOCK_MAGIC_VALUE);
            out.writeLong(this.boxes.size());
            for (RestockBox box : this.boxes.values()) {
                out.writeInt(box.getLocation().getX());
                out.writeInt(box.getLocation().getY());
                out.writeInt(box.getLocation().getZ());
                out.writeLong(box.getCreationTimestamp());
                out.writeLong(box.getLastIndexed());
                out.writeBoolean(box.isMissing());

                Map<Item, Integer> contents = box.getContents();
                out.writeInt(contents.size());
                for (Map.Entry<Item, Integer> entry : contents.entrySet()) {
                    out.writeUTF(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
                    out.writeInt(entry.getValue());
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public synchronized IRestockBox addBox(BetterBlockPos pos) {
        RestockBox existing = this.boxes.get(pos);
        if (existing != null) {
            return existing;
        }
        RestockBox box = new RestockBox(pos, System.currentTimeMillis());
        this.boxes.put(pos, box);
        save();
        return box;
    }

    @Override
    public synchronized boolean removeBox(BetterBlockPos pos) {
        if (this.boxes.remove(pos) != null) {
            save();
            return true;
        }
        return false;
    }

    @Override
    public synchronized IRestockBox getBox(BetterBlockPos pos) {
        return this.boxes.get(pos);
    }

    @Override
    public synchronized Set<IRestockBox> getAllBoxes() {
        return Collections.unmodifiableSet(new HashSet<>(this.boxes.values()));
    }

    @Override
    public synchronized void updateContents(BetterBlockPos pos, Map<Item, Integer> contents) {
        RestockBox box = this.boxes.get(pos);
        if (box == null) {
            return;
        }
        box.setContents(contents, System.currentTimeMillis());
        save();
    }

    @Override
    public synchronized void setMissing(BetterBlockPos pos, boolean missing) {
        RestockBox box = this.boxes.get(pos);
        if (box == null || box.isMissing() == missing) {
            return;
        }
        box.setMissing(missing);
        save();
    }
}
