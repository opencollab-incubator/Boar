package ac.boar.anticheat.ack;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.BlockEntityUpdateAck;
import ac.boar.anticheat.ack.types.BlockUpdateAck;
import ac.boar.anticheat.ack.types.ChunkLoadAck;
import ac.boar.anticheat.ack.types.ChunkPublisherUpdateAck;
import ac.boar.anticheat.ack.types.ContainerOpenAck;
import ac.boar.anticheat.ack.types.CraftingDataAck;
import ac.boar.anticheat.ack.types.CreativeContentAck;
import ac.boar.anticheat.ack.types.DimensionSwitchAck;
import ac.boar.anticheat.ack.types.EntityInterpolateAck;
import ac.boar.anticheat.ack.types.EntityMetadataAck;
import ac.boar.anticheat.ack.types.EntityRemoveAck;
import ac.boar.anticheat.ack.types.GameTypeAck;
import ac.boar.anticheat.ack.types.GlideBoostAck;
import ac.boar.anticheat.ack.types.HotbarSlotAck;
import ac.boar.anticheat.ack.types.InventoryContentAck;
import ac.boar.anticheat.ack.types.InventorySlotAck;
import ac.boar.anticheat.ack.types.MobEffectAck;
import ac.boar.anticheat.ack.types.PlayerMetadataAck;
import ac.boar.anticheat.ack.types.TeleportAcceptAck;
import ac.boar.anticheat.ack.types.UpdateAbilitiesAck;
import ac.boar.anticheat.ack.types.UpdateAttributesAck;
import ac.boar.anticheat.ack.types.UpdateTradeAck;
import ac.boar.anticheat.ack.types.VehicleClearAck;
import ac.boar.anticheat.ack.types.VehicleSetAck;
import ac.boar.anticheat.ack.types.VelocityAck;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.compensated.cache.container.impl.TradeContainerCache;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.data.vanilla.StatusEffect;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.geyser.BlockEntityInfo;
import ac.boar.anticheat.util.geyser.BoarChunk;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.MultiRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;

import java.util.Objects;

public final class BoarDefaultAcknowledgments {

    private BoarDefaultAcknowledgments() {
    }

    public static void registerAll(BoarAcknowledgmentRegistry registry) {
        registry.register(ChunkPublisherUpdateAck.class, BoarDefaultAcknowledgments::handleChunkPublisherUpdate);
        registry.register(ChunkLoadAck.class, BoarDefaultAcknowledgments::handleChunkLoad);
        registry.register(BlockUpdateAck.class, BoarDefaultAcknowledgments::handleBlockUpdate);
        registry.register(BlockEntityUpdateAck.class, BoarDefaultAcknowledgments::handleBlockEntityUpdate);

        registry.register(DimensionSwitchAck.class, BoarDefaultAcknowledgments::handleDimensionSwitch);

        registry.register(EntityRemoveAck.class, BoarDefaultAcknowledgments::handleEntityRemove);
        registry.register(EntityInterpolateAck.class, BoarDefaultAcknowledgments::handleEntityInterpolate);
        registry.register(EntityMetadataAck.class, BoarDefaultAcknowledgments::handleEntityMetadata);

        registry.register(GameTypeAck.class, BoarDefaultAcknowledgments::handleGameType);
        registry.register(UpdateAbilitiesAck.class, BoarDefaultAcknowledgments::handleUpdateAbilities);
        registry.register(PlayerMetadataAck.class, BoarDefaultAcknowledgments::handlePlayerMetadata);
        registry.register(UpdateAttributesAck.class, BoarDefaultAcknowledgments::handleUpdateAttributes);

        registry.register(VelocityAck.class, BoarDefaultAcknowledgments::handleVelocity);
        registry.register(GlideBoostAck.class, BoarDefaultAcknowledgments::handleGlideBoost);

        registry.register(CreativeContentAck.class, BoarDefaultAcknowledgments::handleCreativeContent);
        registry.register(CraftingDataAck.class, BoarDefaultAcknowledgments::handleCraftingData);
        registry.register(ContainerOpenAck.class, BoarDefaultAcknowledgments::handleContainerOpen);
        registry.register(UpdateTradeAck.class, BoarDefaultAcknowledgments::handleUpdateTrade);
        registry.register(InventorySlotAck.class, BoarDefaultAcknowledgments::handleInventorySlot);
        registry.register(InventoryContentAck.class, BoarDefaultAcknowledgments::handleInventoryContent);
        registry.register(HotbarSlotAck.class, BoarDefaultAcknowledgments::handleHotbarSlot);

        registry.register(MobEffectAck.class, BoarDefaultAcknowledgments::handleMobEffect);

        registry.register(VehicleClearAck.class, BoarDefaultAcknowledgments::handleVehicleClear);
        registry.register(VehicleSetAck.class, BoarDefaultAcknowledgments::handleVehicleSet);

        registry.register(TeleportAcceptAck.class, BoarDefaultAcknowledgments::handleTeleportAccept);
    }

