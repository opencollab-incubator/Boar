package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.world.base.CompensatedWorld;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.util.geyser.BlockStorage;
import ac.boar.anticheat.util.geyser.BoarChunk;
import ac.boar.anticheat.util.geyser.BoarChunkSection;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.chunk.bitarray.BitArray;
import org.geysermc.geyser.level.chunk.bitarray.BitArrayVersion;
import org.geysermc.geyser.level.chunk.bitarray.SingletonBitArray;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;

import java.util.Objects;

public class ServerChunkPackets implements PacketListener {
    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        final CompensatedWorld world = player.compensatedWorld;

        if (event.getPacket() instanceof NetworkChunkPublisherUpdatePacket packet) {
            player.sendLatencyStack(() -> {
                world.setCenterX(packet.getPosition().getX() >> 4);
                world.setCenterZ(packet.getPosition().getZ() >> 4);
                world.setRadius(packet.getRadius());

                world.yeetOutOfRangeChunks();
            });
        }

        // There are a ton of chunk sending and world type for Bedrock (why support that much anyway)
        // But since Geyser only use this, then we only added support for this, no need to torture ourselves.
        if (event.getPacket() instanceof LevelChunkPacket packet) {
            final int subChunksCount = packet.getSubChunksLength();
            if (subChunksCount <= -2 || packet.getDimension() < 0 || packet.getDimension() > 2) {
                // These cases will all be ignored.
                return;
            }

            final int x = packet.getChunkX() << 4, z = packet.getChunkZ() << 4;
            // Avoid spamming latency if possible, unless the player is seriously lagging then this shouldn't false.
            if (Math.abs(player.position.x - x) <= 16 || Math.abs(player.position.z - z) <= 16) {
                player.sendLatencyStack();
            }

            final BedrockDimension dimension = DimensionUtil.dimensionFromId(packet.getDimension());
            final BoarChunkSection[] sections = new BoarChunkSection[dimension.height() >> 4];

            final ByteBuf buf = packet.getData().retainedDuplicate();
            try {
                for (int sectionY = 0; sectionY < subChunksCount; sectionY++) {
                    buf.readByte(); // Chunk format version, Geyser only use the latest version 9 so this info is useless to us.

                    final short layerCount = buf.readUnsignedByte(); // Layers (ideally 2).
                    buf.readUnsignedByte(); // Sub chunk index, useless information to us.

                    final BlockStorage[] layers = new BlockStorage[layerCount];
                    for (int layer = 0; layer < layerCount; layer++) {
                        layers[layer] = readLayer(buf, player.BEDROCK_AIR);
                    }

                    sections[sectionY] = new BoarChunkSection(layers);
                }

                // Ignore the rest, I only need the chunk data.
            } catch (Exception ignored) {
                // Bedrock just ignore and use whatever they were able to read.
            } finally {
                buf.release();
            }

            player.getLatencyUtil().queue(() -> {
                if (!player.compensatedWorld.isInLoadDistance(packet.getChunkX(), packet.getChunkZ()) || dimension != player.compensatedWorld.getDimension()) {
//                    System.out.println("Out of distance...");
                    return;
                }

                world.put(packet.getChunkX(), packet.getChunkZ(), sections);
            });
        }

        if (event.getPacket() instanceof UpdateBlockPacket packet) {
            // Ugly hack.
            if (packet.getDataLayer() == 0 && Boar.getConfig().ignoreGhostBlock() && !player.inLoadingScreen && player.sinceLoadingScreen >= 2) {
                boolean newBlockIsAir = player.AIR_IDS.contains(packet.getDefinition().getRuntimeId());
                boolean oldBlockIsAir = player.AIR_IDS.contains(player.compensatedWorld.getRawBlockAt(packet.getBlockPosition().getX(), packet.getBlockPosition().getY(), packet.getBlockPosition().getZ(), 0));

                if (newBlockIsAir && !oldBlockIsAir) {
                    int distance = Math.abs(packet.getBlockPosition().getY() - GenericMath.floor(player.position.y - 1));
                    if (distance <= 1) {
                        player.tickSinceBlockResync = 5;
                        world.updateBlock(packet.getBlockPosition(), packet.getDataLayer(), packet.getDefinition().getRuntimeId());
                    }
                }
            }

            // Avoid spamming latency if possible, unless the player is seriously lagging then this shouldn't false.
            boolean send = player.position.distanceTo(new Vec3(packet.getBlockPosition())) <= 16;
            if (send) {
                player.sendLatencyStack();
            }

            player.getLatencyUtil().queue(() -> world.updateBlock(packet.getBlockPosition(), packet.getDataLayer(), packet.getDefinition().getRuntimeId()));
        }

        if (event.getPacket() instanceof BlockEntityDataPacket packet) {
            player.sendLatencyStack();
            player.getLatencyUtil().queue(() -> {
                final BoarChunk chunk = player.compensatedWorld.getChunk(packet.getBlockPosition().getX() >> 4, packet.getBlockPosition().getZ() >> 4);
                if (chunk == null) {
                    return;
                }

                final Vector3i pos = packet.getBlockPosition();
                chunk.blockEntities().removeIf(block -> block.getX() == pos.getX() && block.getY() == pos.getY() && block.getZ() == pos.getZ());
                chunk.blockEntities().add(new BlockEntityInfo(pos.getX(), pos.getY(), pos.getZ(), null, packet.getData()));
            });
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

    private BlockStorage readLayer(final ByteBuf buf, final int initialId) {
        final int version = buf.readUnsignedByte() >> 1;
        if (version == 127) { // 127 = Same values as previous palette
            return null;
        }

        final BitArray bitArray;
        if (version == 0) {
            bitArray = BitArrayVersion.get(version, true).createArray(4096, null);
        } else {
            bitArray = BitArrayVersion.get(version, true).createArray(4096);
        }

        if (!(bitArray instanceof SingletonBitArray)) {
            for (int i = 0; i < bitArray.getWords().length; i++) {
                bitArray.getWords()[i] = buf.readIntLE();
            }
        }

        final int size = bitArray instanceof SingletonBitArray ? 1 : VarInts.readInt(buf);

        final IntList palette = new IntArrayList(size);
        for (int i = 0; i < size; i++) {
            palette.add(VarInts.readInt(buf));
        }

        if (palette.isEmpty()) {
            palette.add(initialId);
        }
        return new BlockStorage(bitArray, palette);
    }
}