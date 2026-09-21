package ac.boar.anticheat.prediction.ticker.base;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.collision.Collider;
import ac.boar.anticheat.collision.MovementCollision;
import ac.boar.anticheat.prediction.engine.data.BounceGravityCorrection;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Direction;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Properties;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.codec.v975.Bedrock_v975;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

@RequiredArgsConstructor
public class EntityTicker {
    private static final float COLLISION_EPSILON = 1.0E-5f;
    private static final float FLT_EPSILON = 1.1920929E-7f;
    private static final float MIN_BOUNCE_SPEED = 0.08f + FLT_EPSILON;

    protected final BoarPlayer player;

    public void tick() {
        this.baseTick();
    }

    public void baseTick() {
        player.inBlockState = null;

        this.updateWaterState();
        this.updateHeadInWater();
        this.updateSwimming();

        player.soulSandBelow = player.compensatedWorld.getBlockState(player.position.down(1.0E-3F).toVector3i(), 0).is(Blocks.SOUL_SAND);
    }

    private void updateHeadInWater() {
        player.headInWater = false;
        // ref from LiquidPhysicsSystem::_liquidBlockFetch and UnderWaterSensingSystem::doUnderWaterSensing
        if (!player.touchingWater) {
            player.getMovementTrace().log("head water: body contact=false, headInWater=false");
            return;
        }

        final float nativeY = player.nativeOriginY;
        final float headY = nativeY - player.headSampleOffsetY;
        final int x = GenericMath.floor(player.position.x);
        final int y = GenericMath.floor(headY);
        final int z = GenericMath.floor(player.position.z);

        // BlockSource::getLiquidBlock - use layer 0 only when the extra layer contains air.
        BoarBlockState block = player.compensatedWorld.getBlockState(x, y, z, 1);
        final boolean useLayerZero = block.isAir();
        if (useLayerZero) {
            block = player.compensatedWorld.getBlockState(x, y, z, 0);
        }
        if (!block.is(Blocks.WATER)) {
            player.getMovementTrace().log("head water: sample=[" + player.position.x + "," + headY + "," + player.position.z
                    + "] cell=[" + x + "," + y + "," + z + "] layer=" + (useLayerZero ? 0 : 1)
                    + " block=" + block.block() + " sampleOffset=" + player.headSampleOffsetY + " headInWater=false");
            return;
        }

        // UnderWaterSensingSystem::doUnderWaterSensing
        final int rawDepth = block.get(Properties.LEVEL);
        final int n = rawDepth < 8 ? rawDepth + 1 : 1;
        final float surfaceY = (float) (y + 1) - ((float) n / 9.0F - 0.11111111F);
        player.headInWater = headY < surfaceY;
        player.getMovementTrace().log("head water: sample=[" + player.position.x + "," + headY + "," + player.position.z
                + "] cell=[" + x + "," + y + "," + z + "] layer=" + (useLayerZero ? 0 : 1)
                + " rawDepth=" + rawDepth + " surfaceY=" + surfaceY + " sampleOffset=" + player.headSampleOffsetY
                + " currentOffset=" + player.vanillaOffsetY + " previousOffset=" + player.previousVanillaOffsetY
                + " headInWater=" + player.headInWater);
    }

    private void updateSwimming() {
        if (player.getFlagTracker().has(EntityFlag.SWIMMING)) {
            player.getFlagTracker().set(EntityFlag.SWIMMING, player.touchingWater && player.vehicleData == null);
        }
    }

    private record FluidFlowEntry(FluidState state, Vector3i position, int boundaryFaces) {
    }

    private record FluidContact(boolean found, float height) {
    }

    private void updateWaterState() {
        player.fluidHeight.clear();
        player.selectedFluid = Fluid.EMPTY;
        player.touchingWater = false;
        if (player.isRegionUnloaded()) {
            return;
        }

        // LiquidPhysicsSystem::_liquidBlockFetch
        final List<FluidFlowEntry> entries = new ArrayList<>();
        final FluidContact lava = this.gatherFluid(Fluid.LAVA, entries);
        final FluidContact water = this.gatherFluid(Fluid.WATER, lava.found() ? null : entries);
        player.touchingWater = water.found();
        player.fluidHeight.put(Fluid.LAVA, lava.height());
        player.fluidHeight.put(Fluid.WATER, water.height());
        player.selectedFluid = lava.found() ? Fluid.LAVA : water.found() ? Fluid.WATER : Fluid.EMPTY;
        this.applyFluidPush(entries);
    }