    private static void handleChunkPublisherUpdate(BoarPlayer player, ChunkPublisherUpdateAck ack) {
        player.compensatedWorld.setRadiusCenter(ack.position());
        player.compensatedWorld.setRadius(ack.radius());
        player.compensatedWorld.yeetOutOfRangeChunks();
    }

    private static void handleChunkLoad(BoarPlayer player, ChunkLoadAck ack) {
        if (player.compensatedWorld.isOutOfRadius(ack.chunkX() << 4, ack.chunkZ() << 4) || ack.dimension() != player.compensatedWorld.getDimension()) {
            return;
        }
        player.compensatedWorld.put(ack.chunkX(), ack.chunkZ(), ack.sections());
    }

    private static void handleBlockUpdate(BoarPlayer player, BlockUpdateAck ack) {
        player.compensatedWorld.updateBlock(ack.position(), ack.layer(), ack.runtimeId());
    }

    private static void handleBlockEntityUpdate(BoarPlayer player, BlockEntityUpdateAck ack) {
        final BoarChunk chunk = player.compensatedWorld.getChunk(ack.position().getX() >> 4, ack.position().getZ() >> 4);
        if (chunk == null) {
            return;
        }
        final Vector3i pos = ack.position();
        chunk.blockEntities().removeIf(b -> b.x() == pos.getX() && b.y() == pos.getY() && b.z() == pos.getZ());
        chunk.blockEntities().add(new BlockEntityInfo(pos.getX(), pos.getY(), pos.getZ(), ack.data()));
    }

    private static void handleDimensionSwitch(BoarPlayer player, DimensionSwitchAck ack) {
        if (player.compensatedWorld.getDimension() != ack.dimension()) {
            player.currentLoadingScreen = ack.loadingScreenId();
            player.inLoadingScreen = true;
        }
        player.compensatedWorld.getChunks().clear();
        player.compensatedWorld.setDimension(ack.dimension());
        player.getFlagTracker().clear();
        player.getFlagTracker().flying(false);
    }

    private static void handleEntityRemove(BoarPlayer player, EntityRemoveAck ack) {
        if (player.vehicleData != null && player.vehicleData.vehicleRuntimeId == ack.uniqueEntityId()) {
            player.vehicleData = null;
        }
        player.compensatedWorld.removeEntity(ack.uniqueEntityId());
    }

    private static void handleEntityInterpolate(BoarPlayer player, EntityInterpolateAck ack) {
        final EntityCache entity = player.compensatedWorld.getEntity(ack.runtimeEntityId());
        if (entity != null) {
            Boar.debug("[entity-ack] dispatch interpolate runtimeId=" + ack.runtimeEntityId() + " pos=" + ack.position() + " lerp=" + ack.lerp() + " before=" + entity.getCurrent().getPos(), Boar.DebugMessage.INFO);
            entity.interpolate(ack.position(), ack.lerp());
        } else {
            Boar.debug("[entity-ack] missing entity for interpolate runtimeId=" + ack.runtimeEntityId() + " pos=" + ack.position(), Boar.DebugMessage.WARNING);
        }
    }

