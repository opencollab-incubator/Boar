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
        RESPAWN,
        OTHER
    }

    private boolean accepted;
    public void accept() {
        this.accepted = true;
    }
}
