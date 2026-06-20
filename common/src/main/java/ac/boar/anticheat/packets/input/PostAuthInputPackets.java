package ac.boar.anticheat.packets.input;

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

            if (player.vehicleData != null && player.getEntity().vehicle() == null) {
                event.setCancelled(true);
            }

//            if (player.getTeleportUtil().isTeleporting()) {
//                packet.setPosition(player.position.add(0, player.getYOffset(), 0).toVector3f());
//                LegacyAuthInputPackets.correctInputData(player, packet);
//                return;
//            }

            // Although I'd like to avoid to do this at all times, it's justttt to be safe.
            // Shouldn't desync, since we already set it to be the unvalidated position if offset is close enough
            // Look at LegacyAuthInputPackets#doPostPrediction line 58
            packet.setPosition(player.position.add(0, player.getYOffset(), 0).toVector3f());
            LegacyAuthInputPackets.correctInputData(player, packet);

            if (player.getTeleportUtil().isTeleporting()) {
                return;
            }

            if (player.tickSinceBlockResync > 0) player.tickSinceBlockResync--;
        }
    }
}
