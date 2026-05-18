package ac.boar.anticheat.packets.input;

import ac.boar.anticheat.data.input.TickData;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public class PostAuthInputPackets implements PacketListener {
    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof PlayerAuthInputPacket packet) {
            player.dirtyRiptide = false;
            player.thisTickSpinAttack = false;
            player.thisTickOnGroundSpinAttack = false;
            player.doingInventoryAction = false;
            player.hasDepthStrider = false;
            player.nearBamboo = false;

            player.getTeleportUtil().getAuthInputHistory().put(packet.getTick(), new TickData(packet, player.getFlagTracker().cloneFlags(), player.dimensions));

            if (player.vehicleData != null && player.getSession().getPlayerEntity().getVehicle() == null) {
                event.setCancelled(true);
            }

            // Although I'd like to avoid to do this at all times, it's justttt to be safe.
            // Shouldn't desync, since we already set it to be the unvalidated position if offset is close enough
            // Look at LegacyAuthInputPackets#doPostPrediction line 58
            packet.setPosition(player.position.add(0, player.getYOffset(), 0).toVector3f());
            LegacyAuthInputPackets.correctInputData(player, packet);

            // If the player is teleporting, NO PACKET SHOULD EVER PASS THROUGH. Except when they're rewinding.
            if (player.getTeleportUtil().isHardTeleporting()) {
                event.setCancelled(true);
                return;
            }

            if (player.tickSinceBlockResync > 0) player.tickSinceBlockResync--;
            player.getTeleportUtil().pollRewindHistory();
        }
    }
}