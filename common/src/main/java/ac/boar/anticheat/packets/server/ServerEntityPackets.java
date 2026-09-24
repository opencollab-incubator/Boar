package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.ack.types.AddEntityAck;
import ac.boar.anticheat.ack.types.EntityInterpolateAck;
import ac.boar.anticheat.ack.types.EntityRemoveAck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.Set;

public class ServerEntityPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        switch (event.getPacket()) {
            case RemoveEntityPacket packet -> player.sendLatencyStack(new EntityRemoveAck(packet.getUniqueEntityId()));
            case AddEntityPacket packet -> this.handleEntityAdd(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(), packet.getMetadata(), packet.getRotation().getX(), packet.getRotation().getY(), packet.getHeadRotation());
            case AddPlayerPacket packet -> this.handleEntityAdd(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(), packet.getMetadata(), packet.getRotation().getX(), packet.getRotation().getY(), packet.getRotation().getZ());
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
                    pitch = packet.getPitch();
                }
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_YAW)) {
                    yaw = packet.getYaw();
                }
                if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_HEAD_YAW)) {
                    headYaw = packet.getHeadYaw();
                }

                this.queuePositionUpdate(event, entity, posX, posY, posZ, pitch, yaw, headYaw, true);
            }
            case MoveEntityAbsolutePacket packet -> {
                player.compensatedWorld
                        .fetchTrackedEntity(packet.getRuntimeEntityId())
                        .ifPresent(entity -> this.queuePositionUpdate(event, entity, packet.getPosition(), packet.getRotation(), true));
            }
            case MovePlayerPacket packet when packet.getRuntimeEntityId() != player.runtimeEntityId -> {
                player.compensatedWorld
                        .fetchTrackedEntity(packet.getRuntimeEntityId())
                        .ifPresent(entity -> this.queuePositionUpdate(event, entity, packet.getPosition(), packet.getRotation(), packet.getMode() == MovePlayerPacket.Mode.NORMAL));
            }
            default -> {}
        }
    }

    private void handleEntityAdd(final BoarPlayer player, final long runtimeId, final long uniqueId, final Vector3f rawPosition, final EntityDataMap metadata, final float pitch, final float yaw, final float headYaw) {
        final EntityCache entity = player.compensatedWorld.addToCache(player, runtimeId, uniqueId);
        if (entity == null) {
            return;
        }

        final Vec3 position = new Vec3(rawPosition);
        entity.setServerPosition(position);
        entity.init();
        entity.interpolate(position, false);
        entity.applyRotation(pitch, yaw, headYaw);
        entity.setMetadata(metadata);

        // The entity stays hidden from checks and prediction until the client acknowledges the
        // spawn packet. This keeps entity adds lag compensated, the same as entity removals.
        player.sendLatencyStack(new AddEntityAck(runtimeId));
    }

    private void queuePositionUpdate(final CloudburstPacketEvent event, final EntityCache entity, final Vector3f raw, final Vector3f rotation, final boolean tryLerp) {
        queuePositionUpdate(event, entity, raw.getX(), raw.getY(), raw.getZ(),
                rotation.getX(), rotation.getY(), rotation.getZ(), tryLerp);
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
            final boolean tryLerp
    ) {
        final BoarPlayer player = event.getPlayer();
        final long runtimeId = entity.getRuntimeId();
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

        entity.setServerPosition(newPos);
        player.queueAcknowledgment(new EntityInterpolateAck(runtimeId, posX, posY, posZ,
                pitch, yaw, headYaw, tryLerp && distance < 4096));
    }

}