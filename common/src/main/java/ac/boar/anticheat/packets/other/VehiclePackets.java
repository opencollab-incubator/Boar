package ac.boar.anticheat.packets.other;

import ac.boar.anticheat.ack.types.VehicleClearAck;
import ac.boar.anticheat.ack.types.VehicleSetAck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;

public class VehiclePackets implements PacketListener {
    @Override
    public void onPacketReceived(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof InteractPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            if (packet.getAction() == InteractPacket.Action.LEAVE_VEHICLE) {
                player.vehicleData = null;
            }
        }
    }

    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        if (event.getPacket() instanceof SetEntityLinkPacket packet) {
            final EntityLinkData link = packet.getEntityLink();
            if (link == null) {
                return;
            }

            long entityId = packet.getEntityLink().getFrom();
            long riderId = packet.getEntityLink().getTo();

            // We handle this separately.
            if (riderId != player.runtimeEntityId) {
                final EntityCache riderCache = player.compensatedWorld.getEntity(riderId);
                if (riderCache != null) {
                    riderCache.setInVehicle(link.getType() != EntityLinkData.Type.REMOVE);
                }

                return;
            }

            final EntityCache cache = player.compensatedWorld.getEntity(entityId);
            if (cache == null) {
                // Likely won't happen, but why not!
                return;
            }

            // Yep.
            player.getTeleportUtil().getQueuedTeleports().clear();

            if (link.getType() == EntityLinkData.Type.REMOVE) {
                player.queueAcknowledgment(new VehicleClearAck());
                return;
            }

            player.queueAcknowledgment(new VehicleSetAck(entityId));
        }
    }
}
