package ac.boar.anticheat.check.impl.badpackets;

import ac.boar.anticheat.check.api.BaseCheck;
import ac.boar.anticheat.check.api.impl.PacketCheck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.protocol.api.CloudburstPacketEvent;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

@CheckInfo(name = "Bad Packet", type = "B")
public class BadPacketB extends BaseCheck implements PacketCheck {
    public BadPacketB(BoarPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceived(CloudburstPacketEvent event) {
        if (!(event.getPacket() instanceof PlayerAuthInputPacket packet)) {
            return;
        }

        float wrappedY = MathUtil.wrapDegrees(packet.getRotation().getY());
        float wrappedX = MathUtil.clamp(packet.getRotation().getX(), -90, 90);
        if (wrappedY != packet.getRotation().getY()) { // While this is vanilla behaviour on Java, not the case with Bedrock.
            fail("claimedYaw=" + packet.getRotation().getY() + ", wrappedYaw=" + wrappedY);
        } else if (wrappedX != packet.getRotation().getX()) {
            fail("claimedPitch=" + packet.getRotation().getX() + ", wrappedPitch=" + wrappedX);
        } else {
            return;
        }

        // Should be safe to kick?
        player.kick("Invalid auth input packet!");
    }
}
