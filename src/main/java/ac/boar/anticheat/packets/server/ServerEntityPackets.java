package ac.boar.anticheat.packets.server;

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
            player.sendLatencyStack();
            player.sendLatencyStack(() -> {
                if (player.vehicleData != null && player.vehicleData.vehicleRuntimeId == packet.getUniqueEntityId()) {
                    player.vehicleData = null;
                }

                player.compensatedWorld.removeEntity(packet.getUniqueEntityId());
            });
        }

        if (event.getPacket() instanceof AddEntityPacket packet) {
            final EntityCache entity = player.compensatedWorld.addToCache(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId());
            if (entity == null) {
                return;
            }

            final Vec3 position = new Vec3(packet.getPosition());
            entity.setServerPosition(position);
            entity.init();
            entity.interpolate(position, false);

            entity.setMetadata(packet.getMetadata());
        }

        if (event.getPacket() instanceof AddPlayerPacket packet) {
            final EntityCache entity = player.compensatedWorld.addToCache(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId());
            if (entity == null) {
                return;
            }

            final Vec3 position = new Vec3(packet.getPosition());
            entity.setServerPosition(position);
            entity.init();
            entity.interpolate(position, false);

            entity.setMetadata(packet.getMetadata());
        }

        if (event.getPacket() instanceof MoveEntityDeltaPacket packet) {
            final EntityCache entity = player.compensatedWorld.getEntity(packet.getRuntimeEntityId());
            if (entity == null) {
                return;
            }

            final Set<MoveEntityDeltaPacket.Flag> flags = packet.getFlags();

            final boolean useless = !flags.contains(MoveEntityDeltaPacket.Flag.HAS_X) && !flags.contains(MoveEntityDeltaPacket.Flag.HAS_Y) && !flags.contains(MoveEntityDeltaPacket.Flag.HAS_Z);
            if (useless) {
                return;
            }

            float x = packet.getX(), y = packet.getY(), z = packet.getZ();
            if (!flags.contains(MoveEntityDeltaPacket.Flag.HAS_X)) {
                x = entity.getServerPosition().getX();
            }
            if (!flags.contains(MoveEntityDeltaPacket.Flag.HAS_Y)) {
                y = entity.getServerPosition().getY();
            }
            if (!flags.contains(MoveEntityDeltaPacket.Flag.HAS_Z)) {
                z = entity.getServerPosition().getZ();
            }

            this.queuePositionUpdate(event, entity, Vector3f.from(x, y, z), true);
        }

        if (event.getPacket() instanceof MoveEntityAbsolutePacket packet) {
            final EntityCache entity = player.compensatedWorld.getEntity(packet.getRuntimeEntityId());
            if (entity == null) {
                return;
            }

            this.queuePositionUpdate(event, entity, packet.getPosition(), false);
        }

        if (event.getPacket() instanceof MovePlayerPacket packet) {
            if (packet.getRuntimeEntityId() == player.runtimeEntityId) {
                return;
            }

            final EntityCache entity = player.compensatedWorld.getEntity(packet.getRuntimeEntityId());
            if (entity == null) {
                return;
            }

            this.queuePositionUpdate(event, entity, packet.getPosition(), packet.getMode() == MovePlayerPacket.Mode.NORMAL);
        }
    }

    private void queuePositionUpdate(final CloudburstPacketEvent event, final EntityCache entity, final Vector3f raw, final boolean lerp) {
        final BoarPlayer player = event.getPlayer();
        final Vec3 position = new Vec3(raw.sub(0, entity.getYOffset(), 0));

        final float distance = entity.getServerPosition().squaredDistanceTo(position);
        if (distance < 1.0E-15) {
            return;
        }

        entity.setServerPosition(position);

        // We need 2 transaction to check, if player receive the first transaction they could already have received the packet
        // Or they could lag right before they receive the actual update position packet so we can't be sure
        // But if player respond to the transaction AFTER the position packet they 100% already receive the packet.
        player.sendLatencyStack();

        player.getLatencyUtil().queue(() -> entity.interpolate(position, lerp && distance < 4096));
        event.getPostTasks().add(() -> player.sendLatencyStack(() -> entity.setPast(null)));
    }
}