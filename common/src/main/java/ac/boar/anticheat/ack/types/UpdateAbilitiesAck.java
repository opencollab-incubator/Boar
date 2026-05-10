package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;

import java.util.List;

public record UpdateAbilitiesAck(List<AbilityLayer> layers) implements Acknowledgment {
}
