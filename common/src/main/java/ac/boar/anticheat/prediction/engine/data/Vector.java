package ac.boar.anticheat.prediction.engine.data;

import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
public class Vector {
    public final static Vector NONE = new Vector(VectorType.NORMAL, Vec3.ZERO);

    private long stackId = -1;

    private Vec3 velocity;
    private VectorType type;
    private boolean ignored;
    private boolean twice;

    public Vector(final VectorType type, final Vec3 vec3) {
        this.type = type;
        this.velocity = vec3;
    }

    public Vector(final VectorType type, final Vec3 vec3, final long stackId) {
        this.type = type;
        this.velocity = vec3;
        this.stackId = stackId;
    }
}