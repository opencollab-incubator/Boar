package ac.boar.anticheat.compensated.world.base;

import ac.boar.anticheat.Boar;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Setter
@Getter
public class CompensatedWorld {
    private static final int INITIAL_CHUNK_CLEANUP_DELAY_TICKS = 300;

    private final BoarPlayer player;
    private final Long2ObjectMap<BoarChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final LongSet exemptedChunks = new LongOpenHashSet();
    @Getter(AccessLevel.NONE)
    private final Long2ObjectMap<Map<PendingBlockUpdateKey, Integer>> pendingBlockUpdates = new Long2ObjectOpenHashMap<>();

    private Dimension dimension;

    private final Long2ObjectMap<EntityCache> entities = new Long2ObjectOpenHashMap<>();
    // Entities the server has spawned but the client has not acknowledged yet. An AddEntityAck
    // promotes them into the visible map, so checks and prediction stay in sync with the client.
    private final Long2ObjectMap<EntityCache> pendingEntities = new Long2ObjectOpenHashMap<>();
    private final Map<Long, Long> uniqueIdToRuntimeId = new HashMap<>();

    public void removeEntity(final long uniqueId) {
        final Long key = this.uniqueIdToRuntimeId.remove(uniqueId);
        if (key == null) {
            return;
        }

        this.entities.remove((long) key);
        this.pendingEntities.remove((long) key);
    }

    public EntityCache getEntity(long id) {
        return this.entities.get(id);
    }

    public Optional<EntityCache> fetchEntity(long id) {
        EntityCache entity = this.entities.get(id);
        return entity == null ? Optional.empty() : Optional.of(entity);
    }

    /**
     * Get an entity the server has spawned, even when the client has not acknowledged the spawn
     * yet. Only outbound packet handlers must use this — they mirror what the server has sent so
     * far. Checks and prediction must use {@link #getEntity(long)} so they only see entities that
     * exist on the client.
     */
    public EntityCache getTrackedEntity(long id) {
        final EntityCache entity = this.entities.get(id);
        return entity != null ? entity : this.pendingEntities.get(id);
    }

    public Optional<EntityCache> fetchTrackedEntity(long id) {
        return Optional.ofNullable(this.getTrackedEntity(id));
    }

    /**
     * Make a pending entity visible. Called when the client acknowledges the spawn packet.
     */
    public void promoteEntity(final long runtimeId) {
        final EntityCache entity = this.pendingEntities.remove(runtimeId);
        if (entity != null) {
            this.entities.put(runtimeId, entity);
        }
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

        this.pendingEntities.put(runtimeId, cache);
        this.uniqueIdToRuntimeId.put(uniqueId, runtimeId);

        return cache;
    }

    private int viewDistance = 16;
    private long lastChunkClean = Long.MIN_VALUE;
    private int chunkCleanupDelayTicks = INITIAL_CHUNK_CLEANUP_DELAY_TICKS;

    public void setViewDistance(int viewDistance) {
        // The client always uses the server chunk view distance plus 1 unconditionally regardless of the radius it requested. It's also why we can get away with
        // ignoring RequestChunkRadius packets from the client.
        this.viewDistance = Math.max(1, viewDistance + 1);
    }

