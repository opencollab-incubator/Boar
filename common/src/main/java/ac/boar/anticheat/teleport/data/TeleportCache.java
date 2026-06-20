package ac.boar.anticheat.teleport.data;

import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public class TeleportCache {

    private final Vec3 position;

    @Setter
    private boolean accepted;

    @Getter
    public static class Normal extends TeleportCache {
        private final boolean onGround;

        public Normal(Vec3 position, boolean onGround) {
            super(position);
            this.onGround = onGround;
        }
    }

    public static class DimensionSwitch extends TeleportCache {
        public DimensionSwitch(Vec3 position) {
            super(position);
        }
    }
}
