package ac.boar.anticheat.packets.other;

import ac.boar.anticheat.ack.types.PassengerLinkAck;
import ac.boar.anticheat.ack.types.VehicleClearAck;
import ac.boar.anticheat.ack.types.VehicleSetAck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;

public class VehiclePackets implements PacketListener {
    @Override
    public void onPacketReceived(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof InteractPacket packet && packet.getAction() == InteractPacket.Action.LEAVE_VEHICLE) {
            if (player.staticSeat != null) {
                player.staticSeat.leaveRequested = true;
            }
            // The server can refuse the leave. The client may leave on its own first (AuthInputPackets.localExitStart).
            // Otherwise we stay in the vehicle until the link is removed.
            if (player.vehicleData != null) {
                // A second request while the first is pending does not start a new local exit. The client already left after the first one,
                // or it waits for the server (server-only dismount).
                if (!player.vehicleData.pendingLeave) {
                    player.vehicleData.localExitDecided = false;
                }
                player.vehicleData.pendingLeave = true;
                player.vehicleData.pendingLeaveNs = System.nanoTime();
                player.horseJumpInput.localDismount();
                EntityCache horse = player.compensatedWorld.getEntity(player.vehicleData.vehicleRuntimeId);
                if (horse != null && horse.getVehicleType() == VehicleType.HORSE) {
                    horse.getHorseGround().localLeave();
                }
            }
        }
    }

    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        // A link can name a player whose entity is not spawned yet. The player list tells us it is a player.
        if (event.getPacket() instanceof PlayerListPacket packet) {
            for (final PlayerListPacket.Entry entry : packet.getEntries()) {
                if (packet.getAction() == PlayerListPacket.Action.ADD) {
                    player.listedPlayers.put(entry.getUuid(), entry.getEntityId());
                } else {
                    player.listedPlayers.remove(entry.getUuid());
                }
            }
            return;
        }

        if (event.getPacket() instanceof SetEntityLinkPacket packet) {
            final EntityLinkData link = packet.getEntityLink();
            if (link == null) {
                return;
            }
            if (player.staticSeatTracker.link(player, link)) {
                return;
            }

            // Both ids in the link are unique ids.
            long vehicleUniqueId = link.getFrom();
            long riderUniqueId = link.getTo();

            final EntityCache cache = player.compensatedWorld.getTrackedEntityByUniqueId(vehicleUniqueId);
            // Every link, ours or not, changes the passenger order, which decides who steers.
            if (cache != null) {
                player.queueAcknowledgment(new PassengerLinkAck(cache.getRuntimeId(), riderUniqueId, player.isPlayerEntity(riderUniqueId),
                        link.getType() == EntityLinkData.Type.REMOVE, link.getVehicleAngularVelocity()));
            }

            // We handle this separately.
            if (riderUniqueId != player.uniqueEntityId) {
                final EntityCache riderCache = player.compensatedWorld.getTrackedEntityByUniqueId(riderUniqueId);
                if (riderCache != null) {
                    riderCache.setInVehicle(link.getType() != EntityLinkData.Type.REMOVE);
                }

                return;
            }

            if (cache == null) {
                // Likely won't happen, but why not!
                return;
            }

            if (link.getType() == EntityLinkData.Type.REMOVE) {
                // A dismount teleport, if any, was sent during this ride, before the link was removed.
                final VehicleData vehicle = player.vehicleData;
                player.queueAcknowledgment(new VehicleClearAck(vehicle == null ? null : player.getTeleportUtil().getLastTeleportSince(vehicle.mountTick)));
                return;
            }

            player.queueAcknowledgment(new VehicleSetAck(cache.getRuntimeId(), vehicleUniqueId, player.tick, System.nanoTime()));
        }
    }
}
