package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.EntityMetadataAck;
import ac.boar.anticheat.ack.types.GameTypeAck;
import ac.boar.anticheat.ack.types.PlayerMetadataAck;
import ac.boar.anticheat.ack.types.UpdateAbilitiesAck;
import ac.boar.anticheat.ack.types.UpdateAttributesAck;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.inventory.BoarItemStack;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.validator.blockbreak.ServerBreakBlockValidator;
import ac.boar.mappings.item.Items;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MovementPredictionSyncPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

public class ServerDataPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof StartGamePacket start) {
            player.runtimeEntityId = start.getRuntimeEntityId();

            player.compensatedWorld.setDimension(DimensionUtil.dimensionFromId(start.getDimensionId()));
            player.currentLoadingScreen = null;
            player.inLoadingScreen = true;

            // We need this to do rewind teleport.
            start.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER_WITH_REWIND);
            start.setRewindHistorySize(Boar.getConfig().rewindHistory());
            player.serverBreakBlockValidator = new ServerBreakBlockValidator(player);

            player.sendLatencyStack(new GameTypeAck(start.getPlayerGameType()));
        }

        if (event.getPacket() instanceof SetPlayerGameTypePacket packet) {
            player.sendLatencyStack(new GameTypeAck(GameType.from(packet.getGamemode())));
        }

        if (event.getPacket() instanceof UpdateAbilitiesPacket packet) {
            if (packet.getUniqueEntityId() != player.runtimeEntityId) {
                return;
            }

            player.queueAcknowledgment(new UpdateAbilitiesAck(packet.getAbilityLayers()));
        }

        if (event.getPacket() instanceof SetEntityDataPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                final EntityCache cache = player.compensatedWorld.getEntity(player.runtimeEntityId);
                if (cache == null) {
                    return;
                }

                // No need to send latency, we only use a few's metadata values from them and most of them almost never actually changed so we should be good,
                // for eg: (COLLIDEABLE flag is always true for certain entity regardless of what).
                player.queueAcknowledgment(new EntityMetadataAck(cache.getRuntimeId(), packet.getMetadata()));
                return;
            }

            if (player.vehicleData != null) {
                return;
            }

            Float height = packet.getMetadata().get(EntityDataTypes.HEIGHT);
            Float width = packet.getMetadata().get(EntityDataTypes.WIDTH);
            Float scale = packet.getMetadata().get(EntityDataTypes.SCALE);
            Vector3i bedPosition = packet.getMetadata().get(EntityDataTypes.BED_POSITION);

            final EnumMap<EntityFlag, Boolean> flags = packet.getMetadata().getFlags();
            if (flags == null && height == null && width == null && scale == null && bedPosition == null) {
                return;
            }

            final Set<EntityFlag> flagsCopy;
            if (flags != null) {
                flagsCopy = EnumSet.noneOf(EntityFlag.class);
                flags.forEach((k, v) -> {
                    if (v != null && v) {
                        flagsCopy.add(k);
                    }
                });
            } else {
                flagsCopy = null;
            }

            // Dimension seems to be controlled server-side as far as I know (tested with clumsy).
            player.queueAcknowledgment(new PlayerMetadataAck(width, height, scale, flagsCopy, bedPosition));
        }

        if (event.getPacket() instanceof UpdateAttributesPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            player.sendLatencyStack(new UpdateAttributesAck(packet.getAttributes()));
        }
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        if (event.getPacket() instanceof MovementPredictionSyncPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            if (packet.getSpeed() != player.getSpeed()) {
                Boar.debug("[attribute-debug] MovementPredictionSync clientSpeed=" + packet.getSpeed() + " boarSpeed=" + player.getSpeed() + " -> refreshAttributesToSelf()", Boar.DebugMessage.WARNING);
                player.getEntity().refreshAttributesToSelf();
                // Boar.getInstance().getAlertManager().alert("Speed doesn't match!");
            }

            player.getFlagTracker().set(EntityFlag.SNEAKING, packet.getFlags().contains(EntityFlag.SNEAKING));
            player.getFlagTracker().set(EntityFlag.SWIMMING, packet.getFlags().contains(EntityFlag.SWIMMING) && player.touchingWater);
            player.getFlagTracker().set(EntityFlag.SPRINTING, packet.getFlags().contains(EntityFlag.SPRINTING));

            boolean using = packet.getFlags().contains(EntityFlag.USING_ITEM);
            if (!using) {
                // This is a shit solution to prevent player to do no slow using this packet but ehhhh
                // We wouldn't have to do this if we're handling eating properly
                player.getEntity().releaseItem();
            }

            player.getFlagTracker().set(EntityFlag.USING_ITEM, using);

            final ContainerCache cache = player.compensatedInventory.armorContainer;
            player.getFlagTracker().set(EntityFlag.GLIDING, BoarItemStack.of(player.getSession(), cache.get(1).getData()).is(Items.ELYTRA) && packet.getFlags().contains(EntityFlag.GLIDING));
        }
    }
}
