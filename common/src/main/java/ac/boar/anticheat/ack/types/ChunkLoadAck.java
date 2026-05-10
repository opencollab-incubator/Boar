package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.util.Dimension;
import ac.boar.anticheat.util.geyser.BoarChunkSection;

public record ChunkLoadAck(int chunkX, int chunkZ, Dimension dimension, BoarChunkSection[] sections) implements Acknowledgment {
}
