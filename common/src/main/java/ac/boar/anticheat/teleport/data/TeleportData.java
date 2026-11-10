package ac.boar.anticheat.teleport.data;

import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;

@RequiredArgsConstructor
@Getter
public class TeleportData {
    private final Vec3 position;
    private final boolean onGround;
    private final Source source;
    private final MovePlayerContext movePlayerContext;

    public TeleportData(Vec3 position, boolean onGround, Source source) {
        this(position, onGround, source, null);
    }

    // The ride object tells us which mount this is, even if the player remounts the same actor.
    // The packet fields pick the client's normal teleport path, not the rewind path or the passenger placement path.
    public record MovePlayerContext(MovePlayerPacket.Mode mode, long ridingRuntimeEntityId, long tick, VehicleData ride) {}

    public boolean isOrdinaryTeleportForRide(final VehicleData ride) {
        return ride != null && this.movePlayerContext != null && this.movePlayerContext.ride() == ride
                && this.movePlayerContext.mode() == MovePlayerPacket.Mode.TELEPORT
                && this.movePlayerContext.ridingRuntimeEntityId() == 0 && this.movePlayerContext.tick() == 0;
    }

    public enum Source {
        MOVE_PLAYER_TELEPORT,
        MOVE_PLAYER_NORMAL,
        MOVE_PLAYER_RESPAWN,
        RESPAWN,
        OTHER
    }
}
