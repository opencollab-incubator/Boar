package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.util.Dimension;

public record DimensionSwitchAck(Dimension dimension, Integer loadingScreenId) implements Acknowledgment {
}
