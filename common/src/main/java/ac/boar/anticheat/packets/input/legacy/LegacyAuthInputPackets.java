package ac.boar.anticheat.packets.input.legacy;

import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.impl.OffsetHandlerCheck;
import ac.boar.anticheat.collision.Collider;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.data.inventory.BoarItemStack;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.UncertainRunner;
import ac.boar.anticheat.util.InputUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.item.Items;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.Iterator;
import java.util.Map;

public class LegacyAuthInputPackets {
    public static void updateUnvalidatedPosition(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        player.prevUnvalidatedPosition = player.unvalidatedPosition.clone();
        player.unvalidatedNativeOriginY = packet.getPosition().getY();
        player.unvalidatedPosition = new Vec3(packet.getPosition().down(player.getYOffset()));
        player.unvalidatedTickEnd = new Vec3(packet.getDelta());
    }

    public static void doPostPrediction(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        player.postTick();

        if (player.getTeleportUtil().isTeleporting() || player.insideUnloadedChunk) {
            correctInputData(player, packet);
            return;
        }

        player.getTeleportUtil().updateLastKnownValid(player.position.up(player.getYOffset()));

        final UncertainRunner uncertainRunner = new UncertainRunner(player);

        // Properly calculated offset by comparing position instead of poorly calculated velocity that get calculated using (pos - prevPos) to account for floating point errors.
        float offset = player.position.distanceTo(player.unvalidatedPosition);
        final float rawOffset = offset;
        float extraOffset = uncertainRunner.extraOffset(offset);
        offset -= extraOffset;
        final float extraOffsetNonTickEnd = uncertainRunner.extraDripstoneOffsetNonTickEnd(offset);
        offset -= extraOffsetNonTickEnd;
        uncertainRunner.uncertainPushTowardsTheClosetSpace();

        player.getMovementTrace().log("offset: raw=" + rawOffset + " extra=" + extraOffset
                + " extraNonTickEnd=" + extraOffsetNonTickEnd + " final=" + offset
                + " predictedPos=" + player.position + " actualPos=" + player.unvalidatedPosition);

        for (Map.Entry<Class<?>, Check> entry : player.getCheckHolder().entrySet()) {
            Check v = entry.getValue();
            if (v instanceof OffsetHandlerCheck check) {
                check.onPredictionComplete(offset);
            }
        }

        if (player.disableMitigations()) {
            player.velocity = player.unvalidatedTickEnd.clone();
            player.lastTickFinalVelocity = player.unvalidatedTickEnd.clone();
            player.setPos(player.unvalidatedPosition.clone(), false);
            player.nativeOriginY = player.unvalidatedNativeOriginY;
        } else {
            // Keep the predicted movement during a pending correction or its cooldown tick.
            final boolean hasPendingCorrection = player.getTeleportUtil().hasPendingCorrection();
            final boolean inCorrectionCooldown = player.getTeleportUtil().isCorrectionCooldown();
            final boolean canAcceptClient = !hasPendingCorrection && !inCorrectionCooldown;

            // Have to do this due to loss precision, especially elytra!
            if (canAcceptClient && player.velocity.distanceTo(player.unvalidatedTickEnd) - extraOffset < player.getPosAcceptanceThreshold()) {
                player.getMovementTrace().log("post: accepted client velocity " + player.unvalidatedTickEnd);
                player.velocity = player.unvalidatedTickEnd.clone();
            }

            if (canAcceptClient && offset < player.getPosAcceptanceThreshold()) {
                player.getMovementTrace().log("post: accepted client position " + player.unvalidatedPosition);
                player.setPos(player.unvalidatedPosition.clone(), false);
                player.nativeOriginY = player.unvalidatedNativeOriginY;
            }

            if (!canAcceptClient) {
                player.getMovementTrace().log("post: kept prediction (pendingCorrection=" + hasPendingCorrection
                        + " correctionCooldown=" + inCorrectionCooldown + ")");
            }

            if (!hasPendingCorrection && inCorrectionCooldown) {
                player.getTeleportUtil().setCorrectionCooldown(false);
            }
        }

        correctInputData(player, packet);
    }

