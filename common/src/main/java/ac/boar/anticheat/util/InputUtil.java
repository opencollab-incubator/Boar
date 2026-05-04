package ac.boar.anticheat.util;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public class InputUtil {
    public static void processInput(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        Vec3 input = Vec3.ZERO.clone();

        // Player literally can't move in these cases.
        if (player.doingInventoryAction || packet.getMotion().getX() == 0 && packet.getMotion().getY() == 0) {
            player.input = input;
            return;
        }

        // This is unconfirmed bug that I haven't been able to reproduce myself, but a certain person report
        // that some players keep repeatedly rollback and their debug from /boar preddebug showing them move in completely wrong direction?
        // Maybe because mojang fucked up and not sending analog move input? Whatever the reason is, let's just grab raw move vector instead of analog
        // it wouldn't make that much of a difference anyway.
        input = new Vec3(MathUtil.clamp(packet.getRawMoveVector().getX(), -1, 1), 0, MathUtil.clamp(packet.getRawMoveVector().getY(), -1, 1));
        if (MathUtil.sign(input.x) == input.x && MathUtil.sign(input.z) == input.z && input.x != 0 && input.z != 0) {
            // Avoid the use of sqrt if possible.
            input = input.multiply(0.70710677F);
        } else {
            float length = input.horizontalLength();
            // Player input should only be normalized if player won't gain any advantage after normalizing input.
            if (length >= 1) {
                input = new Vec3(input.x / length, 0, input.z / length);
            }
        }

        player.input = input;
    }
}
