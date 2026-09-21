package ac.boar.anticheat.ack;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.*;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.compensated.cache.container.impl.TradeContainerCache;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.data.inventory.SlotSnapshot;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.data.vanilla.StatusEffect;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.validator.inventory.click.ItemRequestProcessor;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.geyser.BlockEntityInfo;
import ac.boar.anticheat.util.geyser.BoarChunk;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.MultiRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;

import java.util.List;
import java.util.Objects;

public final class BoarDefaultAcknowledgments {

    private BoarDefaultAcknowledgments() {
    }

    public static void registerAll(BoarAcknowledgmentRegistry registry) {
        registry.register(ChunkRadiusUpdateAck.class, BoarDefaultAcknowledgments::handleChunkRadiusUpdate);
        registry.register(ChunkLoadAck.class, BoarDefaultAcknowledgments::handleChunkLoad);
        registry.register(SubChunkLoadAck.class, BoarDefaultAcknowledgments::handleSubChunkLoad);
        registry.register(BlockUpdateAck.class, BoarDefaultAcknowledgments::handleBlockUpdate);
        registry.register(BlockEntityUpdateAck.class, BoarDefaultAcknowledgments::handleBlockEntityUpdate);

        registry.register(DimensionSwitchAck.class, BoarDefaultAcknowledgments::handleDimensionSwitch);

        registry.register(AddEntityAck.class, BoarDefaultAcknowledgments::handleEntityAdd);
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
        registry.register(ItemStackResponseAck.class, BoarDefaultAcknowledgments::handleItemStackResponse);
        registry.register(HotbarSlotAck.class, BoarDefaultAcknowledgments::handleHotbarSlot);

        registry.register(MobEffectAck.class, BoarDefaultAcknowledgments::handleMobEffect);

        registry.register(VehicleClearAck.class, BoarDefaultAcknowledgments::handleVehicleClear);
        registry.register(VehicleSetAck.class, BoarDefaultAcknowledgments::handleVehicleSet);

        registry.register(TeleportAcceptAck.class, BoarDefaultAcknowledgments::handleTeleportAccept);
        registry.register(RespawnStateAck.class, BoarDefaultAcknowledgments::handleRespawnState);
        registry.register(MovementCorrectionAck.class, BoarDefaultAcknowledgments::handleMovementCorrection);
    }

    private static void handleChunkRadiusUpdate(BoarPlayer player, ChunkRadiusUpdateAck ack) {
        player.compensatedWorld.setViewDistance(ack.radius());
    }

    private static void handleChunkLoad(BoarPlayer player, ChunkLoadAck ack) {
        if (ack.dimension() != player.compensatedWorld.getDimension()) {
            return;
        }
        player.compensatedWorld.put(ack.chunkX(), ack.chunkZ(), ack.sections());
    }

    private static void handleSubChunkLoad(BoarPlayer player, SubChunkLoadAck ack) {
        if (ack.dimension() != player.compensatedWorld.getDimension()) {
            return;
        }
        player.compensatedWorld.updateSection(ack.chunkX(), ack.chunkZ(), ack.sectionY(), ack.section());
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
        player.pendingDimensionSwitches = Math.max(0, player.pendingDimensionSwitches - 1);
        if (player.compensatedWorld.getDimension() != ack.dimension()) {
            player.currentLoadingScreen = ack.loadingScreenId();
            player.inLoadingScreen = true;
        }
        player.compensatedWorld.clearChunks();
        player.compensatedWorld.setDimension(ack.dimension());
        player.getFlagTracker().clear();
        player.getFlagTracker().flying(false);
    }

    private static void handleEntityAdd(BoarPlayer player, AddEntityAck ack) {
        player.compensatedWorld.promoteEntity(ack.runtimeEntityId());
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
            entity.interpolate(ack.posX(), ack.posY(), ack.posZ(), ack.lerp());
            entity.applyRotation(ack.pitch(), ack.yaw(), ack.headYaw());
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
        if (Boar.getConfig().debugMode()) {
            player.getMovementTrace().log("metadata apply before: ack=" + System.identityHashCode(ack)
                    + " playerTick=" + player.tick + " sentTick=" + ack.sentTick()
                    + " swimming=" + ack.swimming() + " flags=" + ack.flags()
                    + " current=" + player.getFlagTracker().cloneFlags());
        }
        if (ack.flags() != null) {
            player.getFlagTracker().set(player, ack.flags(), ack.sentTick());
        }
        if (ack.swimming() != null) {
            player.getFlagTracker().set(EntityFlag.SWIMMING, ack.swimming());
        }
        if (Boar.getConfig().debugMode()) {
            player.getMovementTrace().log("metadata apply after: ack=" + System.identityHashCode(ack) + " playerTick=" + player.tick + " current=" + player.getFlagTracker().cloneFlags());
        }

        if (ack.width() != null) {
            player.dimensions = EntityDimensions.fixed(ack.width(), player.dimensions.height()).withEyeHeight(player.dimensions.eyeHeight());
            player.setBoundingBox(player.position);
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
            player.setBoundingBox(player.position);
        }

        if (ack.scale() != null) {
            player.dimensions = player.dimensions.hardScaled(ack.scale());
        }

        if (ack.bedPosition() != null) {
            player.bedPosition = ack.bedPosition().equals(Vector3i.ZERO) ? null : ack.bedPosition();
        }
    }

