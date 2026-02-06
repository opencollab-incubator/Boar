package ac.boar.anticheat.check.impl.badpackets;

import ac.boar.anticheat.check.api.annotations.CheckInfo;
import ac.boar.anticheat.check.api.impl.PacketCheck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.protocol.api.CloudburstPacketEvent;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

@CheckInfo(name = "Bad Packet", type = "A")
public class BadPacketA extends PacketCheck {
    public BadPacketA(BoarPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceived(CloudburstPacketEvent event) {
        if (event.getPacket() instanceof PlayerAuthInputPacket packet) {
            if (!MathUtil.isValid(packet.getPosition()) || !MathUtil.isValid(packet.getRotation()) || !MathUtil.isValid(packet.getDelta())) {
                fail("pos=" + packet.getPosition() + ", rot=" + packet.getRotation() + ", delta=" + packet.getDelta());
                player.kick("Invalid auth input packet!");
            }
        }
    }
}
