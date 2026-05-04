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
        public Normal(Vec3 position) {
            super(position);
        }
    }

    public static class DimensionSwitch extends TeleportCache {
        public DimensionSwitch(Vec3 position) {
            super(position);
        }
    }

    @ToString
    @Getter
    public static class Rewind extends TeleportCache {
        private final long tick;
        private final Vec3 tickEnd;
        private final boolean onGround;

        public Rewind(long tick, Vec3 position, Vec3 tickEnd, boolean onGround) {
            super(position);
            this.tick = tick;
            this.tickEnd = tickEnd;
            this.onGround = onGround;
        }
    }
}
