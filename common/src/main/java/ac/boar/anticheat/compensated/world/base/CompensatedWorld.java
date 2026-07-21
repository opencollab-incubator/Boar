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
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Setter
@Getter
public class CompensatedWorld {
    private final BoarPlayer player;
    private final Long2ObjectMap<BoarChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final LongSet exemptedChunks = new LongOpenHashSet();

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

    private int viewDistance = 16;
    private long lastChunkClean = Long.MIN_VALUE;

    public void setViewDistance(int viewDistance) {
        // The client always uses the server chunk view distance plus 1 unconditionally regardless of the radius it requested. It's also why we can get away with
        // ignoring RequestChunkRadius packets from the client.
        this.viewDistance = Math.max(1, viewDistance + 1);
    }

    public void cleanChunksAtPlayerPosition() {
        if (this.player == null) {
            return;
        }

        final int playerChunkX = GenericMath.floor(this.player.position.x) >> 4;
        final int playerChunkZ = GenericMath.floor(this.player.position.z) >> 4;
        final long playerChunk = MathUtil.chunkPositionToLong(playerChunkX, playerChunkZ);
        if (playerChunk == this.lastChunkClean) {
            return;
        }

        this.lastChunkClean = playerChunk;
        this.yeetOutOfRangeChunks();
    }

    public void yeetOutOfRangeChunks() {
        this.chunks.keySet().removeIf(key -> {
            final int chunkX = (int) key, chunkZ = (int) (key >> 32);
            final boolean inView = !this.isOutOfRadius(chunkX << 4, chunkZ << 4);

            // Keep a new chunk until the client position enters its view range, can happen on some server software that sends chunks first
            // before sending the teleport packet.
            if (this.exemptedChunks.contains(key)) {
                if (inView) {
                    this.exemptedChunks.remove(key);
                }
                return false;
            }

            return !inView;
        });
    }

    public boolean isOutOfRadius(int blockX, int blockZ) {
        if (this.player == null) {
            return false;
        }

        final int chunkX = blockX >> 4;
        final int chunkZ = blockZ >> 4;
        final int playerChunkX = GenericMath.floor(this.player.unvalidatedPosition.x) >> 4;
        final int playerChunkZ = GenericMath.floor(this.player.unvalidatedPosition.z) >> 4;
        return !isChunkInView(this.viewDistance, chunkX, chunkZ, playerChunkX, playerChunkZ);
    }

    // translated from GridArea::isChunkInCircle accounting for horizontal distance only and a bit extra lenience
    static boolean isChunkInView(int viewDistance, int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        final long dx = Math.abs((long) playerChunkX - chunkX);
        final long dz = Math.abs((long) playerChunkZ - chunkZ);

        // The client clips its circular view to this square boundary
        final long maxCoordinate = viewDistance + 1L;
        if (dx > maxCoordinate || dz > maxCoordinate) {
            return false;
        }

        final long distanceSquared = dx * dx + dz * dz;
        final float threshold = viewDistance + 1.5F + 1.7320508F;
        return distanceSquared < threshold * threshold;
    }

    public void put(int x, int z, BoarChunkSection[] chunks) {
        long chunkPosition = MathUtil.chunkPositionToLong(x, z);
        this.chunks.put(chunkPosition, new BoarChunk(chunks, new ArrayList<>()));
        this.updateChunkExemption(chunkPosition, x, z);
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
            final long chunkPosition = MathUtil.chunkPositionToLong(chunkX, chunkZ);
            this.chunks.put(chunkPosition, new BoarChunk(sections, new ArrayList<>()));
            this.updateChunkExemption(chunkPosition, chunkX, chunkZ);
            return;
        }

        chunk.sections()[sectionY] = section;
    }

    private void updateChunkExemption(long chunkPosition, int chunkX, int chunkZ) {
        if (this.isOutOfRadius(chunkX << 4, chunkZ << 4)) {
            this.exemptedChunks.add(chunkPosition);
        } else {
            this.exemptedChunks.remove(chunkPosition);
        }
    }

    public void clearChunks() {
        this.chunks.clear();
        this.exemptedChunks.clear();
        this.lastChunkClean = Long.MIN_VALUE;
    }

    public boolean isChunkLoaded(int blockX, int blockZ) {
        return this.getChunk(blockX >> 4, blockZ >> 4) != null;
    }

    // Resolve the loaded-chunk lookup from a player/entity position. A plain (int) cast rounds towards
    // zero instead of flooring, so at negative coordinates it resolves to the neighbouring chunk in the
    // one-block strip next to every chunk border (x or z in -1..0, -17..-16, ...) - prediction could then
    // run against a not-yet-loaded chunk and rewind the player. Floor, matching Geyser and vanilla.
    public boolean isChunkLoadedAt(float x, float z) {
        return this.isChunkLoaded(GenericMath.floor(x), GenericMath.floor(z));
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