    public static void correctInputData(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        if (player.isMovementExempted() || player.disableMitigations()) {
            return;
        }

        // https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/translator/protocol/bedrock/entity/player/input/BedrockMovePlayer.java#L90
        // Geyser check for our vertical collision for calculation for ground, do this to prevent possible no-fall bypass.
        packet.getInputData().remove(PlayerAuthInputData.HORIZONTAL_COLLISION);
        packet.getInputData().remove(PlayerAuthInputData.VERTICAL_COLLISION);

        if (player.horizontalCollision) {
            packet.getInputData().add(PlayerAuthInputData.HORIZONTAL_COLLISION);
        }

        if (player.verticalCollision) {
            packet.getInputData().add(PlayerAuthInputData.VERTICAL_COLLISION);
        }

        // Prevent player from spoofing this to trick Geyser into sending the wrong ground status.
        packet.setDelta(player.velocity.toVector3f());
    }

    public static void processAuthInput(final BoarPlayer player, final PlayerAuthInputPacket packet, boolean processInputData) {
        player.setInputData(packet.getInputData());
        player.clientMotion = packet.getMotion();

        InputUtil.processInput(player, packet);

        player.prevYaw = player.yaw;
        player.prevPitch = player.pitch;
        player.yaw = packet.getRotation().getY();
        player.pitch = packet.getRotation().getX();

        player.rotation = packet.getRotation();

        player.prevInteractRotUnchanged = player.prevInteractRotation.equals(player.interactRotation);
        player.prevInteractRotation = player.interactRotation.clone();
        player.interactRotation = packet.getInteractRotation().clone();

        player.inputMode = packet.getInputMode();

        if (processInputData) {
            processInputData(player);
            updateVanillaOffset(player);

            // Player isn't moving forward but is sprinting and their flag sync, this shouldn't happen unless the player is swimming
            // The client keeps the swim and the sprint flag while the input vector's length is at least 0.7071 long, in any direction
            // as-per SwimTriggerSystem::doTick
            if (player.input.z <= 0 && player.getFlagTracker().has(EntityFlag.SPRINTING) && !player.getFlagTracker().has(EntityFlag.SWIMMING) && player.desyncedFlag.get() == -1) {
                player.getFlagTracker().set(EntityFlag.SPRINTING, false);
                if (!player.disableMitigations()) { // tell the server that the player "wants" to stop sprinting.
                    packet.getInputData().add(PlayerAuthInputData.STOP_SPRINTING);
                }
            }
        }
    }

    private static boolean flagAtTickStart(BoarPlayer player, EntityFlag flag, PlayerAuthInputData start, PlayerAuthInputData stop) {
        if (player.getInputData().contains(stop)) {
            return true;
        }
        if (player.getInputData().contains(start)) {
            return false;
        }
        return player.getFlagTracker().has(flag);
    }

    private static void updateVanillaOffset(final BoarPlayer player) {
        // VanillaOffsetSystemUtil::_clientTick and UnderWaterSensingSystem::doUnderWaterSensing.
        player.headSampleOffsetY = player.previousVanillaOffsetY;
        player.previousVanillaOffsetY = player.vanillaOffsetY;

        // UpdateHorizontalPoseSystem::update
        final boolean horizontal = player.getFlagTracker().has(EntityFlag.GLIDING)
                || player.getFlagTracker().has(EntityFlag.DAMAGE_NEARBY_MOBS)
                || player.getFlagTracker().has(EntityFlag.SWIMMING)
                || player.getFlagTracker().has(EntityFlag.CRAWLING);
        float targetEyeHeight = 1.62001F;
        if (horizontal) {
            targetEyeHeight = 0.4F;
        } else if (player.getFlagTracker().has(EntityFlag.SNEAKING)) {
            // VanillaOffsetSystem::tick uses the world base game version from StartGame.
            targetEyeHeight = 1.62001F - player.sneakingEyeHeightReduction;
        }
        final float targetOffsetY = 1.62001F - targetEyeHeight; // VanillaOffsetSystemUtil::_clientTick - there's supposed to be an offset here but all paths seem to produce zero
        player.vanillaOffsetY += (targetOffsetY - player.vanillaOffsetY) * 0.5F;
    }

