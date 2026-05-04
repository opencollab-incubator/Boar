package ac.boar.anticheat.data;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;

@ToString
@Getter
@Setter
public class BreakingData {
    private PlayerActionType state;
    private Vector3i position;
    private int face;

    private float breakingProcess;

    public BreakingData(PlayerActionType state, Vector3i position, int face) {
        this.state = state;
        this.position = position;
        this.face = face;
    }
}