    private static void handleUpdateAttributes(BoarPlayer player, UpdateAttributesAck ack) {
        if (player.vehicleData != null) {
            return;
        }

        for (final AttributeData data : ack.attributes()) {
            if (data.getName().equals("minecraft:movement")) {
                player.clientNeedsMovementSpeedAttributeUpdate = false;
            }

            final AttributeInstance attribute = player.attributes.get(data.getName());
            if (attribute == null) {
                continue;
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
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(player.getSession().name() + ": failed to build trade container cache", e);
        }
    }

    private static void handleInventorySlot(BoarPlayer player, InventorySlotAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        if (ack.containerId() == 125) {
            final NbtMap tag = ack.storageItem() == null ? null : ack.storageItem().getTag();
            if (tag == null || !tag.containsKey("bundle_id")) {
                return;
            }
            final ItemCache cache = inv.getBundleCache().get(tag.getInt("bundle_id"));
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
            final NbtMap tag = ack.storageItem() == null ? null : ack.storageItem().getTag();
            if (tag == null || !tag.containsKey("bundle_id")) {
                return;
            }
            final ItemCache cache = inv.getBundleCache().get(tag.getInt("bundle_id"));
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

        // Make sure we have enough slots for what the server is sending
        container.ensureSlots(ack.contents().size());
        final int limit = Math.min(ack.contents().size(), container.getContents().length);
        for (int i = 0; i < limit; i++) {
            container.set(i, ack.contents().get(i), false);
        }
    }

    private static void handleItemStackResponse(BoarPlayer player, ItemStackResponseAck ack) {
        final CompensatedInventory inv = player.compensatedInventory;
        for (final ItemStackResponse response : ack.responses()) {
            final List<SlotSnapshot> snapshots = inv.pendingRequests.remove(response.getRequestId());
            if (response.getResult() == ItemStackResponseStatus.OK) {
                inv.rejectionStreak = 0;
                applyResponseSlots(inv, response);
                continue;
            }
            if (snapshots == null) {
                continue;
            }

            for (int i = snapshots.size() - 1; i >= 0; i--) {
                final SlotSnapshot snapshot = snapshots.get(i);
                if (snapshot.container().holdsSlot(snapshot.slot())) {
                    snapshot.container().set(snapshot.slot(), snapshot.oldItem());
                }
            }

            // Pending requests that touched these slots saved items this rollback just removed and restoring those later would
            // create items out of thin air
            for (final List<SlotSnapshot> later : inv.pendingRequests.values()) {
                later.removeIf(other -> {
                    for (final SlotSnapshot own : snapshots) {
                        if (own.container() == other.container() && own.slot() == other.slot()) {
                            return true;
                        }
                    }
                    return false;
                });
            }

            inv.rejectionStreak++;
            final Boar.DebugMessage level = inv.rejectionStreak >= 5 ? Boar.DebugMessage.WARNING : Boar.DebugMessage.INFO;
            Boar.debug(player.getSession().name() + ": server rejected item stack request " + response.getRequestId()
                    + " (" + response.getResult() + "), rolled back " + snapshots.size() + " slot(s)"
                    + ", streak=" + inv.rejectionStreak, level);
        }
    }

    private static void applyResponseSlots(CompensatedInventory inv, ItemStackResponse response) {
        for (final ItemStackResponseContainer containerInfo : response.getContainers()) {
            final ContainerSlotType type = containerInfo.getContainerName() != null ?
                    containerInfo.getContainerName().getContainer() :
                    containerInfo.getContainer();
            final ContainerCache cache = ItemRequestProcessor.findContainer(inv, type);
            if (cache == null) {
                continue;
            }
            for (final ItemStackResponseSlot slotInfo : containerInfo.getItems()) {
                final int slot = slotInfo.getSlot();
                if (!cache.holdsSlot(slot)) {
                    continue;
                }
                if (slotInfo.getCount() <= 0) {
                    cache.set(slot, ItemData.AIR);
                    continue;
                }

                final ItemCache current = cache.get(slot);
                if (current.isEmpty()) {
                    // The server has an item here but we predicted air?
                    continue;
                }
                current.count(slotInfo.getCount());
                current.netId(slotInfo.getStackNetworkId());
            }
        }
    }

    private static void handleHotbarSlot(BoarPlayer player, HotbarSlotAck ack) {
        player.compensatedInventory.heldItemSlot = ack.slot();
    }

    private static void handleMobEffect(BoarPlayer player, MobEffectAck ack) {
        if (ack.event() == MobEffectPacket.Event.ADD || ack.event() == MobEffectPacket.Event.MODIFY) {
            if (ack.effect().equals(Effect.SPEED) || ack.effect().equals(Effect.SLOWNESS)) {
                player.clientNeedsMovementSpeedAttributeUpdate = true;
            }
            final int raw = ack.duration();
            final int duration = raw < 0 || raw == Integer.MAX_VALUE ? Integer.MAX_VALUE : raw + 1;
            player.getActiveEffects().put(ack.effect(), new StatusEffect(ack.effect(), ack.amplifier(), duration));
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
        ack.data().accept();
    }

    private static void handleRespawnState(BoarPlayer player, RespawnStateAck ack) {
        // SERVER_SEARCHING is sent on death. SERVER_READY is sent once the client asked to respawn.
        player.dead = ack.state() == RespawnPacket.State.SERVER_SEARCHING;
    }

    private static void handleMovementCorrection(BoarPlayer player, MovementCorrectionAck ack) {
        player.getTeleportUtil().removePendingCorrection();
        player.getTeleportUtil().markCorrected();
    }
}
