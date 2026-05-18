package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.EntityInterpolateAck;
import ac.boar.anticheat.ack.types.EntityRemoveAck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.Set;

public class ServerEntityPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        if (event.getPacket() instanceof RemoveEntityPacket packet) {
            player.sendLatencyStack(new EntityRemoveAck(packet.getUniqueEntityId()));
        } else if (event.getPacket() instanceof AddEntityPacket packet) {
            final EntityCache entity = player.compensatedWorld.addToCache(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId());
            if (entity == null) {
                return;
            }

            final Vec3 position = new Vec3(packet.getPosition());
            entity.setServerPosition(position);
            entity.init();
            entity.interpolate(position, false);

            entity.setMetadata(packet.getMetadata());
        } else if (event.getPacket() instanceof AddPlayerPacket packet) {
            final EntityCache entity = player.compensatedWorld.addToCache(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId());
            if (entity == null) {
                return;
            }

            final Vec3 position = new Vec3(packet.getPosition());
            entity.setServerPosition(position);
            entity.init();
            entity.interpolate(position, false);

            entity.setMetadata(packet.getMetadata());
        } else if (event.getPacket() instanceof MoveEntityDeltaPacket packet) {
            final EntityCache entity = player.compensatedWorld.getEntity(packet.getRuntimeEntityId());
            if (entity == null) {
                return;
            }

            final Set<MoveEntityDeltaPacket.Flag> flags = packet.getFlags();
            Float posX = null, posY = null, posZ = null;
            if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_X)) {
                posX = packet.getX();
            }
            if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Y)) {
                posY = packet.getY();
            }
            if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Z)) {
                posZ = packet.getZ();
            }

            this.queuePositionUpdate(event, entity, posX, posY, posZ, true);
        } else if (event.getPacket() instanceof MoveEntityAbsolutePacket packet) {
            player.compensatedWorld
                    .fetchEntity(packet.getRuntimeEntityId())
                    .ifPresent(entity -> this.queuePositionUpdate(event, entity, packet.getPosition(), true));
        } else if (event.getPacket() instanceof MovePlayerPacket packet) {
            if (packet.getRuntimeEntityId() == player.runtimeEntityId) {
                return;
            }

            player.compensatedWorld
                    .fetchEntity(packet.getRuntimeEntityId())
                    .ifPresent(entity -> this.queuePositionUpdate(event, entity, packet.getPosition(), packet.getMode() == MovePlayerPacket.Mode.NORMAL));
        }
    }

    private void queuePositionUpdate(final CloudburstPacketEvent event, final EntityCache entity, final Vector3f raw, final boolean tryLerp) {
        queuePositionUpdate(event, entity, raw.getX(), raw.getY(), raw.getZ(), tryLerp);
    }

    private void queuePositionUpdate(
            final CloudburstPacketEvent event,
            final EntityCache entity,
            final Float posX,
            Float posY,
            final Float posZ,
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
        player.queueAcknowledgment(new EntityInterpolateAck(runtimeId, posX, posY, posZ, tryLerp && distance < 4096));
    }

}