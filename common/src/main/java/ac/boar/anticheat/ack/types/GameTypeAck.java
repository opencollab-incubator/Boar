package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.GameType;

public record GameTypeAck(GameType gameType) implements Acknowledgment {
}