    public void applyWaterPushAfterTeleport() {
        if (player.vehicleData != null || player.isRegionUnloaded() || this.gatherFluid(Fluid.LAVA, null).found()) {
            return;
        }
        this.updateWaterState();
    }

    // BlockSource::getLiquidBlock
    private FluidState getSelectedFluidState(final int x, final int y, final int z) {
        final BoarBlockState extra = player.compensatedWorld.getBlockState(x, y, z, 1);
        final BoarBlockState selected = extra.isAir() ? player.compensatedWorld.getBlockState(x, y, z, 0) : extra;
        return selected.getFluidState(0);
    }

    private FluidContact gatherFluid(final Fluid tag, final List<FluidFlowEntry> entries) {
        // LiquidPhysicsSystem::_liquidBlockFetch
        final Box box = tag == Fluid.LAVA ? contractForFluidScan(player.boundingBox, 0.1F, 0.4F, 0.1F) : contractForFluidScan(player.boundingBox, 0.001F, 0.401F, 0.001F);
        final int minX = GenericMath.floor(box.minX), endX = GenericMath.floor(box.maxX + 1.0F);
        final int minY = GenericMath.floor(box.minY), endY = GenericMath.floor(box.maxY + 1.0F);
        final int minZ = GenericMath.floor(box.minZ), endZ = GenericMath.floor(box.maxZ + 1.0F);
        boolean found = false;
        float height = Float.MIN_VALUE;
        final StringBuilder hits = Boar.getConfig().debugMode() ? new StringBuilder() : null;

        for (int y = minY; y < endY; y++) {
            for (int z = minZ; z < endZ; z++) {
                for (int x = minX; x < endX; x++) {
                    final FluidState state = this.getSelectedFluidState(x, y, z);
                    if (state.fluid() != tag) {
                        continue;
                    }
                    if (entries != null) {
                        int faces = 0;
                        if (z == minZ) faces |= 1 << Direction.NORTH.ordinal();
                        if (z == endZ - 1) faces |= 1 << Direction.SOUTH.ordinal();
                        if (x == minX) faces |= 1 << Direction.WEST.ordinal();
                        if (x == endX - 1) faces |= 1 << Direction.EAST.ordinal();
                        entries.add(new FluidFlowEntry(state, Vector3i.from(x, y, z), faces)); // (??????)::LiquidPhysicsSystemAnon::_gatherInAABB
                    }

                    final boolean contact = (float) x <= box.maxX && (float) y <= box.maxY && (float) z <= box.maxZ;
                    if (contact) {
                        found = true;
                        height = Math.max(height, y + 1 - state.height());
                    }
                    if (hits != null) {
                        final BoarBlockState extra = player.compensatedWorld.getBlockState(x, y, z, 1);
                        hits.append(" [").append(x).append(',').append(y).append(',').append(z)
                                .append(" layer=").append(extra.isAir() ? 0 : 1)
                                .append(" usedLevel=").append(state.level()).append(" height=").append(state.height())
                                .append(" accepted=").append(contact).append(']');
                    }
                }
            }
        }
        if (hits != null) {
            player.getMovementTrace().log("fluid scan: " + tag + " box=[" + box.minX + "," + box.minY + "," + box.minZ
                    + " -> " + box.maxX + "," + box.maxY + "," + box.maxZ + "] found=" + found
                    + " collectFlow=" + (entries != null) + " hits=" + (hits.length() == 0 ? "none" : hits));
        }
        return new FluidContact(found, found ? height : 0);
    }

    private void applyFluidPush(final List<FluidFlowEntry> entries) {
        final Fluid selected = player.selectedFluid;
        if (selected == Fluid.EMPTY) {
            return;
        }
        boolean insideFlowing = false;
        for (FluidFlowEntry entry : entries) {
            insideFlowing |= entry.state().level() > 0;
        }
        boolean sideFlowing = false;
        if (!insideFlowing) {
            for (FluidFlowEntry entry : entries) {
                for (Direction direction : Direction.HORIZONTAL) {
                    if ((entry.boundaryFaces() & (1 << direction.ordinal())) == 0) {
                        continue;
                    }
                    final Vector3i side = entry.position().add(direction.getUnitVector());
                    final FluidState state = this.getSelectedFluidState(side.getX(), side.getY(), side.getZ());
                    // LiquidPhysicsSystem::_liquidBlockFetch
                    sideFlowing |= state.fluid() == selected && state.level() > 0;
                }
            }
        }

        Vec3 flowSum = new Vec3(0, 0, 0);
        Vec3 push = new Vec3(0, 0, 0);
        if (insideFlowing || sideFlowing) {
            // LiquidPhysicsSystem::_liquidBlockFetch
            for (FluidFlowEntry entry : entries) {
                flowSum = flowSum.add(entry.state().getFlow(player, entry.position()));
            }
            if (flowSum.length() > 0.0F) {
                final float speed = selected == Fluid.LAVA ? 0.0035F : 0.014F;
                push = FluidState.normalizeFlow(flowSum).multiply(speed);
                player.velocity = player.velocity.add(push);
            }
        }
        player.getMovementTrace().log("liquid push: selected=" + selected + " entries=" + entries.size()
                + " flowNeeded=" + (insideFlowing || sideFlowing) + " insideFlowing=" + insideFlowing
                + " sideFlowing=" + sideFlowing + " flowSum=" + flowSum + " push=" + push);
    }

