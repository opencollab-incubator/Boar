package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.EntityMetadataAck;
import ac.boar.anticheat.ack.types.GameTypeAck;
import ac.boar.anticheat.ack.types.PlayerMetadataAck;
import ac.boar.anticheat.ack.types.UpdateAbilitiesAck;
import ac.boar.anticheat.ack.types.UpdateAttributesAck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.PlayerData;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.util.VanillaOffsetUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.validator.blockbreak.ServerBreakBlockValidator;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeOperation;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket;

import java.util.*;

public class ServerDataPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof StartGamePacket start) {
            player.runtimeEntityId = start.getRuntimeEntityId();
            player.sneakingEyeHeightReduction = VanillaOffsetUtil.sneakingEyeHeightReduction(start.getVanillaVersion());

            final Vec3 wirePosition = new Vec3(start.getPlayerPosition());
            final Vec3 playerPosition = wirePosition.down(player.getYOffset());
            player.compensatedWorld.clearChunks();
            player.compensatedWorld.setDimension(DimensionUtil.dimensionFromId(start.getDimensionId()));
            player.setPos(playerPosition, false);
            player.prevPosition = playerPosition.clone();
            player.unvalidatedPosition = playerPosition.clone();
            player.unvalidatedNativeOriginY = player.nativeOriginY;
            player.prevUnvalidatedPosition = playerPosition.clone();
            player.velocity = Vec3.ZERO.clone();
            player.lastTickFinalVelocity = Vec3.ZERO.clone();
            player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
            player.insideUnloadedChunk = true;
            player.getTeleportUtil().reset(wirePosition);
            player.currentLoadingScreen = null;
            player.inLoadingScreen = true;

