package ac.boar.anticheat.packets.server;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.validator.blockbreak.ServerBreakBlockValidator;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.item.Items;

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

            player.sendLatencyStack(() -> player.gameType = start.getPlayerGameType());
        }

        if (event.getPacket() instanceof SetPlayerGameTypePacket packet) {
            player.sendLatencyStack(() -> player.gameType = GameType.from(packet.getGamemode()));
        }

        if (event.getPacket() instanceof UpdateAbilitiesPacket packet) {
            if (packet.getUniqueEntityId() != player.runtimeEntityId) {
                return;
            }

            event.getPostTasks().add(() -> player.sendLatencyStack(() -> {
                player.abilities.clear();
                for (AbilityLayer layer : packet.getAbilityLayers()) {
                    player.abilities.addAll(layer.getAbilityValues());
                }

                player.getFlagTracker().setFlying(player.abilities.contains(Ability.FLYING) || player.abilities.contains(Ability.MAY_FLY) && player.getFlagTracker().isFlying());
            }));
        }

        if (event.getPacket() instanceof SetEntityDataPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                final EntityCache cache = player.compensatedWorld.getEntity(player.runtimeEntityId);
                if (cache == null) {
                    return;
                }

                // No need to send latency, we only use a few's metadata values from them and most of them almost never actually changed so we should be good,
                // for eg: (COLLIDEABLE flag is always true for certain entity regardless of what).
                player.getLatencyUtil().queue(() -> cache.setMetadata(packet.getMetadata()));
                return;
            }

            if (player.vehicleData != null) {
                return;
            }

            Float height = packet.getMetadata().get(EntityDataTypes.HEIGHT);
            Float width = packet.getMetadata().get(EntityDataTypes.WIDTH);
            Float scale = packet.getMetadata().get(EntityDataTypes.SCALE);

            final EnumMap<EntityFlag, Boolean> flags = packet.getMetadata().getFlags();
            if (flags == null && height == null && width == null && scale == null) {
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

            player.sendLatencyStack();

//            final long id = player.sentStackId.get();
//            player.desyncedFlag.set(flagsCopy != null ? id : -1);
            player.getLatencyUtil().queue(() -> {
                if (flagsCopy != null) {
                    player.getFlagTracker().set(player, flagsCopy);
                }

                // Dimension seems to be controlled server-side as far as I know (tested with clumsy).

                if (width != null) {
                    player.dimensions = EntityDimensions.fixed(width, player.dimensions.height()).withEyeHeight(player.dimensions.eyeHeight());
                    player.boundingBox = player.dimensions.getBoxAt(player.position);
                    // System.out.println("Update width!");
                }

                if (height != null) {
                    float eyeHeight = 1.62F;
                    if (Math.abs(height - 0.2F) <= 1.0E-3) {
                        eyeHeight = 0.2F;
                    } else if (Math.abs(height - 0.6F) <= 1.0E-3) {
                        eyeHeight = 0.4F;
                    } else if (Math.abs(height - 1.5F) <= 1.0E-3) {
                        eyeHeight = 1.27F;
                    }

                    player.dimensions = EntityDimensions.fixed(player.dimensions.width(), height).withEyeHeight(eyeHeight);
                    player.boundingBox = player.dimensions.getBoxAt(player.position);
                    // System.out.println("Update height!");
                }

                if (scale != null) {
                    player.dimensions = player.dimensions.hardScaled(scale);
                }

//                if (player.desyncedFlag.get() == id) {
//                    player.desyncedFlag.set(-1);
//                }
            });
        }

        if (event.getPacket() instanceof UpdateAttributesPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            player.sendLatencyStack(() -> {
                if (player.vehicleData != null) {
                    return;
                }

                for (final AttributeData data : packet.getAttributes()) {
                    final AttributeInstance attribute = player.attributes.get(data.getName());
                    if (attribute == null) {
                        return;
                    }

                    attribute.clearModifiers();
                    attribute.setBaseValue(data.getDefaultValue());
                    attribute.setValue(data.getValue());

                    // This is useless since there is no modifiers but still be here if Geyser decide to change this in the future.
                    for (AttributeModifierData lv5 : data.getModifiers()) {
                        attribute.addTemporaryModifier(lv5);
                    }
                }
            });
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
                final SessionPlayerEntity entity = player.getSession().getPlayerEntity();

                UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
                attributesPacket.setRuntimeEntityId(entity.geyserId());
                attributesPacket.getAttributes().addAll(entity.getAttributes().values());
                player.getSession().sendUpstreamPacket(attributesPacket);

                // Boar.getInstance().getAlertManager().alert("Speed doesn't match!");
            }

            player.getFlagTracker().set(EntityFlag.SNEAKING, packet.getFlags().contains(EntityFlag.SNEAKING));
            player.getFlagTracker().set(EntityFlag.SWIMMING, packet.getFlags().contains(EntityFlag.SWIMMING) && player.touchingWater);
            player.getFlagTracker().set(EntityFlag.SPRINTING, packet.getFlags().contains(EntityFlag.SPRINTING));

            boolean using = packet.getFlags().contains(EntityFlag.USING_ITEM);
            if (!using) {
                // This is a shit solution to prevent player to do no slow using this packet but ehhhh
                // We wouldn't have to do this if we're handling eating properly but brah, I'm retarded.
                player.getSession().releaseItem();
            }

            player.getFlagTracker().set(EntityFlag.USING_ITEM, using);

            final ContainerCache cache = player.compensatedInventory.armorContainer;
            player.getFlagTracker().set(EntityFlag.GLIDING, player.compensatedInventory.translate(cache.get(1).getData()).getId() == Items.ELYTRA.javaId() && packet.getFlags().contains(EntityFlag.GLIDING));
        }
    }
}
