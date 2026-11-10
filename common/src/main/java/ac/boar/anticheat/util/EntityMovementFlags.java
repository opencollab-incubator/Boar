package ac.boar.anticheat.util;

import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;

/** Reads entity movement packets the way the wire encodes them: the delta flags (by this session's protocol version) and the byte angles. */
public final class EntityMovementFlags {
    // MoveEntityDeltaSerializer_v2168 replaces the old flag bits with separate booleans.
    private static final int SEPARATE_DELTA_FLAGS_PROTOCOL = 2168;

    private EntityMovementFlags() {
    }

    public static boolean isDeltaOnGround(final MoveEntityDeltaPacket packet, final int protocolVersion) {
        if (protocolVersion >= SEPARATE_DELTA_FLAGS_PROTOCOL) {
            return packet.isOnGround();
        }
        return packet.getFlags().contains(MoveEntityDeltaPacket.Flag.ON_GROUND);
    }

    public static boolean isDeltaTeleport(final MoveEntityDeltaPacket packet, final int protocolVersion) {
        if (protocolVersion >= SEPARATE_DELTA_FLAGS_PROTOCOL) {
            return packet.isForceMove();
        }
        return packet.getFlags().contains(MoveEntityDeltaPacket.Flag.TELEPORTING);
    }

    public static boolean isDeltaForceMoveLocal(final MoveEntityDeltaPacket packet, final int protocolVersion) {
        if (protocolVersion >= SEPARATE_DELTA_FLAGS_PROTOCOL) {
            return packet.isForceMoveLocalEntity();
        }
        return packet.getFlags().contains(MoveEntityDeltaPacket.Flag.FORCE_MOVE_LOCAL_ENTITY);
    }

    public static boolean isDeltaForceCompletion(final MoveEntityDeltaPacket packet, final int protocolVersion) {
        if (protocolVersion >= SEPARATE_DELTA_FLAGS_PROTOCOL) {
            return packet.isForceCompletion();
        }
        return packet.getFlags().contains(MoveEntityDeltaPacket.Flag.FORCE_COMPLETION);
    }

    // MoveEntity angles are sent as a signed byte. Round them the way the codec does: divide, cast to byte, multiply. Keep this order.
    public static float wireByteAngle(final float angle) {
        return (byte) (angle / (360F / 256F)) * (360F / 256F);
    }
}
