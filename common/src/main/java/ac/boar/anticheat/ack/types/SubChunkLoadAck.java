package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.util.Dimension;
import ac.boar.anticheat.util.geyser.BoarChunkSection;

public record SubChunkLoadAck(int chunkX, int chunkZ, int sectionY, Dimension dimension, BoarChunkSection section, boolean known) implements Acknowledgment {
}
