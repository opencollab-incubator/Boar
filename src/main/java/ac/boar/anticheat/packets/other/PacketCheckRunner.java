package ac.boar.anticheat.packets.other;

import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.impl.PacketCheck;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;

public class PacketCheckRunner implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        for (final Check check : event.getPlayer().getCheckHolder().values()) {
            if (!(check instanceof PacketCheck packetCheck)) {
                continue;
            }

            packetCheck.onPacketSend(event);
        }
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        for (final Check check : event.getPlayer().getCheckHolder().values()) {
            if (!(check instanceof PacketCheck packetCheck)) {
                continue;
            }

            packetCheck.onPacketReceived(event);
        }
    }
}