    public static void processInputData(final BoarPlayer player) {
        if (!player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            player.sinceTridentUse = 0;
        }

        // ViewT<StrictEntityContext, Include<InterpolateMovementNeededComponent>, SwimAmountComponent, ActorDataFlagComponent const>::each<void (*)(StrictEntityContext const&, SwimAmountComponent&, ActorDataFlagComponent const&)>
        // and registerActorMovementTickSystems
        final boolean swimmingAtTickStart = flagAtTickStart(player, EntityFlag.SWIMMING, PlayerAuthInputData.START_SWIMMING, PlayerAuthInputData.STOP_SWIMMING);
        final boolean crawlingAtTickStart = flagAtTickStart(player, EntityFlag.CRAWLING, PlayerAuthInputData.START_CRAWLING, PlayerAuthInputData.STOP_CRAWLING);
        player.swimAmount = swimmingAtTickStart || crawlingAtTickStart ? Math.min(player.swimAmount + 0.1F, 1.0F) : Math.max(player.swimAmount - 0.1F, 0.0F);

        if (player.getFlagTracker().has(EntityFlag.CRAWLING)) {
            player.ticksSinceCrawling++;
        } else {
            player.ticksSinceCrawling = 0;
        }

        // We rely on the SNEAK_CURRENT_RAW flag for the sneaking state since it is more reliable. However, we still need to account for cases where
        // the player may not be holding the sneak bind but still cannot un-sneak (e.g. - under a slab).
        final boolean useRawSneakState = player.inputMode.equals(InputMode.MOUSE); // SNEAK_CURRENT_RAW only seems to be applied on KBM (keyboard/mouse) - is this intentional or client bug?
        if (useRawSneakState) {
            final boolean wasSneaking = player.getFlagTracker().has(EntityFlag.SNEAKING);
            final boolean forcedSneak = wasSneaking && !Collider.canStandUp(player);
            player.getFlagTracker().set(EntityFlag.SNEAKING, player.getInputData().contains(PlayerAuthInputData.SNEAK_CURRENT_RAW) || forcedSneak);
        }

        final Iterator<PlayerAuthInputData> iterator = player.getInputData().iterator();
        while (iterator.hasNext()) {
            final PlayerAuthInputData input = iterator.next();
            switch (input) {
                case START_GLIDING -> {
                    final ContainerCache cache = player.compensatedInventory.armorContainer;

                    // Prevent player from spoofing elytra gliding, could false, considering that the compensated inventory is a bit half-baked but should works in most case.
                    player.getFlagTracker().set(EntityFlag.GLIDING, BoarItemStack.of(player.getSession(), cache.get(1).getData()).is(Items.ELYTRA));
                    if (!player.getFlagTracker().has(EntityFlag.GLIDING) && !player.disableMitigations()) {
                        iterator.remove();
                    }
                }
                case STOP_GLIDING -> player.getFlagTracker().set(EntityFlag.GLIDING, false);

                case START_SPRINTING -> {
                    boolean forwardMovement = player.input.getZ() > 0;
                    player.setSprinting(forwardMovement);

                    // Don't let player send an START_SPRINTING to force server to send back a sprinting attribute or allow the
                    // client to trick the server into letting it get sprinting speed while not moving forward.
                    if (!forwardMovement && !player.disableMitigations()) {
                        iterator.remove();
                    }
                }
                case STOP_SPRINTING -> player.setSprinting(false);

                case START_SWIMMING -> player.getFlagTracker().set(EntityFlag.SWIMMING, true);
                case STOP_SWIMMING -> player.getFlagTracker().set(EntityFlag.SWIMMING, false);

                // Prevent the server from constantly trying to update these states which would cause a massive desync loop
                case START_SNEAKING ->  {
                    if (!useRawSneakState) {
                        player.getFlagTracker().set(EntityFlag.SNEAKING, true);
                    } else if (!player.getInputData().contains(PlayerAuthInputData.SNEAK_CURRENT_RAW) && !player.disableMitigations()) {
                        iterator.remove();
                    }
                }
                case STOP_SNEAKING -> {
                    if (!useRawSneakState) {
                        player.getFlagTracker().set(EntityFlag.SNEAKING, false);
                    } else if (player.getInputData().contains(PlayerAuthInputData.SNEAK_CURRENT_RAW) && !player.disableMitigations()) {
                        iterator.remove();
                    }
                }

                case START_FLYING -> player.getFlagTracker().setFlying(player.abilities.contains(Ability.MAY_FLY) || player.abilities.contains(Ability.FLYING));
                case STOP_FLYING -> player.getFlagTracker().setFlying(false);

                case STOP_SPIN_ATTACK -> {
                    if (player.dirtySpinStop) {
                        player.stopRiptide();
                        player.velocity = player.velocity.multiply(-0.2F);
                    } else if (!player.disableMitigations()) {
                        iterator.remove();
                    }
                }

                case START_USING_ITEM -> {
                    final ItemData itemData = player.compensatedInventory.inventoryContainer.getHeldItemData();
                    BoarItemStack itemStack = BoarItemStack.of(player.getSession(), itemData);

                    final ItemUseTracker.DirtyUsing armed = player.getItemUseTracker().getDirtyUsing();
                    if (armed == ItemUseTracker.DirtyUsing.NONE && player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
                        // The client sent the flag again while still using an item? Here we'll just keep the current using item state
                        continue;
                    }

                    // TODO: Try and debug inventory issues further.
                    if (itemStack.isEmpty()) {
                        player.getFlagTracker().set(EntityFlag.USING_ITEM, true);
                        player.lastItemUseStateChangeTick = player.tick;
                        player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
                        continue;
                    }

                    if (armed == ItemUseTracker.DirtyUsing.NONE) {
                        if (player.getItemUseTracker().canBeUse(itemData, itemStack.item())) {
                            player.getFlagTracker().set(EntityFlag.USING_ITEM, true);
                            player.getItemUseTracker().use(itemData, itemStack.item(), true);
                            player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
                            continue;
                        }

                        if (!player.disableMitigations()) {
                            iterator.remove();
                        }
                        continue;
                    }

                    player.getFlagTracker().set(EntityFlag.USING_ITEM, true);
                    player.getItemUseTracker().use(itemData, itemStack.item(), true);
                    player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
                }

                // Should we really validate crawling, I mean sure 1 block gap, but it's that big of advantage if they lose speed
                // in the process? It's not *that* big of an advantage since it's not really hard to get into crawling mode anyway.
                // But maybe we should still validate it in case some parkour server start complaining.
                case START_CRAWLING -> player.getFlagTracker().set(EntityFlag.CRAWLING, true);
                case STOP_CRAWLING -> player.getFlagTracker().set(EntityFlag.CRAWLING, false);
            }
        }

        final ItemUseTracker.DirtyUsing dirtyUsing = player.getItemUseTracker().getDirtyUsing();
        if (dirtyUsing == ItemUseTracker.DirtyUsing.INVENTORY_TRANSACTION && player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            // The client sent a new use transaction while still using an item (right-click spam) + server metadata causing some type of flag desync
            player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
        } else if (dirtyUsing != ItemUseTracker.DirtyUsing.NONE) {
            // Shit hack, I know I'm too lazy to properly check for when the item is actually usable eg: riptide trident in water.
            // Also, there are bugs in bedrock where the player can still use even tho they're not supposed to so what we get will never
            // be reliable (https://bugs.mojang.com/browse/MCPE/issues/MCPE-178647), call me out for being lazy but blame bugrock.
            if (dirtyUsing == ItemUseTracker.DirtyUsing.INVENTORY_TRANSACTION || dirtyUsing == ItemUseTracker.DirtyUsing.METADATA && !player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
                player.getEntity().releaseItem();
            }

            player.getFlagTracker().set(EntityFlag.USING_ITEM, false);
            player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
        }
        player.dirtySpinStop = false;
    }
}
