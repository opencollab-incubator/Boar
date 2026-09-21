package ac.boar.anticheat.teleport.data;

import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class TeleportData {
    private final Vec3 position;
    private final boolean onGround;
    private final Source source;

    public enum Source {
        MOVE_PLAYER_TELEPORT,
        MOVE_PLAYER_NORMAL,
        MOVE_PLAYER_RESPAWN,
        RESPAWN,
        OTHER
    }
}