            start.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER_WITH_REWIND);
            start.setRewindHistorySize(Boar.getConfig().rewindHistory());
            player.serverBreakBlockValidator = (ServerBreakBlockValidator) player.getCheckHolder().get(ServerBreakBlockValidator.class);

            player.sendLatencyStack(new GameTypeAck(start.getPlayerGameType()));
        }

        if (event.getPacket() instanceof SetPlayerGameTypePacket packet) {
            player.sendLatencyStack(new GameTypeAck(GameType.from(packet.getGamemode())));
        }

        if (event.getPacket() instanceof UpdatePlayerGameTypePacket packet && packet.getEntityId() == player.runtimeEntityId) {
            player.sendLatencyStack(new GameTypeAck(packet.getGameType()));
        }

        if (event.getPacket() instanceof UpdateAbilitiesPacket packet) {
            if (packet.getUniqueEntityId() != player.runtimeEntityId) {
                return;
            }

            player.queueAcknowledgment(new UpdateAbilitiesAck(packet.getAbilityLayers()));
        }

        if (event.getPacket() instanceof SetEntityDataPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                final EntityCache cache = player.compensatedWorld.getTrackedEntity(packet.getRuntimeEntityId());
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
            final Boolean observedSwimming = flags == null ? null : flags.get(EntityFlag.SWIMMING);
            final Boolean observedSprinting = flags == null ? null : flags.get(EntityFlag.SPRINTING);
            final boolean observedFlagsKey = packet.getMetadata().containsKey(EntityDataTypes.FLAGS);
            final boolean observedFlags2Key = packet.getMetadata().containsKey(EntityDataTypes.FLAGS_2);
            if (flags == null && height == null && width == null && scale == null && bedPosition == null) {
                return;
            }

            final Set<EntityFlag> flagsCopy;
            Boolean swimming = null;
            if (flags != null) {
                swimming = flags.get(EntityFlag.SWIMMING);
                flagsCopy = EnumSet.noneOf(EntityFlag.class);
                flags.forEach((k, v) -> {
                    if (v != null && v) {
                        flagsCopy.add(k);
                    }
                });
            } else {
                flagsCopy = null;
            }
            if (width != null) {
                width = Math.max(0f, width - 1e-4f);
            }

            // Dimension seems to be controlled server-side as far as I know (tested with clumsy).
            final PlayerMetadataAck acknowledgment = new PlayerMetadataAck(width, height, scale, flagsCopy, swimming, bedPosition, player.tick);
            if (Boar.getConfig().debugMode()) {
                final int acknowledgmentId = System.identityHashCode(acknowledgment);
                player.getMovementTrace().log("metadata enqueue: ack=" + acknowledgmentId + " playerTick=" + player.tick
                        + " packetTick=" + packet.getTick() + " keys=" + packet.getMetadata().keySet()
                        + " air=" + packet.getMetadata().get(EntityDataTypes.AIR_SUPPLY)
                        + " flagMap=" + (flags == null ? 0 : System.identityHashCode(flags))
                        + " hasFlagsKey=" + observedFlagsKey + " hasFlags2Key=" + observedFlags2Key
                        + " observedSwimming=" + observedSwimming + " observedSprinting=" + observedSprinting
                        + " swimming=" + swimming + " flags=" + flagsCopy
                        + " width=" + width + " height=" + height + " scale=" + scale);
            }
            player.queueAcknowledgment(acknowledgment);
        }

        if (event.getPacket() instanceof UpdateAttributesPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }
            if (!packet.getAttributes().isEmpty()) {
                // sometimes the attribute list can be immutable
                List<AttributeData> attributes = new ArrayList<>(packet.getAttributes());
                attributes.replaceAll(ServerDataPackets::stripModifiers);
                packet.setAttributes(attributes);
            }
            player.sendLatencyStack(new UpdateAttributesAck(packet.getAttributes()));
        }
    }

    /* @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        if (event.getPacket() instanceof MovementPredictionSyncPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            if (packet.getSpeed() != player.getSpeed()) {
                player.getEntity().refreshAttributesToSelf();
                // Boar.getInstance().getAlertManager().alert("Speed doesn't match!");
            }

            player.getFlagTracker().set(EntityFlag.SNEAKING, packet.getFlags().contains(EntityFlag.SNEAKING));
            player.getFlagTracker().set(EntityFlag.SWIMMING, packet.getFlags().contains(EntityFlag.SWIMMING) && player.touchingWater);
            player.getFlagTracker().set(EntityFlag.SPRINTING, packet.getFlags().contains(EntityFlag.SPRINTING));

            boolean using = packet.getFlags().contains(EntityFlag.USING_ITEM);
            final boolean staleStop = !using && player.getItemUseTracker().getDirtyUsing() == ItemUseTracker.DirtyUsing.INVENTORY_TRANSACTION;
            if (!staleStop) {
                if (!using) {
                    // This is a shit solution to prevent player to do no slow using this packet but ehhhh
                    // We wouldn't have to do this if we're handling eating properly
                    player.getEntity().releaseItem();
                }

                player.getFlagTracker().set(EntityFlag.USING_ITEM, using);
            }

            final ContainerCache cache = player.compensatedInventory.armorContainer;
            player.getFlagTracker().set(EntityFlag.GLIDING, BoarItemStack.of(player.getSession(), cache.get(1).getData()).is(Items.ELYTRA) && packet.getFlags().contains(EntityFlag.GLIDING));
        }
    } */

    public static AttributeData stripModifiers(AttributeData data) {
        if (data.getName().equals("minecraft:movement") || data.getName().equals("minecraft:underwater_movement") || data.getName().equals("minecraft:lava_movement")) {
            if (data.getModifiers().isEmpty()) {
                return data;
            }

            float newBase = data.getDefaultValue();
            for (final AttributeModifierData modifier : data.getModifiers()) {
                if (modifier.getOperation() == AttributeOperation.ADDITION) {
                    newBase += modifier.getAmount();
                }
            }
            float newValue = newBase;
            for (final AttributeModifierData modifier : data.getModifiers()) {
                if (modifier.getOperation() == AttributeOperation.MULTIPLY_BASE) {
                    newValue += (newBase * modifier.getAmount());
                }
            }
            for (final AttributeModifierData modifier : data.getModifiers()) {
                if (modifier.getOperation() == AttributeOperation.MULTIPLY_TOTAL && !modifier.equals(PlayerData.SPRINTING_SPEED_BOOST)) {
                    newValue *= (1.0F + modifier.getAmount());
                }
            }

            // System.out.println(data + " -> " + new AttributeData(data.getName(), data.getMinimum(), data.getMaximum(), newValue, newBase));
            return new AttributeData(data.getName(), data.getMinimum(), data.getMaximum(), newValue, newBase);
        }
        return data;
    }

}
