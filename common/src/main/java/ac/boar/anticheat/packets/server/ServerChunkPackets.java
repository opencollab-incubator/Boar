package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.BlockEntityUpdateAck;
import ac.boar.anticheat.ack.types.BlockUpdateAck;
import ac.boar.anticheat.ack.types.ChunkLoadAck;
import ac.boar.anticheat.ack.types.ChunkPublisherUpdateAck;
import ac.boar.anticheat.ack.types.SubChunkLoadAck;
import ac.boar.anticheat.compensated.world.base.CompensatedWorld;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.Dimension;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.util.geyser.BoarChunkSection;
import ac.boar.anticheat.util.geyser.ChunkDecoder;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.data.SubChunkRequestResult;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;

import java.util.Objects;

public class ServerChunkPackets implements PacketListener {
    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        final CompensatedWorld world = player.compensatedWorld;

        if (event.getPacket() instanceof NetworkChunkPublisherUpdatePacket packet) {
            player.sendLatencyStack(new ChunkPublisherUpdateAck(packet.getPosition(), packet.getRadius()));
        } else if (event.getPacket() instanceof LevelChunkPacket packet) {
            // Servers will have to implement their own custom handlers for chunks if they want to use the sub-chunk cache system.
            if (packet.isCachingEnabled()) {
                return;
            }
            // Sub-chunk request mode is enabled - actual chunk data will arrive in SubChunkPackets.
            if (packet.isRequestSubChunks()) {
                return;
            }

            final int subChunksCount = packet.getSubChunksLength();
            if (subChunksCount <= -2 || packet.getDimension() < 0 || packet.getDimension() > 2) {
                // These cases will all be ignored.
                return;
            }

            final int x = packet.getChunkX() << 4, z = packet.getChunkZ() << 4;
            // Avoid spamming latency if possible, unless the player is seriously lagging then this shouldn't false.
            /* if (Math.abs(player.position.x - x) <= 16 || Math.abs(player.position.z - z) <= 16) {
                player.sendLatencyStack();
            } */

            final Dimension dimension = DimensionUtil.dimensionFromId(packet.getDimension());
            final BoarChunkSection[] sections = new BoarChunkSection[dimension.height() >> 4];

            final ByteBuf buf = packet.getData().retainedDuplicate();
            try {
                for (int i = 0; i < subChunksCount; i++) {
                    final ChunkDecoder.DecodedSubChunk decoded = ChunkDecoder.readSubChunk(buf, player.mappingInfo.airId(), i, dimension.minY());
                    if (decoded.sectionY() < 0 || decoded.sectionY() >= sections.length) {
                        continue;
                    }
                    sections[decoded.sectionY()] = decoded.section();
                }

                // Ignore the rest, I only need the chunk data.
            } catch (Exception ignored) {
                // Bedrock just ignore and use whatever they were able to read.
            } finally {
                buf.release();
            }

            player.queueAcknowledgment(new ChunkLoadAck(packet.getChunkX(), packet.getChunkZ(), dimension, sections));
        } else if (event.getPacket() instanceof SubChunkPacket packet) {
            if (packet.isCacheEnabled()) {
                return;
            }

            // TODO: Implement handling custom dimensions
            if (packet.getDimension() < 0 || packet.getDimension() > 2) {
                return;
            }

            final Dimension dimension = DimensionUtil.dimensionFromId(packet.getDimension());
            final Vector3i center = packet.getCenterPosition();

            for (SubChunkData entry : packet.getSubChunks()) {
                final SubChunkRequestResult result = entry.getResult();
                if (result != SubChunkRequestResult.SUCCESS && result != SubChunkRequestResult.SUCCESS_ALL_AIR) {
                    continue;
                }

                final Vector3i offset = entry.getPosition();
                final int chunkX = center.getX() + offset.getX();
                final int chunkZ = center.getZ() + offset.getZ();
                final int sectionY = (center.getY() + offset.getY()) - (dimension.minY() >> 4);

                BoarChunkSection section = null;
                if (result == SubChunkRequestResult.SUCCESS && entry.getData() != null) {
                    final ByteBuf buf = entry.getData().retainedDuplicate();
                    try {
                        section = ChunkDecoder.readSubChunk(buf, player.mappingInfo.airId(), sectionY, dimension.minY()).section();
                    } catch (Exception ignored) {
                    } finally {
                        buf.release();
                    }
                }

                player.queueAcknowledgment(new SubChunkLoadAck(chunkX, chunkZ, sectionY, dimension, section));
            }
        } else if (event.getPacket() instanceof UpdateBlockPacket packet) {
            // Ugly hack.
            if (packet.getDataLayer() == 0 && Boar.getConfig().ignoreGhostBlock() && !player.inLoadingScreen && player.sinceLoadingScreen >= 2) {
                boolean newBlockIsAir = player.mappingInfo.airIds().contains(packet.getDefinition().getRuntimeId());
                boolean oldBlockIsAir = player.mappingInfo.airIds().contains(player.compensatedWorld.getRawBlockAt(packet.getBlockPosition().getX(), packet.getBlockPosition().getY(), packet.getBlockPosition().getZ(), 0));

                if (newBlockIsAir && !oldBlockIsAir) {
                    int distance = Math.abs(packet.getBlockPosition().getY() - GenericMath.floor(player.position.y - 1));
                    if (distance <= 1) {
                        player.tickSinceBlockResync = 5;
                        world.updateBlock(packet.getBlockPosition(), packet.getDataLayer(), packet.getDefinition().getRuntimeId());
                    }
                }
            }

            // Avoid spamming latency if possible, unless the player is seriously lagging then this shouldn't false.
            /* boolean send = player.position.distanceTo(new Vec3(packet.getBlockPosition())) <= 16;
            if (send) {
                player.sendLatencyStack();
            } */

            player.queueAcknowledgment(new BlockUpdateAck(packet.getBlockPosition(), packet.getDataLayer(), packet.getDefinition().getRuntimeId()));
        } else if (event.getPacket() instanceof UpdateSubChunkBlocksPacket packet) {
            // TODO: Figure out the difference between standard block entries and extra block entries and re-evaluate if this current handling is correct.
            for (BlockChangeEntry entry : packet.getStandardBlocks()) {
                player.queueAcknowledgment(new BlockUpdateAck(entry.getPosition(), 0, entry.getDefinition().getRuntimeId()));
            }
            for (BlockChangeEntry entry : packet.getExtraBlocks()) {
                player.queueAcknowledgment(new BlockUpdateAck(entry.getPosition(), 1, entry.getDefinition().getRuntimeId()));
            }
        } else if (event.getPacket() instanceof BlockEntityDataPacket packet) {
            player.sendLatencyStack(new BlockEntityUpdateAck(packet.getBlockPosition(), packet.getData()));
        }
    }

    @Override
    public void onPacketReceived(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof ServerboundLoadingScreenPacket packet && packet.getType() == ServerboundLoadingScreenPacketType.END_LOADING_SCREEN) {
            if (Objects.equals(player.currentLoadingScreen, packet.getLoadingScreenId()) && player.inLoadingScreen) {
                player.currentLoadingScreen = null;
                player.inLoadingScreen = false;
                player.sinceLoadingScreen = 0;
            }
        }
    }
}
