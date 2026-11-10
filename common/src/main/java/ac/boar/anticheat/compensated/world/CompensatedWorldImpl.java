package ac.boar.anticheat.compensated.world;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.collision.CollisionRecord;
import org.cloudburstmc.math.GenericMath;
import ac.boar.anticheat.collision.util.CuboidBlockIterator;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.world.base.CompensatedWorld;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.prediction.vehicle.FollowerHistory;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public class CompensatedWorldImpl extends CompensatedWorld {
    public CompensatedWorldImpl(BoarPlayer player) {
        super(player);
    }

    private KnownBlockReads knownBlockReads;

    /**
     * Tracks the block reads made while it is open (from trackBlockReads to close). Callers wrap anything from one collision query
     * to a whole boat tick (BoatTicker.rerun). known() is true only when at least one block was read and every read hit known chunk data
     * and a known block mapping. A collision scan that reaches an unloaded chunk or leaves the world height also makes it false.
     * It never changes what collision returns.
     */
    public final class KnownBlockReads implements AutoCloseable {
        private boolean known = true;
        private int reads;
        private final IntSet checkedRawIds = new IntOpenHashSet();

        public boolean known() {
            return this.known && this.reads > 0;
        }

        @Override
        public void close() {
            CompensatedWorldImpl.this.knownBlockReads = null;
        }
    }

    public KnownBlockReads trackBlockReads() {
        if (this.knownBlockReads != null) {
            throw new IllegalStateException("Nested block read tracking");
        }
        return this.knownBlockReads = new KnownBlockReads();
    }

    /**
     * Records one block read for the open KnownBlockReads, if any. getRawBlockAt calls it on every read. Code that reads through
     * getKnownRawBlockAt instead calls it itself. It looks up where the block data came from, so callers pass only the position and layer.
     */
    public final void observeBlockRead(int x, int y, int z, int layer) {
        if (this.knownBlockReads == null) {
            return;
        }
        this.knownBlockReads.reads++;
        if (!this.knownBlockReads.known) {
            return;
        }
        var raw = getKnownRawBlockAt(x, y, z, layer);
        if (raw.isEmpty()) {
            this.knownBlockReads.known = false;
        } else if (this.knownBlockReads.checkedRawIds.add(raw.getAsInt()) && !getPlayer().mappingInfo.isKnownRawId(raw.getAsInt())) {
            // A block ID we could not map is unknown, not air, even if the chunk data arrived.
            this.knownBlockReads.known = false;
        }
    }

    @Override
    public int getRawBlockAt(int x, int y, int z, int layer) {
        observeBlockRead(x, y, z, layer);
        return super.getRawBlockAt(x, y, z, layer);
    }

    private void markUnknownBlockRead() {
        if (this.knownBlockReads != null) {
            this.knownBlockReads.known = false;
        }
    }

    public boolean noCollision(Box aabb) {
        return this.collectColliders(this.getEntityCollisions(aabb), aabb).isEmpty();
    }

    public FluidState getFluidState(final Vector3i vec3) {
        return this.getFluidState(vec3.getX(), vec3.getY(), vec3.getZ());
    }

    public FluidState getFluidState(final Mutable mutable) {
        return this.getFluidState(mutable.getX(), mutable.getY(), mutable.getZ());
    }

    public FluidState getFluidState(int x, int y, int z) {
        BoarBlockState level1Block = getBlockState(x, y, z, 1);
        FluidState level1Fluid = level1Block.getFluidState(1);
        if (level1Fluid.fluid() == Fluid.WATER) {
            return level1Fluid;
        }

        return getBlockState(x, y, z, 0).getFluidState(0);
    }

    public List<CollisionRecord> collectMovementColliders(final Box volume) {
        final List<CollisionRecord> records = new ArrayList<>();
        for (Box shape : this.getEntityCollisions(volume)) {
            records.add(new CollisionRecord(shape, null, null));
        }
        final int minX = GenericMath.floor(volume.minX - 1.0F);
        final int maxX = GenericMath.floor(volume.maxX + 1.0F);
        final int minZ = GenericMath.floor(volume.minZ - 1.0F);
        final int maxZ = GenericMath.floor(volume.maxZ + 1.0F);
        final int minY = Math.max(this.getMinY(), GenericMath.floor(volume.minY - 1.0F));
        final int maxY = Math.min(this.getHeightY() - 1, GenericMath.floor(volume.maxY + 1.0F));
        if (GenericMath.floor(volume.minY - 1F) < getMinY() || GenericMath.floor(volume.maxY + 1F) >= getHeightY()) {
            markUnknownBlockRead();
        }
        if (minY >= maxY) {
            markUnknownBlockRead();
            return records;
        }

        // BlockSourceVisitor::visitCollisionShapes<BlockSourceVisitor::MoveCollisionVisitor>
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!this.isChunkLoaded(x, z)) {
                    markUnknownBlockRead();
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    final BoarBlockState state = this.getBlockState(x, y, z, 0);
                    final Vector3i position = Vector3i.from(x, y, z);
                    for (Box shape : state.findCollision(this.getPlayer(), position, volume, false)) {
                        // see BlockType::addCollisionShapes and BlockCollisionBoxComponent::addComponentCollisionShapes
                        if (shape.minX < shape.maxX && shape.minY < shape.maxY && shape.minZ < shape.maxZ && shape.intersects(volume)) {
                            records.add(new CollisionRecord(shape, state, position));
                        }
                    }
                }
            }
        }
        return records;
    }

    public List<Box> collectColliders(List<Box> list, Box aABB) {
        ImmutableList.Builder<Box> builder = ImmutableList.builderWithExpectedSize(list.size() + 1);
        if (!list.isEmpty()) {
            builder.addAll(list);
        }

        final CuboidBlockIterator iterator = CuboidBlockIterator.iterator(aABB);
        while (iterator.step()) {
            int x = iterator.getX(), y = iterator.getY(), z = iterator.getZ();
            if (this.isChunkLoaded(x, z)) {
                BoarBlockState state = this.getBlockState(x, y, z, 0);
                builder.addAll(state.findCollision(this.getPlayer(), Vector3i.from(x, y, z), aABB, true));
            } else {
                markUnknownBlockRead();
            }
        }
        return builder.build();
    }

    public List<Box> getEntityCollisions(final Box query) {
        return this.solidEntities(query.expand(1.0E-7F)).stream().map(SolidEntity::shape).toList();
    }

    // How far nearby actors may push the mover out. It also applies when the mover hits blocks.
    // It searches a wider area, like the native client does, but uses the same actors and saved shapes as the solid collision query.
    public Vec3 nearbyDepenetrationMagnitude(final Box moverBox) {
        Vec3 magnitude = null;
        for (final SolidEntity candidate : this.solidEntities(moverBox.expand(2.0F))) {
            final boolean happyGhast = candidate.entity().getDefinition().identifier().equals("minecraft:happy_ghast");
            final Vec3 value = new Vec3(.1F, happyGhast ? 2.0F : .1F, .1F);
            magnitude = magnitude == null ? value : new Vec3(Math.max(magnitude.x, value.x), Math.max(magnitude.y, value.y), Math.max(magnitude.z, value.z));
        }
        return magnitude;
    }

    private record SolidEntity(EntityCache entity, Box shape) {}

    private List<SolidEntity> solidEntities(final Box query) {
        final List<SolidEntity> entities = new ArrayList<>();
        // The vehicle the player rides, and anything riding it, never block the player.
        final VehicleData vehicle = this.getPlayer().vehicleData;
        final EntityCache ridden = vehicle == null ? null : this.getEntity(vehicle.vehicleRuntimeId);
        try {
            for (final EntityCache cache : this.getEntities().values()) {
                if (cache == null || ridden != null && this.isInRide(ridden, cache) || !this.collides(ridden, cache)) {
                    continue;
                }
                final Box shape = this.entityCollisionBox(cache, false);
                if (query.intersects(shape)) {
                    entities.add(new SolidEntity(cache, shape));
                }
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(getPlayer().getSession().name() + ": failed to collect entity collisions", e);
        }
        return entities;
    }

    // A temporary vertical shift of one entity's collision shape, for a rerun of the player's tick (BoatTopRetry). Always cleared after the rerun.
    private EntityCache collisionShiftEntity;
    private float collisionShiftY;

    public void setCollisionShift(final EntityCache entity, final float y) {
        this.collisionShiftEntity = entity;
        this.collisionShiftY = y;
    }

    public void clearCollisionShift() {
        this.collisionShiftEntity = null;
        this.collisionShiftY = 0;
    }

    /** Boat shape for a player on foot. Uses the same boats and saved shape as the solid-entity query, but has no fallback (returns null instead). */
    public Box trustedBoatCollisionBox(final EntityCache cache) {
        if (this.getPlayer().vehicleData != null || cache == null || this.getEntity(cache.getRuntimeId()) != cache
                || cache.getVehicleType() != VehicleType.BOAT || !this.collides(null, cache)) {
            return null;
        }
        return this.entityCollisionBox(cache, true);
    }

    private Box entityCollisionBox(final EntityCache cache, final boolean historyOnly) {
        final Box box = this.unshiftedEntityCollisionBox(cache, historyOnly);
        return box != null && cache == this.collisionShiftEntity ? box.offset(0, this.collisionShiftY, 0) : box;
    }

    private Box unshiftedEntityCollisionBox(final EntityCache cache, final boolean historyOnly) {
        final FollowerHistory history = cache.getFollowerHistory();
        if (cache.getVehicleType() == VehicleType.BOAT && history != null && !history.isLocallyPredicted()) {
            final String fallbackReason = history.tickedAt(this.getPlayer().tick) ? history.untrustedClockReason() : "unticked";
            if (fallbackReason == null) {
                // Solid-mob collision uses the shape saved before this tick's interpolation (smooth movement) step.
                return cache.getDimensions().getBoxAt(history.renderX(), history.renderY() - cache.getYOffset(), history.renderZ());
            }
            if (!historyOnly && cache.getFollowerFallbackLogTick() != this.getPlayer().tick) {
                cache.setFollowerFallbackLogTick(this.getPlayer().tick);
                this.getPlayer().getMovementTrace().log("follower collision fallback: entity=" + cache.getRuntimeId() + " reason=" + fallbackReason);
            }
        }
        return historyOnly ? null : cache.getCurrent().getBoundingBox();
    }

    // Does this entity block the mover? The mover is the vehicle the player rides, or the player on foot.
    // - A mob (the player, a horse) is blocked by any collidable entity.
    // - A stackable mover (a boat) is blocked only by other stackable entities. A boat is not a mob.
    private boolean collides(final EntityCache mover, final EntityCache entity) {
        final boolean moverMob = mover == null || !this.isStackableByDefault(mover);
        final boolean moverStackable = mover != null && this.isStackable(mover);
        return moverMob && this.isCollidable(entity) || moverStackable && this.isStackable(entity);
    }

    private boolean isCollidable(final EntityCache entity) {
        final Boolean flag = this.flag(entity, EntityFlag.COLLIDABLE);
        return flag != null && flag;
    }

    private boolean isStackable(final EntityCache entity) {
        final Boolean flag = this.flag(entity, EntityFlag.STACKABLE);
        return flag != null && flag;
    }

    // Boats and minecarts are the entity definitions with is_stackable. They are not mobs.
    private boolean isStackableByDefault(final EntityCache entity) {
        return switch (entity.getDefinition().identifier().toLowerCase(Locale.ROOT)) {
            case "minecraft:minecart", "minecraft:chest_minecart", "minecraft:tnt_minecart", "minecraft:hopper_minecart" -> true;
            default -> entity.getVehicleType() == VehicleType.BOAT;
        };
    }

    private Boolean flag(final EntityCache entity, final EntityFlag flag) {
        final EnumMap<EntityFlag, Boolean> flags = entity.getMetadata().getFlags();
        return flags == null ? null : flags.get(flag);
    }

    private boolean isInRide(final EntityCache ridden, final EntityCache other) {
        if (other == ridden) {
            return true;
        }

        for (final EntityCache.Passenger passenger : ridden.getPassengers()) {
            final Long runtimeId = this.getUniqueIdToRuntimeId().get(passenger.riderUniqueId());
            if (runtimeId != null && runtimeId == other.getRuntimeId()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasChunksAt(int i, int j, int k, int l) {
        for (int q = i; q <= k; ++q) {
            for (int r = j; r <= l; ++r) {
                if (this.isChunkLoaded(q, r)) continue;
                return false;
            }
        }
        return true;
    }
}