    private static void handleEntityMetadata(BoarPlayer player, EntityMetadataAck ack) {
        final EntityCache cache = player.compensatedWorld.getEntity(ack.runtimeEntityId());
        if (cache != null) {
            cache.setMetadata(ack.metadata());
        }
    }

    private static void handleGameType(BoarPlayer player, GameTypeAck ack) {
        player.gameType = ack.gameType();
    }

    private static void handleUpdateAbilities(BoarPlayer player, UpdateAbilitiesAck ack) {
        player.abilities.clear();
        for (AbilityLayer layer : ack.layers()) {
            player.abilities.addAll(layer.getAbilityValues());
        }
        player.getFlagTracker().setFlying(player.abilities.contains(Ability.FLYING) || player.abilities.contains(Ability.MAY_FLY) && player.getFlagTracker().isFlying());
    }

    private static void handlePlayerMetadata(BoarPlayer player, PlayerMetadataAck ack) {
        if (ack.flags() != null) {
            player.getFlagTracker().set(player, ack.flags());
        }

        if (ack.width() != null) {
            player.dimensions = EntityDimensions.fixed(ack.width(), player.dimensions.height()).withEyeHeight(player.dimensions.eyeHeight());
            player.boundingBox = player.dimensions.getBoxAt(player.position);
        }

        if (ack.height() != null) {
            float eyeHeight = 1.62F;
            if (Math.abs(ack.height() - 0.2F) <= 1.0E-3) {
                eyeHeight = 0.2F;
            } else if (Math.abs(ack.height() - 0.6F) <= 1.0E-3) {
                eyeHeight = 0.4F;
            } else if (Math.abs(ack.height() - 1.5F) <= 1.0E-3) {
                eyeHeight = 1.27F;
            }
            player.dimensions = EntityDimensions.fixed(player.dimensions.width(), ack.height()).withEyeHeight(eyeHeight);
            player.boundingBox = player.dimensions.getBoxAt(player.position);
        }

        if (ack.scale() != null) {
            player.dimensions = player.dimensions.hardScaled(ack.scale());
        }
    }

    private static void handleUpdateAttributes(BoarPlayer player, UpdateAttributesAck ack) {
        if (player.vehicleData != null) {
            return;
        }

        for (final AttributeData data : ack.attributes()) {
            final AttributeInstance attribute = player.attributes.get(data.getName());
            if (attribute == null) {
                return;
            }

            attribute.clearModifiers();
            attribute.setBaseValue(data.getDefaultValue());
            attribute.setValue(data.getValue());

            for (AttributeModifierData mod : data.getModifiers()) {
                attribute.addTemporaryModifier(mod);
            }
        }
    }

    private static void handleVelocity(BoarPlayer player, VelocityAck ack) {
        player.certainVelocity = new Vector(VectorType.VELOCITY, ack.motion());
        Boar.debug("[velocity-ack] dispatch velocity=" + ack.motion(), Boar.DebugMessage.INFO);
    }

    private static void handleGlideBoost(BoarPlayer player, GlideBoostAck ack) {
        if (player.glideBoostTicks == 0 && ack.duration() == 0 || ack.duration() == Integer.MAX_VALUE) {
            player.glideBoostTicks = 1;
            return;
        }
        player.glideBoostTicks = Math.max(1, ack.duration() / 2);
    }

    private static void handleCreativeContent(BoarPlayer player, CreativeContentAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        inv.getCreativeData().clear();
        for (CreativeItemData data : ack.contents()) {
            inv.getCreativeData().put(data.getNetId(), data.getItem());
        }
    }

