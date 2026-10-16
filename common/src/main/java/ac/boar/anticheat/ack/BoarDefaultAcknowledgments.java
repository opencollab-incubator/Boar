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
import ac.boar.anticheat.player.data.BoatTakeoverTransition;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.prediction.vehicle.FollowerHistory;
import ac.boar.anticheat.prediction.vehicle.VehicleTicker;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.validator.inventory.click.ItemRequestProcessor;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.LatencyUtil;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.geyser.BlockEntityInfo;
import ac.boar.anticheat.util.geyser.BoarChunk;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.entity.EntityTypes;
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

    private static void authorizeHorseDeath(final BoarPlayer player, final EntityCache entity, final String source) {
        final VehicleData vehicle = player.vehicleData;
        if (vehicle == null || !vehicle.isHorseDeathExitMount(player, entity)) {
            return;
        }
        if (!vehicle.canEndHorseDeath(player, entity)) {
            vehicle.logHorseDeath(player, "decline source=" + source + " reason=ride-authority seen=" + vehicle.horseDeathInputSeen
                    + " predicted=" + vehicle.inputClaimsClientPrediction + " decided=" + vehicle.horseDeathDecided);
            return;
        }
        vehicle.horseDeathAuthorized = true;
        vehicle.logHorseDeath(player, "authorize source=" + source);
    }

    private static void authorizeFollowerDeath(final BoarPlayer player, final EntityCache entity, final String source) {
        final VehicleData vehicle = player.vehicleData;
        if (vehicle == null || !vehicle.isFollowerDeathExitMount(player, entity)) {
            return;
        }
        if (vehicle.followerDeathDecided) {
            vehicle.logFollowerDeath(player, "decline source=" + source + " reason=decided");
            return;
        }
        if (vehicle.followerDeathDeadline == Long.MIN_VALUE) {
            vehicle.followerDeathDeadline = player.tick + VehicleData.FOLLOWER_DEATH_EXIT_INPUTS;
            vehicle.followerDeathPosition = entity.getAcknowledgedPositionTarget();
            vehicle.freezeFollowerDeathClaim(player, entity, true);
        }
        vehicle.followerDeathAuthorized = true;
        vehicle.logFollowerDeath(player, "authorize source=" + source);
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
        registry.register(EntityDeathAck.class, (player, ack) -> {
            authorizeHorseDeath(player, ack.entity(), "event-3");
            authorizeFollowerDeath(player, ack.entity(), "event-3");
        });
        registry.register(EntityAttributesAck.class, BoarDefaultAcknowledgments::handleEntityAttributes);

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
        registry.register(StaticSeatAck.class, (player, ack) -> player.staticSeatTracker.acknowledge(player, ack));
        registry.register(SeatOffsetAck.class, (player, ack) -> player.seatOffset = ack.seatOffset());
        registry.register(FollowerMoveAck.class, BoarDefaultAcknowledgments::handleFollowerMove);
        registry.register(PassengerLinkAck.class, BoarDefaultAcknowledgments::handlePassengerLink);
        registry.register(FollowerMotionAck.class, BoarDefaultAcknowledgments::handleFollowerMotion);
        registry.register(VehicleMotionAck.class, BoarDefaultAcknowledgments::handleVehicleMotion);

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
        player.compensatedWorld.put(ack.chunkX(), ack.chunkZ(), ack.sections(), ack.knownSections());
    }

    private static void handleSubChunkLoad(BoarPlayer player, SubChunkLoadAck ack) {
        if (ack.dimension() != player.compensatedWorld.getDimension()) {
            return;
        }
        player.compensatedWorld.updateSection(ack.chunkX(), ack.chunkZ(), ack.sectionY(), ack.section(), ack.known());
    }

    private static void handleBlockUpdate(BoarPlayer player, BlockUpdateAck ack) {
        final Vector3i pos = ack.position();
        if (Boar.getConfig().debugMode() && player.position.distanceTo(new Vec3(pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F)) <= 4.0F) {
            final int oldId = player.compensatedWorld.getRawBlockAt(pos.getX(), pos.getY(), pos.getZ(), ack.layer());
            Boar.debug(player.getSession().name() + ": [placement-debug] tick=" + player.tick + " server block update pos=" + pos
                    + " layer=" + ack.layer() + " runtimeId " + oldId + " -> " + ack.runtimeId()
                    + " wasOurPlacement=" + player.blockPlacements.isUnansweredPlacement(pos), Boar.DebugMessage.INFO);
        }

        player.blockPlacements.onServerUpdateReceived(ack.position(), ack.layer());
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
        if (player.vehicleData != null) {
            player.vehicleData.invalidateMatchedClaim(player.tick + 1);
        }
        player.pendingDimensionSwitches = Math.max(0, player.pendingDimensionSwitches - 1);
        if (player.compensatedWorld.getDimension() != ack.dimension()) {
            player.currentLoadingScreen = ack.loadingScreenId();
            player.inLoadingScreen = true;
            player.loadingScreenStartMs = System.currentTimeMillis();
        }
        player.compensatedWorld.clearChunks();
        player.compensatedWorld.setDimension(ack.dimension());
        player.blockPlacements.clearPlacements();
        player.getFlagTracker().clear();
        player.getFlagTracker().flying(false);
    }

    private static void handleEntityAdd(BoarPlayer player, AddEntityAck ack) {
        // ack.entity() is never null: ServerEntityPackets.handleEntityAdd returns before it sends this ack when addToCache gives null.
        ack.entity().getHorseImmobility().acceptSpawn();
        player.compensatedWorld.promoteEntity(ack.entity());
        attachLateVehicle(player, ack.entity());
    }

    // A ride can start before Boar has its vehicle. VehiclePackets only sends the link ack for an actor it tracks, so the actor is missing
    // at that ack only when its removal was acked first. handleVehicleSet then makes a ride with no vehicle (type OTHER, no driver).
    // When a new actor with the same runtime and unique IDs arrives, fill in the vehicle, type and driver, and restart the vehicle
    // simulation like a new mount.
    // The only caller (handleEntityAdd) passes the non-null actor that promoteEntity just made visible under its own runtime ID.
    private static void attachLateVehicle(final BoarPlayer player, final EntityCache entity) {
        final VehicleData ride = player.vehicleData;
        if (ride == null || ride.mountedEntity != null || entity.getRuntimeId() != ride.vehicleRuntimeId || entity.getUniqueId() != ride.vehicleUniqueId) {
            return;
        }
        ride.mountedEntity = entity;
        ride.lastKnownEntity = entity;
        ride.type = entity.getVehicleType();
        ride.driver = entity.isControlledBy(player.uniqueEntityId);
        ride.needsVehicleRestart = true;
        ride.boatTakeover = BoatTakeoverTransition.arm(player, ride, entity);
        final String line = "ride: vehicle arrived after the mount, attached type=" + ride.type + " driver=" + ride.driver
                + " runtime=" + entity.getRuntimeId();
        player.getMovementTrace().log(line);
        Boar.debug(player.getSession().name() + ": [movement-debug] " + line, Boar.DebugMessage.INFO);
    }

    private static void handleEntityRemove(BoarPlayer player, EntityRemoveAck ack) {
        final VehicleData vehicle = player.vehicleData;
        if (ack.entity() != null && vehicle != null && vehicle.vehicleUniqueId == ack.uniqueEntityId()
                && player.compensatedWorld.getEntity(vehicle.vehicleRuntimeId) == ack.entity()) {
            player.horseJumpInput.invalidate();
            // A removal ends the ride like a link removal, so apply the dismount teleport the same way as in handleVehicleClear.
            endRide(player, vehicle, ack.lastTeleport());
        }
        player.compensatedWorld.removeEntity(ack.uniqueEntityId(), ack.entity());
    }

    // Ends the ride and applies its dismount teleport, if there is one. Without it, the position stays in the seat's coordinates.
    // Callers reset the jump input first. Each caller has its own rule for that.
    private static void endRide(final BoarPlayer player, final VehicleData vehicle, final TeleportData lastTeleport) {
        player.vehicleData = null;
        // While riding, player.velocity was the controlled vehicle's velocity. The rider has its own frozen velocity.
        player.velocity = Vec3.ZERO.clone();

        // A teleport accepted while riding only counts if it is the one the server sent right before the dismount.
        final TeleportData accepted = player.getTeleportUtil().takeAcceptedTeleport();
        final TeleportData candidate = accepted != null ? accepted : vehicle.parkedTeleport;
        if (candidate != null && candidate == lastTeleport) {
            player.getTeleportUtil().apply(candidate);
        }
    }

    // An ordinary move (not a teleport or forced move) of this boat while the player is its first passenger.
    // Also true after an earlier ack in the same list ended that ride (afterRideClearedInList, set by LatencyUtil for the boat at the list start).
    private static boolean ordinaryMoveOfPlayerBoat(final BoarPlayer player, final VehicleData vehicle, final EntityCache entity,
            final boolean ordinaryActorMove, final EntityCache boat, final boolean afterRideClearedInList) {
        return ordinaryActorMove && boat == entity && (afterRideClearedInList || VehicleData.isFirstPassengerOfBoat(player, vehicle, entity));
    }

    private static void handleEntityInterpolate(BoarPlayer player, EntityInterpolateAck ack) {
        final EntityCache entity = player.compensatedWorld.getEntity(ack.entity().getRuntimeId());
        if (entity != null && (ack.boat() == null || ack.boat() == entity)) {
            final VehicleData vehicle = player.vehicleData;
            if (ack.teleport() && vehicle != null && vehicle.mountedEntity == entity) {
                vehicle.boatTakeover = null;
            }
            final boolean playerBoatMove = ordinaryMoveOfPlayerBoat(player, vehicle, entity, ack.ordinaryActorMove(), ack.boat(), ack.afterRideClearedInList());
            boolean lerp = ack.lerp();
            Float posX = ack.posX(), posY = ack.posY(), posZ = ack.posZ();
            if (ack.fromMoveActorPacket()) {
                // Fill in axes the packet left out, using the last acknowledged position. Do this even if an old lerp target survived a snap.
                final Vec3 destination = entity.getAcknowledgedPositionTarget();
                if (posX != null) {
                    destination.x = posX;
                }
                if (posY != null) {
                    destination.y = posY;
                }
                if (posZ != null) {
                    destination.z = posZ;
                }
                posX = destination.x;
                posY = destination.y;
                posZ = destination.z;
                // Standard non-player actors have a predicted movement component, so they skip the client's snap fallback.
                final boolean playerActor = entity.getType() != null && entity.getType().is(EntityTypes.PLAYER);
                lerp = !playerActor || ack.lerp() && !(ack.teleport() && entity.getCurrent().getPos().squaredDistanceTo(destination) >= .25F);
                // Not handled yet: player differences that depend on chunks. For now, ack.lerp keeps the choice made when the packet was sent.
            }
            // The client keeps the last server packet to decode deltas (changes from the last position).
            // But it ignores ordinary moves while it controls the boat itself.
            entity.interpolate(posX, posY, posZ, lerp, !playerBoatMove);
            if (vehicle != null && vehicle.followerDeathAuthorized && entity == vehicle.mountedEntity && vehicle.followerDeathPosition != null
                    && vehicle.followerDeathPosition.distanceTo(entity.getAcknowledgedPositionTarget()) != 0) {
                // Keep this set, even if another position packet moves the actor back to the death position in the same batch.
                vehicle.followerDeathPositionChanged = true;
            }
            // These fields keep the server rotation the client acknowledged. The rotation of the driven actor lives in VehicleData.
            entity.applyRotation(ack.pitch(), ack.yaw(), ack.headYaw());
            if (ack.fromMoveActorPacket() && ack.horse() == entity && vehicle != null && vehicle.matchesForcedHorseEntity(entity)) {
                if (ack.forceCompletion()) {
                    vehicle.horseForcedCompletionSeen = true;
                    vehicle.horseForcedMove = null;
                    player.getMovementTrace().log("horse forced move: declined force-completion for this ride");
                } else if (ack.forceMoveLocal() && !ack.teleport()) {
                    vehicle.horseForcedMove = null;
                    player.getMovementTrace().log("horse forced move: declined non-teleport local move");
                } else if (ack.teleport()) {
                    if (vehicle.horseForcedCompletionSeen || vehicle.ticks <= 0 || !VehicleTicker.clientSimulates(player, vehicle)
                            || !entity.hasFirstPlayerPassenger(player.uniqueEntityId)) {
                        vehicle.horseForcedMove = null;
                        player.getMovementTrace().log("horse forced move: declined control, mount phase or completion");
                    } else {
                        vehicle.horseForcedMove = new VehicleData.HorseForcedMove(entity.getAcknowledgedPositionTarget().clone(), entity.getYaw(),
                                ack.onGround());
                    }
                }
            }
            if (!playerBoatMove && ack.boat() == entity && lerp && entity.isLocallySimulatedBoat()) {
                // A direct replacement sets position and rotation together.
                // This old boat path does not copy the client's force-completion queued-target branch.
                entity.setPendingServerAngles(new EntityCache.ServerAngles(entity.getPitch(), entity.getYaw(), entity.getHeadYaw()));
            }
            // The server's ground flag for the ack's own horse, still cached under its runtime ID. A teleport or force move sets it directly.
            if (ack.fromMoveActorPacket() && ack.horse() == entity) {
                entity.getHorseGround().serverMove(ack.onGround(), ack.teleport() || ack.forceMoveLocal());
            }
        }
    }

    private static void handleEntityMetadata(BoarPlayer player, EntityMetadataAck ack) {
        final EntityCache cache = player.compensatedWorld.getEntity(ack.runtimeEntityId());
        if (cache != null) {
            final var flags = ack.metadata().getFlags();
            final VehicleData vehicle = player.vehicleData;
            // After a local landing, only an incoming flag word that contains Standing can set it again.
            if (vehicle != null && vehicle.type == VehicleType.HORSE && vehicle.matchesMountedEntity(cache) && flags != null
                    && flags.containsKey(EntityFlag.STANDING)) {
                vehicle.horseStanding = flags.get(EntityFlag.STANDING);
            }
            cache.setMetadata(ack.metadata());
            cache.getHorseGround().controlTransition();
            updateDriver(player, cache);
        }
    }

    private static void handleEntityAttributes(BoarPlayer player, EntityAttributesAck ack) {
        final EntityCache cache = player.compensatedWorld.getEntity(ack.entity().getRuntimeId());
        // ack.entity() is never null: ServerDataPackets only builds this ack for a tracked entity. So this also rejects a null cache.
        if (cache != ack.entity()) {
            return;
        }
        cache.setAttributes(ack.attributes());
        for (final AttributeData attribute : ack.attributes()) {
            if (!"minecraft:health".equals(attribute.getName())) {
                continue;
            }
            final VehicleData vehicle = player.vehicleData;
            if (attribute.getValue() <= 0) {
                authorizeHorseDeath(player, cache, "health");
                authorizeFollowerDeath(player, cache, "health");
                continue;
            }
            if (attribute.getValue() > 0 && vehicle != null && vehicle.isHorseDeathExitMount(player, cache) && vehicle.horseDeathAuthorized) {
                vehicle.horseDeathAuthorized = false;
                vehicle.logHorseDeath(player, "decline reason=revived decided=" + vehicle.horseDeathDecided);
            }
            // Match by actor only, like the authorization we cancel. The saddle state at this ack must not keep an old authorization alive.
            if (attribute.getValue() > 0 && vehicle != null && vehicle.followerDeathAuthorized && cache == vehicle.mountedEntity) {
                vehicle.followerDeathAuthorized = false;
                vehicle.followerDeathDeadline = Long.MIN_VALUE;
                vehicle.followerDeathPosition = null;
                vehicle.followerDeathPositionChanged = false;
                vehicle.invalidateMatchedClaim(player.tick);
                vehicle.logFollowerDeath(player, "decline reason=revived");
            }
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
            if (ack.flags().contains(EntityFlag.USING_ITEM)) {
                player.getItemUseTracker().onServerUsingAcked(ack.sentTick());
            }
            player.getFlagTracker().set(player, ack.flags(), ack.sentTick());
        }
        if (ack.swimming() != null) {
            player.getFlagTracker().set(EntityFlag.SWIMMING, ack.swimming());
        }
        if (Boar.getConfig().debugMode()) {
            player.getMovementTrace().log("metadata apply after: ack=" + System.identityHashCode(ack) + " playerTick=" + player.tick + " current=" + player.getFlagTracker().cloneFlags());
        }

        if (ack.width() != null) {
            player.setDimensions(EntityDimensions.fixed(ack.width(), player.dimensions.height()).withEyeHeight(player.dimensions.eyeHeight()));
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
            player.setDimensions(EntityDimensions.fixed(player.dimensions.width(), ack.height()).withEyeHeight(eyeHeight));
            player.setBoundingBox(player.position);
        }

        if (ack.scale() != null) {
            player.setDimensions(player.dimensions.hardScaled(ack.scale()));
        }

        if (ack.bedPosition() != null) {
            player.bedPosition = ack.bedPosition().equals(Vector3i.ZERO) ? null : ack.bedPosition();
        }

        if (ack.flags() != null && !ack.flags().contains(EntityFlag.SLEEPING)) {
            player.bedPosition = null;
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
            if (data.getName().equals("minecraft:player.hunger")) {
                player.hunger = data.getValue();
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

            if (Boar.getConfig().debugMode() && data.getName().equals("minecraft:movement")) {
                // The client drops its sprint boost here too and takes the packet's value (BaseAttributeMap::updateAttribute).
                Boar.debug(player.getSession().name() + ": [speed-debug] tick=" + player.tick + " speed ack applied value=" + data.getValue()
                        + " base=" + data.getDefaultValue() + " boarSpeed=" + player.getSpeed()
                        + " boarSprinting=" + player.getFlagTracker().has(EntityFlag.SPRINTING), Boar.DebugMessage.INFO);
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
        player.compensatedInventory.switchHeldSlot(ack.slot());
    }

    private static void handleMobEffect(BoarPlayer player, MobEffectAck ack) {
        if (ack.horse() != null) {
            // The ACK holds the exact horse that was sent. A new entity may have reused its runtime ID, so compare the cached actor by identity.
            // The effect is always jump boost: PlayerEffectPackets only makes a horse effect ack for it.
            if (player.compensatedWorld.getEntity(ack.horse().getRuntimeId()) == ack.horse()) {
                if (ack.event() == MobEffectPacket.Event.ADD || ack.event() == MobEffectPacket.Event.MODIFY) {
                    ack.horse().getHorseJumpEffect().update(ack.amplifier(), ack.duration(),
                            ack.sentInputEpoch() == player.acceptedInputEpoch ? ack.sentInput() : -1, player.acceptedInputCount);
                } else if (ack.event() == MobEffectPacket.Event.REMOVE) {
                    ack.horse().getHorseJumpEffect().remove();
                }
            }
            return;
        }
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

    private static void handlePassengerLink(BoarPlayer player, PassengerLinkAck ack) {
        final EntityCache cache = player.compensatedWorld.getEntity(ack.vehicleRuntimeId());
        if (cache == null) {
            return;
        }

        if (ack.remove()) {
            cache.removePassenger(ack.riderUniqueId());
        } else {
            final boolean newRider = cache.addPassenger(ack.riderUniqueId(), ack.riderIsPlayer());
            if (newRider && cache.getVehicleType() == VehicleType.BOAT && Float.isFinite(ack.angularVelocity())) {
                cache.setBoatAngularVelocity(ack.angularVelocity());
                // The client also gives the boat it drives this angular velocity when another rider gets on.
                // In-game testing showed the driver's next turn step was this tick's torque on a zero angular velocity, not the old turn speed.
                // Only tested for a rider getting on.
                final VehicleData ride = player.vehicleData;
                if (ride != null && ride.vehicleRuntimeId == cache.getRuntimeId() && ride.driver && ride.type == VehicleType.BOAT
                        && ack.riderUniqueId() != player.uniqueEntityId) {
                    ride.yawVelocity = ack.angularVelocity();
                }
            }
        }

        cache.getHorseGround().controlTransition();
        // A back seat rider moves to the front when the rider in front leaves, without a new link.
        updateDriver(player, cache);
    }

    private static void updateDriver(BoarPlayer player, EntityCache cache) {
        final VehicleData vehicle = player.vehicleData;
        if (vehicle == null || vehicle.vehicleRuntimeId != cache.getRuntimeId()) {
            return;
        }

        final boolean driver = cache.isControlledBy(player.uniqueEntityId);
        if (driver != vehicle.driver) {
            // If the player left on their own, the client already reset the rider state, and the link removal only confirms it.
            // Only a seat change we did not expect clears the saved jump input.
            if (!vehicle.pendingLeave || driver) {
                player.horseJumpInput.invalidate();
            }
            vehicle.invalidateHorseJump();
            vehicle.driver = driver;
            vehicle.boatTakeover = BoatTakeoverTransition.arm(player, vehicle, cache);
            vehicle.invalidateHorseStand();
            final boolean boatDriver = driver && vehicle.type == VehicleType.BOAT;
            vehicle.awaitingBoatFormatInput = boatDriver;
            if (boatDriver) {
                // Use ACK order, not input ticks, to tell the old passenger teleport from one accepted after the player became driver.
                // An input may not have parked it yet.
                final TeleportData accepted = player.getTeleportUtil().takeAcceptedTeleport();
                if (accepted != null) {
                    vehicle.parkedTeleport = accepted;
                }
                vehicle.preDriverSwitchTeleport = vehicle.parkedTeleport;
            }
            vehicle.armDriverSwitchTick(vehicle.awaitingBoatFormatInput);
            vehicle.boatActorState = null;
            vehicle.riderCoordinates = false;
            // The client switches between steering the vehicle and following it: start the other way like a mount.
            vehicle.ticks = 0;
            vehicle.stillTicks = 0;
            // The tick counter starts again, so state that is kept across ticks of the old way must go too.
            vehicle.horseJumping = false;
            vehicle.serverMotionActive = false;
        }
    }

    // A server move of a vehicle the server drives. It feeds FollowerHistory (the best guess) and FollowerEnvelope (the set limit).
    // The history stamp is estimatedHandleNs, our guess of when the client handled the packet: max(list sent, last input),
    // moved later toward the reply in replies-alone mode (see LatencyUtil).
    // acknowledgmentReplyNs (when the latency reply reached us) only goes to FollowerEnvelope, as the upper bound of the stamp.
    private static void handleFollowerMove(BoarPlayer player, FollowerMoveAck ack) {
        final EntityCache cache = player.compensatedWorld.getEntity(ack.entity().getRuntimeId());
        if (cache == null || ack.boat() != null && ack.boat() != cache) {
            return;
        }

        final VehicleData vehicle = player.vehicleData;
        if (ordinaryMoveOfPlayerBoat(player, vehicle, cache, ack.ordinaryActorMove(), ack.boat(), ack.afterRideClearedInList())) {
            return;
        }

        cache.setActorOnGround(ack.onGround());

        final LatencyUtil latency = player.getLatencyUtil();
        final long now = latency.estimatedHandleNs();
        if (cache.getFollowerHistory() == null) {
            cache.setFollowerHistory(new FollowerHistory(now, ack.x(), ack.y(), ack.z(), ack.pitch(), ack.yaw(), player.followerArrivalClock));
        }
        if (ack.addedActor()) {
            cache.setActorPitch(ack.pitch());
            cache.setActorYaw(ack.yaw());
            cache.setActorHeadYaw(ack.headYaw());
        }
        // The client's "predicted" bit stays set until its first tick without control. Moves in that gap use the ordinary lerp.
        if (cache.getVehicleType() == VehicleType.BOAT && cache.getFollowerHistory().isLocallyPredicted()) {
            cache.setPendingServerAngles(new EntityCache.ServerAngles(ack.pitch(), ack.yaw(), ack.headYaw()));
            return;
        }
        cache.getFollowerHistory().addMove(now, latency.estimatedEarlyHandleNs(), latency.acknowledgmentGroup(), ack.sentNs(), latency.acknowledgmentReplyNs(),
                ack.x(), ack.y(), ack.z(), ack.pitch(), ack.yaw(), ack.onGround(), ack.addedActor());
    }

    private static void handleFollowerMotion(BoarPlayer player, FollowerMotionAck ack) {
        final EntityCache cache = player.compensatedWorld.getEntity(ack.entity().getRuntimeId());
        // ack.entity() is never null (ServerEntityPackets only builds this ack for a non-null entity), so this also rejects a null cache.
        if (cache != ack.entity() || cache.getFollowerHistory() == null || cache.getFollowerHistory().isLocallyPredicted()) {
            return;
        }

        final LatencyUtil latency = player.getLatencyUtil();
        final long now = latency.estimatedHandleNs();
        cache.getFollowerHistory().addMotion(now, latency.estimatedEarlyHandleNs(), latency.acknowledgmentGroup(), ack.sentNs(), latency.acknowledgmentReplyNs(),
                ack.onGround());
    }

    private static void handleVehicleMotion(BoarPlayer player, VehicleMotionAck ack) {
        if (player.compensatedWorld.getEntity(ack.entity().getRuntimeId()) == ack.entity()) {
            ack.entity().setAcknowledgedMotion(ack.motion().clone());
            ack.entity().setAcknowledgedMotionTick(player.tick);
            if (ack.entity().getVehicleType() == VehicleType.BOAT && MathUtil.finite(ack.motion())) {
                ack.entity().setBoatVelocity(ack.motion().clone());
            }
        }
    }

    private static void handleVehicleClear(BoarPlayer player, VehicleClearAck ack) {
        final VehicleData vehicle = player.vehicleData;
        // We already ended this ride when the client left with the dismount teleport.
        if (vehicle == null) {
            return;
        }
        vehicle.invalidateJumpInputUnlessLeaving(player);
        endRide(player, vehicle, ack.lastTeleport());
    }

    private static void handleVehicleSet(BoarPlayer player, VehicleSetAck ack) {
        final EntityCache cache = player.compensatedWorld.getEntity(ack.vehicleRuntimeId());
        // This acknowledged link replaces the older static seat (staticSeat). Clear it even if the vehicle is not in the cache.
        // Otherwise the static seat stays active while this link starts a ride for a player who was not seated.
        player.staticSeat = null;
        final VehicleData current = player.vehicleData;
        // A repeat link to the same actor does not restart the ride. A change in passenger order updates control elsewhere.
        if (current != null && current.vehicleRuntimeId == ack.vehicleRuntimeId() && current.vehicleUniqueId == ack.vehicleUniqueId() && cache != null
                && current.mountedEntity == cache) {
            // The server sent this link after it got our leave request, so it refused the leave.
            // A link sent before the request says nothing about the leave.
            if (current.pendingLeave && ack.sentNs() - current.pendingLeaveNs > 0) {
                current.pendingLeave = false;
                cache.getHorseGround().remount();
                Boar.debug(player.getSession().name() + ": [movement-debug] ride: leave refused, the server linked the same vehicle again runtime="
                        + ack.vehicleRuntimeId(), Boar.DebugMessage.INFO);
            }
            return;
        }

        if (current != null) {
            player.horseJumpInput.invalidate();
        }
        if (cache != null) {
            // After a local exit (AuthInputPackets.localExitStart) a refusal link starts a new ride here, like the client's own new mount.
            cache.getHorseGround().remount();
        }
        final VehicleData data = new VehicleData();
        data.vehicleRuntimeId = ack.vehicleRuntimeId();
        data.vehicleUniqueId = ack.vehicleUniqueId();
        data.mountedEntity = cache;
        data.mountTick = ack.sentTick();

        if (cache != null) {
            data.type = cache.getVehicleType();
            data.driver = cache.isControlledBy(player.uniqueEntityId);
            data.lastKnownEntity = cache;
        } else {
            // The vehicle's removal was acked before this link. Filled in if an actor with the same IDs arrives (attachLateVehicle).
            Boar.debug(player.getSession().name() + ": [movement-debug] ride: mounted after the vehicle's removal was acked, runtime="
                    + ack.vehicleRuntimeId(), Boar.DebugMessage.INFO);
        }

        player.vehicleData = data;
        data.boatTakeover = BoatTakeoverTransition.arm(player, data, cache);
    }

    private static void handleTeleportAccept(BoarPlayer player, TeleportAcceptAck ack) {
        player.getTeleportUtil().accept(ack.data(), ack.generation());
    }

    private static void handleRespawnState(BoarPlayer player, RespawnStateAck ack) {
        if (player.vehicleData != null) {
            player.vehicleData.invalidateMatchedClaim(player.tick + 1);
            player.vehicleData.boatTakeover = null;
        }
        // SERVER_SEARCHING is sent on death. SERVER_READY is sent once the client asked to respawn.
        player.dead = ack.state() == RespawnPacket.State.SERVER_SEARCHING;
    }

    private static void handleMovementCorrection(BoarPlayer player, MovementCorrectionAck ack) {
        player.getTeleportUtil().removePendingCorrection();
        player.getTeleportUtil().markCorrected();
    }
}
