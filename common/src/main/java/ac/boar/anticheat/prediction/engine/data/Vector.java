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

    private Vec3 velocity;
    private VectorType type;

    public Vector(final VectorType type, final Vec3 vec3) {
        this.type = type;
        this.velocity = vec3;
    }
}