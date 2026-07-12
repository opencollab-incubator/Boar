package ac.boar.anticheat.teleport.data;

import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;

@Getter
public class RewindData extends TeleportData {
    private final long tick;
    private final Vec3 tickEnd;
    private final boolean onGround;

    public RewindData(long tick, Vec3 position, Vec3 tickEnd, boolean ground) {
        super(position);
        this.tick = tick;
        this.tickEnd = tickEnd;
        this.onGround = ground;
    }
}
