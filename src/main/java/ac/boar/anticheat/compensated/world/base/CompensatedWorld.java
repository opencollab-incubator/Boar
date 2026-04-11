package ac.boar.anticheat.compensated.world.base;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.block.impl.BedBlockState;
import ac.boar.anticheat.data.block.impl.HoneyBlockState;
import ac.boar.anticheat.data.block.impl.SlimeBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.geyser.BoarChunk;
import ac.boar.anticheat.util.geyser.BoarChunkSection;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import it.unimi.dsi.fastutil.longs.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.entity.EntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;

import java.util.*;

@RequiredArgsConstructor
@Setter
@Getter
public class CompensatedWorld {
    private final BoarPlayer player;
    private final Long2ObjectMap<BoarChunk> chunks = new Long2ObjectOpenHashMap<>();

    private BedrockDimension dimension;

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
        final Entity entity = player.getSession().getEntityCache().getEntityByGeyserId(runtimeId);
        if (entity == null || entity.getDefinition() == null || runtimeId == player.runtimeEntityId) {
            return null;
        }

        final EntityDefinition<?> definition = entity.getDefinition();
        boolean affectedByOffset = definition.entityType() == EntityType.PLAYER || definition.identifier().equalsIgnoreCase("minecraft:boat") || definition.identifier().equalsIgnoreCase("minecraft:chest_boat");

        player.sendLatencyStack();
        final EntityCache cache = new EntityCache(player, definition.entityType(), definition, runtimeId);
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
                column[(y - getMinY()) >> 4] = palette = new BoarChunkSection(this.player.BEDROCK_AIR);
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
        BlockState state = BlockState.of(getBlockAt(x, y, z, layer));
        if (state.is(Blocks.HONEY_BLOCK)) {
            return new HoneyBlockState(state, Vector3i.from(x, y, z), layer);
        } else if (state.is(Blocks.SLIME_BLOCK)) {
            return new SlimeBlockState(state, Vector3i.from(x, y, z), layer);
        } else if (state.block().toString().contains("_bed")) { // nasty hack, but works!
            return new BedBlockState(state, Vector3i.from(x, y, z), layer);
        }

        return new BoarBlockState(state, Vector3i.from(x, y, z), layer);
    }

    public int getRawBlockAt(int x, int y, int z, int layer) {
        BoarChunkSection[] column = this.getChunkSections(x >> 4, z >> 4);
        if (column == null) {
            return player.BEDROCK_AIR;
        }

        if (y < getMinY() || ((y - getMinY()) >> 4) > column.length - 1) {
            // Y likely goes above or below the height limit of this world
            return player.BEDROCK_AIR;
        }

        BoarChunkSection chunk = column[(y - getMinY()) >> 4];
        if (chunk != null) {
            try {
                int id = chunk.getFullBlock(x & 0xF, y & 0xF, z & 0xF, layer);
                return id == Integer.MIN_VALUE ? player.BEDROCK_AIR : id;
            } catch (Exception e) {
//                e.printStackTrace();
                return player.BEDROCK_AIR;
            }
        }

        return player.BEDROCK_AIR;
    }

    public int getBlockAt(int x, int y, int z, int layer) {
        return player.bedrockBlockToJava.getOrDefault(this.getRawBlockAt(x, y, z, layer), 0);
    }

    public BlockEntityInfo getBlockEntity(int x, int y, int z) {
        final BoarChunk chunk = this.getChunk(x >> 4, z >> 4);
        if (chunk == null) {
            return null;
        }

        for (BlockEntityInfo info : chunk.blockEntities()) {
            if (info.getX() == x && info.getY() == y && info.getZ() == z) {
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