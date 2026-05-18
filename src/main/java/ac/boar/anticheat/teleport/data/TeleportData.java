package ac.boar.anticheat.teleport.data;

import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class TeleportData {
    private final Vec3 position;

    private boolean accepted;
    public void accept() {
        this.accepted = true;
    }
}