    private static void handleCraftingData(BoarPlayer player, CraftingDataAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        inv.getCraftingData().clear();

        for (final RecipeData data : ack.recipes()) {
            switch (data.getType()) {
                case MULTI -> {
                    final MultiRecipeData recipe = (MultiRecipeData) data;
                    inv.getCraftingData().put(recipe.getNetId(), recipe);
                }
                case SHAPED -> {
                    final ShapedRecipeData recipe = (ShapedRecipeData) data;
                    inv.getCraftingData().put(recipe.getNetId(), recipe);
                }
                case SHAPELESS -> {
                    final ShapelessRecipeData recipe = (ShapelessRecipeData) data;
                    inv.getCraftingData().put(recipe.getNetId(), recipe);
                }
                case SMITHING_TRANSFORM -> {
                    final SmithingTransformRecipeData recipe = (SmithingTransformRecipeData) data;
                    inv.getCraftingData().put(recipe.getNetId(), recipe);
                }
                case SMITHING_TRIM -> {
                    final SmithingTrimRecipeData recipe = (SmithingTrimRecipeData) data;
                    inv.getCraftingData().put(recipe.getNetId(), recipe);
                }
            }
        }

        inv.setPotionMixData(ack.potionMixes());
    }

    private static void handleContainerOpen(BoarPlayer player, ContainerOpenAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        final ContainerCache existing = inv.getContainer(ack.id());
        inv.openContainer = Objects.requireNonNullElseGet(existing, () -> new ContainerCache(inv, ack.id(), ack.type(), ack.blockPosition(), ack.uniqueEntityId()));
    }

    private static void handleUpdateTrade(BoarPlayer player, UpdateTradeAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        try {
            inv.openContainer = new TradeContainerCache(inv, ack.offers(), ack.containerId(), ack.containerType(), Vector3i.ZERO, ack.traderUniqueEntityId());
        } catch (Exception ignored) {
        }
    }

    private static void handleInventorySlot(BoarPlayer player, InventorySlotAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        if (ack.containerId() == 125) {
            final ItemCache cache;
            try {
                cache = inv.getBundleCache().get(Objects.requireNonNull(ack.storageItem().getTag()).getInt("bundle_id"));
            } catch (Exception ignored) {
                return;
            }
            if (cache == null) {
                return;
            }
            cache.getBundle().getContents()[ack.slot()] = ItemCache.build(inv, ack.item());
            return;
        }

        final ContainerCache container = inv.getContainer((byte) ack.containerId());
        if (container == null) {
            return;
        }
        if (ack.slot() < 0 || ack.slot() >= container.getContainerSize()) {
            return;
        }
        container.set(ack.slot(), ack.item());
    }

    private static void handleInventoryContent(BoarPlayer player, InventoryContentAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        if (ack.containerId() == 125) {
            final ItemCache cache;
            try {
                cache = inv.getBundleCache().get(Objects.requireNonNull(ack.storageItem().getTag()).getInt("bundle_id"));
            } catch (Exception ignored) {
                return;
            }
            if (cache == null) {
                return;
            }
            for (int i = 0; i < ack.contents().size(); i++) {
                if (i >= 64) {
                    break;
                }
                cache.getBundle().getContents()[i] = ItemCache.build(inv, ack.contents().get(i));
            }
            return;
        }

        final ContainerCache container = inv.getContainer((byte) ack.containerId());
        if (container == null) {
            return;
        }
        for (int i = 0; i < ack.contents().size(); i++) {
            container.set(i, ack.contents().get(i), false);
        }
    }

    private static void handleHotbarSlot(BoarPlayer player, HotbarSlotAck ack) {
        player.compensatedInventory.heldItemSlot = ack.slot();
    }

    private static void handleMobEffect(BoarPlayer player, MobEffectAck ack) {
        if (ack.event() == MobEffectPacket.Event.ADD || ack.event() == MobEffectPacket.Event.MODIFY) {
            player.getActiveEffects().put(ack.effect(), new StatusEffect(ack.effect(), ack.amplifier(), ack.duration() + 1));
        } else if (ack.event() == MobEffectPacket.Event.REMOVE) {
            player.getActiveEffects().remove(ack.effect());
        }
    }

    private static void handleVehicleClear(BoarPlayer player, VehicleClearAck ack) {
        player.vehicleData = null;
    }

    private static void handleVehicleSet(BoarPlayer player, VehicleSetAck ack) {
        player.vehicleData = new VehicleData();
        player.vehicleData.vehicleRuntimeId = ack.vehicleRuntimeId();
    }

    private static void handleTeleportAccept(BoarPlayer player, TeleportAcceptAck ack) {
        ack.cache().setAccepted(true);
    }
}
