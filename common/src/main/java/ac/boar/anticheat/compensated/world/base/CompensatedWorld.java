package ac.boar.anticheat.compensated.world.base;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.Dimension;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.geyser.BlockEntityInfo;
import ac.boar.anticheat.util.geyser.BoarChunk;
import ac.boar.anticheat.util.geyser.BoarChunkSection;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Setter
@Getter
public class CompensatedWorld {
    private final BoarPlayer player;
    private final Long2ObjectMap<BoarChunk> chunks = new Long2ObjectOpenHashMap<>();

    private Dimension dimension;

    private final Long2ObjectMap<EntityCache> entities = new Long2ObjectOpenHashMap<>();
    private final Map<Long, Long> uniqueIdToRuntimeId = new HashMap<>();

    public void removeEntity(final long uniqueId) {
        final Long key = this.uniqueIdToRuntimeId.remove(uniqueId);
        if (key == null) {
            return;
        }

        this.entities.remove((long) key);
    }

    public EntityCache getEntity(long id) {
        return this.entities.get(id);
    }

    public Optional<EntityCache> fetchEntity(long id) {
        EntityCache entity = this.entities.get(id);
        return entity == null ? Optional.empty() : Optional.of(entity);
    }

    public EntityCache addToCache(final BoarPlayer player, final long runtimeId, final long uniqueId) {
        EntityDefinition definition = player.getEntityAccessor().definitionByRuntimeId(runtimeId);
        if (definition == null || runtimeId == player.runtimeEntityId) {
            return null;
        }

        boolean affectedByOffset = definition.type().is(EntityTypes.PLAYER) || definition.identifier().equalsIgnoreCase("minecraft:boat") || definition.identifier().equalsIgnoreCase("minecraft:chest_boat");

        final EntityCache cache = new EntityCache(player, definition.type(), definition, runtimeId);
        cache.setAffectedByOffset(affectedByOffset);
        // Default back to default bounding box if there ain't anything.
        cache.setDimensions(EntityDimensions.fixed(definition.width(), definition.height()));

        this.entities.put(runtimeId, cache);
        this.uniqueIdToRuntimeId.put(uniqueId, runtimeId);

        return cache;
    }

    private int radius;
    private Vector3i radiusCenter;

    public void yeetOutOfRangeChunks() {
        this.chunks.keySet().removeIf(key -> {
            final int chunkX = (int) key, chunkZ = (int) (key >> 32);
            return this.isOutOfRadius(chunkX << 4, chunkZ << 4);
        });
    }

    public boolean isOutOfRadius(int chunkX, int chunkZ) {
        if (this.radiusCenter == null || this.radius <= 0) {
            return false;
        }

        Vec3 radiusCenter = new Vec3(this.radiusCenter).add(0.5f, 0.5f, 0.5f); // Properly correct eh?

        // Still unsure about this... should we get rid of chunk sections, or chunk?
        // Well since we're getting rid of chunks for now, let set the y pos to 0.
        Vec3 chunkCenter = new Vec3(chunkX + 8, 0, chunkZ + 8);
        return radiusCenter.squaredDistanceTo(chunkCenter) > this.radius * this.radius;
    }

    public void put(int x, int z, BoarChunkSection[] chunks) {
        long chunkPosition = MathUtil.chunkPositionToLong(x, z);
        this.chunks.put(chunkPosition, new BoarChunk(chunks, new ArrayList<>()));
    }

    public void updateSection(int chunkX, int chunkZ, int sectionY, BoarChunkSection section) {
        final int sectionCount = this.dimension.height() >> 4;
        if (sectionY < 0 || sectionY >= sectionCount) {
            return;
        }

        BoarChunk chunk = this.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            final BoarChunkSection[] sections = new BoarChunkSection[sectionCount];
            sections[sectionY] = section;
            this.chunks.put(MathUtil.chunkPositionToLong(chunkX, chunkZ), new BoarChunk(sections, new ArrayList<>()));
            return;
        }

        chunk.sections()[sectionY] = section;
    }

    public void removeFromCache(int x, int z) {
        this.chunks.remove(MathUtil.chunkPositionToLong(x, z));
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return this.getChunk(chunkX >> 4, chunkZ >> 4) != null;
    }

    public void updateBlock(final Vector3i position, int layer, int block) {
        this.updateBlock(position.getX(), position.getY(), position.getZ(), layer, block);
    }

    public void updateBlock(int x, int y, int z, int layer, int block) {
        final BoarChunkSection[] column = this.getChunkSections(x >> 4, z >> 4);
        if (column == null) {
            return;
        }

        if (y < getMinY() || ((y - getMinY()) >> 4) > column.length - 1) {
            // Y likely goes above or below the height limit of this world
            return;
        }

        BoarChunkSection palette = column[(y - getMinY()) >> 4];
        if (palette == null) {
            if (block != 0) {
                // A previously empty chunk, which is no longer empty as a block has been added to it
                column[(y - getMinY()) >> 4] = palette = new BoarChunkSection(this.player.mappingInfo.airId());
            } else {
                // Nothing to update
                return;
            }
        }

        palette.setFullBlock(x & 0xF, y & 0xF, z & 0xF, layer, block);
    }

    public BoarBlockState getBlockState(Mutable vector3i, int layer) {
        return getBlockState(vector3i.getX(), vector3i.getY(), vector3i.getZ(), layer);
    }

    public BoarBlockState getBlockState(Vector3i vector3i, int layer) {
        return getBlockState(vector3i.getX(), vector3i.getY(), vector3i.getZ(), layer);
    }

    public BoarBlockState getBlockState(int x, int y, int z, int layer) {
        return BoarBlockState.create(getBlockAt(x, y, z, layer), Vector3i.from(x, y, z), layer);
    }

    public int getRawBlockAt(int x, int y, int z, int layer) {
        BoarChunkSection[] column = this.getChunkSections(x >> 4, z >> 4);
        if (column == null) {
            return player.mappingInfo.airId();
        }

        if (y < getMinY() || ((y - getMinY()) >> 4) > column.length - 1) {
            // Y likely goes above or below the height limit of this world
            return player.mappingInfo.airId();
        }

        BoarChunkSection chunk = column[(y - getMinY()) >> 4];
        if (chunk != null) {
            try {
                int id = chunk.getFullBlock(x & 0xF, y & 0xF, z & 0xF, layer);
                return id == Integer.MIN_VALUE ? player.mappingInfo.airId() : id;
            } catch (Exception e) {
//                e.printStackTrace();
                return player.mappingInfo.airId();
            }
        }

        return player.mappingInfo.airId();
    }

    public int getBlockAt(int x, int y, int z, int layer) {
        return player.fromRawBlockId(this.getRawBlockAt(x, y, z, layer));
    }

    public BlockEntityInfo getBlockEntity(int x, int y, int z) {
        final BoarChunk chunk = this.getChunk(x >> 4, z >> 4);
        if (chunk == null) {
            return null;
        }

        for (BlockEntityInfo info : chunk.blockEntities()) {
            if (info.x() == x && info.y() == y && info.z() == z) {
                return info;
            }
        }

        return null;
    }

    public BoarChunk getChunk(int chunkX, int chunkZ) {
        long chunkPosition = MathUtil.chunkPositionToLong(chunkX, chunkZ);
        return this.chunks.getOrDefault(chunkPosition, null);
    }

    private BoarChunkSection[] getChunkSections(int chunkX, int chunkZ) {
        final BoarChunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        return chunk.sections();
    }

    public int getMinY() {
        return this.dimension.minY();
    }

    public int getHeightY() {
        return this.dimension.maxY();
    }
}