    // LiquidPhysicsSystem::_liquidBlockFetch
    private static Box contractForFluidScan(final Box box, final float x, final float y, final float z) {
        float minX = box.minX + x, maxX = box.maxX - x;
        float minY = box.minY + y, maxY = box.maxY - y;
        float minZ = box.minZ + z, maxZ = box.maxZ - z;
        if (minX > maxX) {
            minX = maxX = (box.minX + box.maxX) * 0.5F;
        }
        if (minY > maxY) {
            minY = maxY = (box.minY + box.maxY) * 0.5F;
        }
        if (minZ > maxZ) {
            minZ = maxZ = (box.minZ + box.maxZ) * 0.5F;
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    protected void applyEffectsFromBlocks() {
        if (player.onGround) {
            final Vector3i lv = player.getOnPos(0.2F);
            player.compensatedWorld.getBlockState(lv, 0).onSteppedOn(player, lv);
        }

        Vector3i min = Vector3i.from(player.boundingBox.minX + 0.001D, player.boundingBox.minY + 0.001D, player.boundingBox.minZ + 0.001D);
        Vector3i max = Vector3i.from(player.boundingBox.maxX - 0.001D, player.boundingBox.maxY - 0.001D, player.boundingBox.maxZ - 0.001D);

        final Mutable mutable = new Mutable();
        for (int i = min.getX(); i <= max.getX(); ++i) {for (int j = min.getY(); j <= max.getY(); ++j) {for (int k = min.getZ(); k <= max.getZ(); ++k) {
            mutable.set(i, j, k);

            player.compensatedWorld.getBlockState(i, j, k, 0).entityInside(player, mutable);
        }}}
    }

    public final BounceGravityCorrection doSelfMove(Vec3 vec3) {
        if (player.abilities.contains(Ability.NO_CLIP)) {
            player.getMovementTrace().log("move: noclip, no collision applied");
            player.setPos(player.position.add(vec3));
            return null;
        }

        if (player.stuckSpeedMultiplier.lengthSquared() > 1.0E-7) {
            player.getMovementTrace().log("move: stuck multiplier " + player.stuckSpeedMultiplier
                    + " applied, velocity zeroed");
            vec3 = vec3.multiply(player.stuckSpeedMultiplier);
            player.stuckSpeedMultiplier = Vec3.ZERO;
            player.velocity = Vec3.ZERO.clone();
        }

        Vec3 oldVec3 = vec3.clone();
        boolean wasOnGround = player.onGround;
        Vec3 penetration = Vec3.ZERO.clone();
        final Collider.MoveResult moveResult = Collider.collideMove(
                player,
                Collider.maybeBackOffFromEdge(player, vec3),
                player.stuckInCollider,
                penetration
        );
        vec3 = moveResult.requestedMovement();
        final Vec3 vec32 = moveResult.clippedMovement();
        boolean hasPenetration = exceedsReconstructionNoise(penetration.x, player.position.x)
                || exceedsReconstructionNoise(penetration.y, player.position.y)
                || exceedsReconstructionNoise(penetration.z, player.position.z);
        player.stuckInCollider = player.penetratedLastFrame && hasPenetration;
        player.penetratedLastFrame = hasPenetration;
        player.setPos(player.position.add(vec32));

        player.getMovementTrace().log("move: input=" + oldVec3 + " afterEdgeBackoff=" + vec3
                + " afterCollide=" + vec32 + " penetration=" + penetration + " newPos=" + player.position);

        boolean collidedX = Math.abs(vec3.x - vec32.x) >= COLLISION_EPSILON;
        boolean collidedZ = Math.abs(vec3.z - vec32.z) >= COLLISION_EPSILON;
        player.horizontalCollision = collidedX || collidedZ;
        // FinalizeMoveSystemImpl::tickFinalizeMoveSystem
        player.verticalCollision = Math.abs(vec3.y - vec32.y) > FLT_EPSILON;
        player.onGround = (player.verticalCollision && vec3.y < 0.0F) || (wasOnGround && !player.verticalCollision && Math.abs(vec3.y) <= COLLISION_EPSILON);

        // The player is near bamboo, we don't know what the offsetting is so we let player decide this...
        if (player.nearBamboo && player.getInputData().contains(PlayerAuthInputData.HORIZONTAL_COLLISION)) {
            player.getMovementTrace().log("move: bamboo hack, trusting client horizontal collision");
            player.horizontalCollision = true;
            collidedX = player.unvalidatedTickEnd.x == 0;
            collidedZ = player.unvalidatedTickEnd.z == 0;
        }

        // Vanilla zeroes the velocity on each axis that the sneak edge guard reduced to zero
        // (SneakMovementSystem::tickSneakMovementSystem, 1.26.30, FLT_EPSILON test).
        if (oldVec3.x != vec3.x || oldVec3.z != vec3.z) {
            player.velocity = new Vec3(
                    Math.abs(vec3.x) <= FLT_EPSILON ? 0 : player.velocity.x,
                    player.velocity.y,
                    Math.abs(vec3.z) <= FLT_EPSILON ? 0 : player.velocity.z
            );
            player.getMovementTrace().log("move: edge backoff velocity, vel=" + player.velocity);
        }

        final MovementCollision.StandingBlock standing = MovementCollision.selectStandingBlock(moveResult.finalBox(), moveResult.records());
        final BounceGravityCorrection bounceCorrection;
        if (player.getSession().protocolVersion() >= Bedrock_v975.CODEC.getProtocolVersion()) {
            bounceCorrection = restituteMovementAfterCollisions(standing, collidedX, collidedZ, moveResult.requestedMovement(), moveResult.clippedMovement());
        } else {
            bounceCorrection = null;
            if (player.horizontalCollision) {
                player.velocity = new Vec3(collidedX ? 0 : player.velocity.x, player.velocity.y, collidedZ ? 0 : player.velocity.z);
            }
            if (player.verticalCollision) {
                if (standing == null) { // mainly can happen with teleports
                    player.velocity.y = 0.0F;
                } else {
                    standing.state().updateEntityMovementAfterFallOn(player, true);
                }
            }
        }

        player.beforeCollision = vec3.clone();
        player.afterCollision = vec32.clone();

        player.getMovementTrace().log("move done: hColl=" + player.horizontalCollision + " (x=" + collidedX
                + " z=" + collidedZ + ") vColl=" + player.verticalCollision + " onGround=" + player.onGround
                + " bounceCorrection=" + bounceCorrection + " vel=" + player.velocity);
        return bounceCorrection;
    }

    private static boolean exceedsReconstructionNoise(float penetration, float coordinate) {
        return penetration >= Math.max(1.0E-4F, 4.0F * Math.ulp(coordinate));
    }

    // ComputeBlockRestitutionSystem::tick
    private BounceGravityCorrection restituteMovementAfterCollisions(final MovementCollision.StandingBlock standing, final boolean xCollision, final boolean zCollision, final Vec3 requested, final Vec3 actual) {
        final Vec3 incoming = player.velocity;
        float restitutionY = 0.0F;
        if (player.verticalCollision && incoming.y < 0.0F && Math.abs(incoming.y) >= MIN_BOUNCE_SPEED && !player.getFlagTracker().has(EntityFlag.SNEAKING) && standing != null) {
            final float bounciness = standing.state().getBlockBounciness();
            if (Math.abs(bounciness) > FLT_EPSILON && bounciness != -1.0F) {
                restitutionY = Math.max(0.0F, -(incoming.y * bounciness));
            }
        }
        final BounceGravityCorrection correction = BounceGravityCorrection.fromMove(restitutionY, requested.y, actual.y);
        player.velocity = new Vec3(
                xCollision ? 0.0F : incoming.x,
                player.verticalCollision ? restitutionY : incoming.y,
                zCollision ? 0.0F : incoming.z
        );
        player.getMovementTrace().log("restitution: incoming=" + incoming + " requested=" + requested + " actual=" + actual
                + " block=" + (standing == null ? "none" : standing.state().block() + " at " + standing.position())
                + " restitutionY=" + restitutionY + " correction=" + correction);
        return correction;
    }
}
