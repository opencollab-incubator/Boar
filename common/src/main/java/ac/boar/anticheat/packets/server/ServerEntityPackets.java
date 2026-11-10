package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.AddEntityAck;
import ac.boar.anticheat.ack.types.EntityDeathAck;
import ac.boar.anticheat.ack.types.EntityInterpolateAck;
import ac.boar.anticheat.ack.types.EntityRemoveAck;
import ac.boar.anticheat.ack.types.FollowerMotionAck;
import ac.boar.anticheat.ack.types.FollowerMoveAck;
import ac.boar.anticheat.ack.types.PassengerLinkAck;
import ac.boar.anticheat.ack.types.VehicleMotionAck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.EntityMovementFlags;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.Set;

public class ServerEntityPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        player.staticSeatTracker.movement(player, event.getPacket());
        switch (event.getPacket()) {
            case EntityEventPacket packet when packet.getType() == EntityEventType.DEATH -> {
                final EntityCache entity = player.compensatedWorld.getTrackedEntity(packet.getRuntimeEntityId());
                if (entity != null) {
                    player.queueAcknowledgment(new EntityDeathAck(entity));
                }
            }
            case RemoveEntityPacket packet -> {
                player.staticSeatTracker.remove(player, packet.getUniqueEntityId());
                final EntityCache entity = player.compensatedWorld.getTrackedEntityByUniqueId(packet.getUniqueEntityId());
                if (entity != null) {
                    entity.setRemovalSent(true);
                }
                // Removing the ridden actor ends the ride, the same as a REMOVE link in VehiclePackets.
                // A dismount teleport, if any, was sent during this ride, before the removal.
                final VehicleData vehicle = player.vehicleData;
                final TeleportData lastTeleport = vehicle != null && vehicle.vehicleUniqueId == packet.getUniqueEntityId()
                        ? player.getTeleportUtil().getLastTeleportSince(vehicle.mountTick)
                        : null;
                player.sendLatencyStack(new EntityRemoveAck(packet.getUniqueEntityId(), entity, lastTeleport));
            }
            case AddEntityPacket packet -> {
                player.staticSeatTracker.spawn(player, packet);
                warnIfNoDefinition(player, packet.getRuntimeEntityId(), packet.getIdentifier());
                final EntityCache entity = this.handleEntityAdd(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(),
                        packet.getMetadata(), packet.getRotation().getX(), packet.getRotation().getY(), packet.getHeadRotation());
                // Only the actor this packet made. An older actor under the same runtime ID must not get this packet's data.
                if (entity != null) {
                    entity.setAttributes(packet.getAttributes());
                    if (packet.getMotion() != null) {
                        player.queueAcknowledgment(new VehicleMotionAck(entity, new Vec3(packet.getMotion())));
                    }
                    // Riders can come with the spawn of the vehicle instead of separate links.
                    for (final EntityLinkData link : packet.getEntityLinks()) {
                        if (link.getFrom() == packet.getUniqueEntityId() && link.getType() != EntityLinkData.Type.REMOVE) {
                            player.queueAcknowledgment(new PassengerLinkAck(entity.getRuntimeId(), link.getTo(), player.isPlayerEntity(link.getTo()), false,
                                    link.getVehicleAngularVelocity()));
                        }
                    }

                    // AddActor puts one move and its motion into the movement history.
                    if (keepsHistory(entity)) {
                        this.setWire(entity, packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ(), packet.getRotation().getX(),
                                packet.getRotation().getY(), packet.getHeadRotation());
                        this.queueHistoryMove(player, entity, false, true, false);
                        player.queueAcknowledgment(new FollowerMotionAck(System.nanoTime(), false, entity));
                    }
                }
            }
            case AddPlayerPacket packet -> {
                player.staticSeatTracker.retire(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId());
                warnIfNoDefinition(player, packet.getRuntimeEntityId(), "minecraft:player");
                this.handleEntityAdd(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(), packet.getMetadata(),
                        packet.getRotation().getX(), packet.getRotation().getY(), packet.getRotation().getZ());
            }
            case MoveEntityDeltaPacket packet -> {
                final EntityCache entity = player.compensatedWorld.getTrackedEntity(packet.getRuntimeEntityId());
                if (entity == null) {
                    return;
                }

                final Set<MoveEntityDeltaPacket.Flag> flags = packet.getFlags();
                Float posX = null, posY = null, posZ = null;
                Float pitch = null, yaw = null, headYaw = null;
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_X)) {
                    posX = packet.getX();
                }
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Y)) {
                    posY = packet.getY();
                }
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Z)) {
                    posZ = packet.getZ();
                }
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_PITCH)) {
                    pitch = EntityMovementFlags.wireByteAngle(packet.getPitch());
                }
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_YAW)) {
                    yaw = EntityMovementFlags.wireByteAngle(packet.getYaw());
                }
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_HEAD_YAW)) {
                    headYaw = EntityMovementFlags.wireByteAngle(packet.getHeadYaw());
                }

                final int protocolVersion = player.getSession().protocolVersion();
                final boolean teleport = EntityMovementFlags.isDeltaTeleport(packet, protocolVersion);
                final boolean forceMoveLocal = EntityMovementFlags.isDeltaForceMoveLocal(packet, protocolVersion);
                final boolean onGround = EntityMovementFlags.isDeltaOnGround(packet, protocolVersion);
                final boolean ordinaryMove = !teleport && !forceMoveLocal;
                this.queuePositionUpdate(event, entity, posX, posY, posZ, pitch, yaw, headYaw, true, true, teleport, forceMoveLocal,
                        EntityMovementFlags.isDeltaForceCompletion(packet, protocolVersion), onGround);

                if (keepsHistory(entity)) {
                    // Remember the last values the server sent. A delta move only changes the fields it has.
                    this.setWire(entity, posX != null ? posX : entity.getWireX(), posY != null ? posY : entity.getWireY(),
                            posZ != null ? posZ : entity.getWireZ(), pitch != null ? pitch : entity.getWirePitch(), yaw != null ? yaw : entity.getWireYaw(),
                            headYaw != null ? headYaw : entity.getWireHeadYaw());
                    this.queueHistoryMove(player, entity, onGround, false, ordinaryMove);
                }
            }
            case MoveEntityAbsolutePacket packet -> {
                player.compensatedWorld
                        .fetchTrackedEntity(packet.getRuntimeEntityId())
                        .ifPresent(entity -> {
                            final Vector3f rotation = Vector3f.from(EntityMovementFlags.wireByteAngle(packet.getRotation().getX()),
                                    EntityMovementFlags.wireByteAngle(packet.getRotation().getY()),
                                    EntityMovementFlags.wireByteAngle(packet.getRotation().getZ()));
                            final boolean ordinaryMove = !packet.isTeleported() && !packet.isForceMove();
                            this.queuePositionUpdate(event, entity, packet.getPosition(), rotation, true, true, packet.isTeleported(),
                                    packet.isForceMove(), packet.isForceCompletion(), packet.isOnGround());
                            if (keepsHistory(entity)) {
                                this.setWire(entity, packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ(), rotation.getX(),
                                        rotation.getY(), rotation.getZ());
                                this.queueHistoryMove(player, entity, packet.isOnGround(), false, ordinaryMove);
                            }
                        });
            }
            case SetEntityMotionPacket packet when packet.getRuntimeEntityId() != player.runtimeEntityId -> {
                final EntityCache entity = player.compensatedWorld.getTrackedEntity(packet.getRuntimeEntityId());
                if (entity != null) {
                    player.queueAcknowledgment(new VehicleMotionAck(entity, new Vec3(packet.getMotion())));
                    if (keepsHistory(entity)) {
                        player.queueAcknowledgment(new FollowerMotionAck(System.nanoTime(), false, entity));
                    }
                }
            }
            case MotionPredictionHintsPacket packet -> {
                final EntityCache entity = player.compensatedWorld.getTrackedEntity(packet.getRuntimeEntityId());
                if (entity != null && keepsHistory(entity)) {
                    player.queueAcknowledgment(new FollowerMotionAck(System.nanoTime(), packet.isOnGround(), entity));
                }
            }
            case MovePlayerPacket packet when packet.getRuntimeEntityId() != player.runtimeEntityId -> {
                player.compensatedWorld
                        .fetchTrackedEntity(packet.getRuntimeEntityId())
                        .ifPresent(entity -> this.queuePositionUpdate(event, entity, packet.getPosition(), packet.getRotation(),
                                packet.getMode() == MovePlayerPacket.Mode.NORMAL, // tryLerp
                                false, // fromMoveActorPacket
                                false, // teleport
                                false, // forceMoveLocal
                                false, // forceCompletion
                                false)); // onGround
            }
            default -> {}
        }
    }

    // The client keeps a movement history for every entity. We only need it for the ones that can be ridden.
    private static boolean keepsHistory(final EntityCache entity) {
        return entity.getVehicleType() != VehicleType.OTHER;
    }

    // Read only for history moves (queueHistoryMove, and the fields a delta move leaves out), so only actors that keep a history set them.
    private void setWire(final EntityCache entity, final float x, final float y, final float z, final float pitch, final float yaw, final float headYaw) {
        entity.setWireX(x);
        entity.setWireY(y);
        entity.setWireZ(z);
        entity.setWirePitch(pitch);
        entity.setWireYaw(yaw);
        entity.setWireHeadYaw(headYaw);
    }

    private void queueHistoryMove(final BoarPlayer player, final EntityCache entity, final boolean onGround, final boolean addedActor,
                                  final boolean ordinaryActorMove) {
        player.queueAcknowledgment(new FollowerMoveAck(entity, System.nanoTime(), entity.getWireX(), entity.getWireY(), entity.getWireZ(),
                entity.getWirePitch(), entity.getWireYaw(), entity.getWireHeadYaw(), onGround, addedActor, ordinaryActorMove, false));
    }

    // The platform adapter gives Boar each entity's definition (type, size). Without one, Boar does not track the entity at all:
    // no collision with it, no reach check for it, and possibly no vehicle simulation if it is a vehicle.
    private static void warnIfNoDefinition(final BoarPlayer player, final long runtimeId, final String identifier) {
        if (runtimeId == player.runtimeEntityId || player.getEntityAccessor().definitionByRuntimeId(runtimeId) != null) {
            return;
        }
        if (Boar.getConfig().staticSeatIdentifiers().contains(identifier)) {
            return;
        }
        Boar.getInstance().getPlatform().logger().warn("No entity definition from the platform adapter for " + identifier + " (runtime id " + runtimeId
                + "). Boar will not track entities of this type: no collision, reach or vehicle simulation for them.");
    }

    // Callers retire static seats with these ids first: StaticSeatTracker.spawn for AddEntity, StaticSeatTracker.retire for AddPlayer.
    // A static seat is only kept when there is no definition, and then addToCache returns null.
    // Returns the new actor, or null when Boar does not track it.
    private EntityCache handleEntityAdd(final BoarPlayer player, final long runtimeId, final long uniqueId, final Vector3f rawPosition,
                                        final EntityDataMap metadata, final float pitch, final float yaw, final float headYaw) {
        final EntityCache previous = player.compensatedWorld.getTrackedEntity(runtimeId);
        final EntityCache entity = player.compensatedWorld.addToCache(player, runtimeId, uniqueId);
        if (entity == null) {
            return null;
        }

        final Vec3 position = new Vec3(rawPosition);
        if (entity.getVehicleType() == VehicleType.BOAT) {
            // Boat packets give the actor origin. Our cached position is the bottom of the box. So subtract the Y offset.
            position.y -= entity.getYOffset();
        }
        entity.setServerPosition(position);
        entity.init();
        entity.interpolate(position, false);
        entity.applyRotation(pitch, yaw, headYaw);
        // Set the follower rotation for the new actor. It stays hidden from checks until the client acks the spawn.
        entity.setActorPitch(pitch);
        entity.setActorYaw(yaw);
        entity.setActorHeadYaw(headYaw);
        entity.setMetadata(metadata);
        if (entity.getVehicleType() == VehicleType.HORSE) {
            entity.getHorseJumpEffect().trackSpawn();
            entity.getHorseGround().spawnOnGround();
            if (previous == null || previous.getUniqueId() != uniqueId || previous.isRemovalSent()) {
                entity.getHorseImmobility().prepareFreshSpawn();
            } else {
                // AddActor came again for an entity that is still alive. Keep its old immobility state, so its pending requests and cleanups are not lost.
                entity.setHorseImmobility(previous.getHorseImmobility());
            }
        }

        // The entity stays hidden from checks and prediction until the client acknowledges the
        // spawn packet. This keeps entity adds lag compensated, the same as entity removals.
        player.sendLatencyStack(new AddEntityAck(entity));
        return entity;
    }

    private void queuePositionUpdate(final CloudburstPacketEvent event, final EntityCache entity, final Vector3f raw, final Vector3f rotation,
                                     final boolean tryLerp, final boolean fromMoveActorPacket, final boolean teleport, final boolean forceMoveLocal,
                                     final boolean forceCompletion, final boolean onGround) {
        queuePositionUpdate(event, entity, raw.getX(), raw.getY(), raw.getZ(), rotation.getX(), rotation.getY(), rotation.getZ(), tryLerp, fromMoveActorPacket,
                teleport, forceMoveLocal, forceCompletion, onGround);
    }

    private void queuePositionUpdate(
            final CloudburstPacketEvent event,
            final EntityCache entity,
            final Float posX,
            Float posY,
            final Float posZ,
            final Float pitch,
            final Float yaw,
            final Float headYaw,
            final boolean tryLerp,
            final boolean fromMoveActorPacket,
            final boolean teleport,
            final boolean forceMoveLocal,
            final boolean forceCompletion,
            final boolean onGround
    ) {
        final BoarPlayer player = event.getPlayer();
        if (posY != null) {
            posY -= entity.getYOffset();
        }

        Vec3 newPos = entity.getServerPosition().clone();
        if (posX != null) newPos.x = posX;
        if (posY != null) newPos.y = posY;
        if (posZ != null) newPos.z = posZ;

        final float distance = entity.getServerPosition().squaredDistanceTo(newPos);
        /* if (distance < 1.0E-15) {
            return;
        } */

        final boolean lerp = tryLerp && distance < 4096;
        entity.setServerPosition(newPos);
        player.queueAcknowledgment(new EntityInterpolateAck(entity, posX, posY, posZ, pitch, yaw, headYaw, lerp, false, fromMoveActorPacket, teleport,
                forceMoveLocal, forceCompletion, onGround));
    }

}