    public void cleanChunksAtPlayerPosition() {
        if (this.player == null) {
            return;
        }

        if (this.chunkCleanupDelayTicks > 0) {
            this.chunkCleanupDelayTicks--;
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
        final BoarChunkSection[] sections = Arrays.copyOf(chunks, chunks.length);
        this.chunks.put(chunkPosition, new BoarChunk(sections, new ArrayList<>(), true, this.player.tick));
        this.updateChunkExemption(chunkPosition, x, z);
        this.applyPendingBlockUpdates(chunkPosition, sections);
    }

    public void updateSection(int chunkX, int chunkZ, int sectionY, BoarChunkSection section) {
        final int sectionCount = this.dimension.height() >> 4;
        if (sectionY < 0 || sectionY >= sectionCount) {
            return;
        }

        final long chunkPosition = MathUtil.chunkPositionToLong(chunkX, chunkZ);
        BoarChunk chunk = this.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            final BoarChunkSection[] sections = new BoarChunkSection[sectionCount];
            sections[sectionY] = section;
            chunk = new BoarChunk(sections, new ArrayList<>(), sectionCount == 1, this.player.tick);
            this.chunks.put(chunkPosition, chunk);
        } else {
            chunk.setSection(section, sectionY);
        }

        this.updateChunkExemption(chunkPosition, chunkX, chunkZ);
        this.applyPendingBlockUpdates(chunkPosition, chunk.sections(), sectionY);
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
        this.pendingBlockUpdates.clear();
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
        if (this.dimension == null || y < this.getMinY() || y >= this.getHeightY()) {
            return;
        }

        final BoarChunkSection[] column = this.getChunkSections(x >> 4, z >> 4);
        if (column == null) {
            this.queuePendingBlockUpdate(x, y, z, layer, block);
            return;
        }

        this.applyBlockUpdate(column, x, y, z, layer, block);
    }

    private void queuePendingBlockUpdate(int x, int y, int z, int layer, int block) {
        final long chunkPosition = MathUtil.chunkPositionToLong(x >> 4, z >> 4);
        Map<PendingBlockUpdateKey, Integer> updates = this.pendingBlockUpdates.get(chunkPosition);
        if (updates == null) {
            updates = new HashMap<>();
            this.pendingBlockUpdates.put(chunkPosition, updates);
        }
        updates.put(new PendingBlockUpdateKey(x, y, z, layer), block);
    }

    private void applyPendingBlockUpdates(long chunkPosition, BoarChunkSection[] column) {
        final Map<PendingBlockUpdateKey, Integer> updates = this.pendingBlockUpdates.remove(chunkPosition);
        if (updates == null) {
            return;
        }

        for (Map.Entry<PendingBlockUpdateKey, Integer> entry : updates.entrySet()) {
            final PendingBlockUpdateKey key = entry.getKey();
            this.applyBlockUpdate(column, key.x(), key.y(), key.z(), key.layer(), entry.getValue());
        }
    }

    private void applyPendingBlockUpdates(long chunkPosition, BoarChunkSection[] column, int sectionY) {
        final Map<PendingBlockUpdateKey, Integer> updates = this.pendingBlockUpdates.get(chunkPosition);
        if (updates == null) {
            return;
        }

        final Iterator<Map.Entry<PendingBlockUpdateKey, Integer>> iterator = updates.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<PendingBlockUpdateKey, Integer> entry = iterator.next();
            final PendingBlockUpdateKey key = entry.getKey();
            if ((key.y() - this.getMinY()) >> 4 != sectionY) {
                continue;
            }

            this.applyBlockUpdate(column, key.x(), key.y(), key.z(), key.layer(), entry.getValue());
            iterator.remove();
        }

        if (updates.isEmpty()) {
            this.pendingBlockUpdates.remove(chunkPosition);
        }
    }

    private void applyBlockUpdate(BoarChunkSection[] column, int x, int y, int z, int layer, int block) {
        final int index = (y - this.getMinY()) >> 4;
        if (index < 0 || index >= column.length) {
            return;
        }

        BoarChunkSection section = column[index];
        if (section == null) {
            if (!this.player.mappingInfo.airIds().contains(block)) {
                section = new BoarChunkSection(this.player.mappingInfo.airId());
                column[index] = section;
            } else {
                return;
            }
        } else if (section.isShared()) {
            // COW so that we never modify a section shared with other players worlds
            section = section.copy();
            column[index] = section;
        }

        section.setFullBlock(x & 0xF, y & 0xF, z & 0xF, layer, block);
    }

    private record PendingBlockUpdateKey(int x, int y, int z, int layer) {
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
                Boar.getInstance().getPlatform().logger().error(player.getSession().name() + ": failed to read block at " + x + "," + y + "," + z + " layer=" + layer, e);
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
