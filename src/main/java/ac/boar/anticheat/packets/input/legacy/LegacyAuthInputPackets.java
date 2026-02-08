package ac.boar.anticheat.packets.input.legacy;

import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.impl.OffsetHandlerCheck;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.UncertainRunner;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.InputUtil;

import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.item.Items;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.Iterator;
import java.util.Map;

public class LegacyAuthInputPackets {
    public static void updateUnvalidatedPosition(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        player.prevUnvalidatedPosition = player.unvalidatedPosition.clone();
        player.unvalidatedPosition = new Vec3(packet.getPosition().sub(0, player.getYOffset(), 0));
        player.unvalidatedTickEnd = new Vec3(packet.getDelta());
    }

    public static void doPostPrediction(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        player.postTick();
        player.getTeleportUtil().cachePosition(player.tick, player.position.add(0, player.getYOffset(), 0).toVector3f());

        final UncertainRunner uncertainRunner = new UncertainRunner(player);

        // Properly calculated offset by comparing position instead of poorly calculated velocity that get calculated using (pos - prevPos) to account for floating point errors.
        float offset = player.position.distanceTo(player.unvalidatedPosition);
        float extraOffset = uncertainRunner.extraOffset(offset);
        offset -= extraOffset;
        offset -= uncertainRunner.extraOffsetNonTickEnd(offset);
        uncertainRunner.uncertainPushTowardsTheClosetSpace();

        for (Map.Entry<Class<?>, Check> entry : player.getCheckHolder().entrySet()) {
            Check v = entry.getValue();
            if (v instanceof OffsetHandlerCheck check) {
                check.onPredictionComplete(offset);
            }
        }

        // Have to do this due to loss precision, especially elytra!
        if (player.velocity.distanceTo(player.unvalidatedTickEnd) - extraOffset < player.getMaxOffset()) {
            player.velocity = player.unvalidatedTickEnd.clone();
        }
        correctInputData(player, packet);

        if (offset < player.getMaxOffset()) {
            player.setPos(player.unvalidatedPosition.clone(), false);
        }

        player.prevPosition = player.position;
    }

    public static void correctInputData(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        if (player.isMovementExempted()) {
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

        InputUtil.processInput(player, packet);

        player.prevYaw = player.yaw;
        player.prevPitch = player.pitch;
        player.yaw = packet.getRotation().getY();
        player.pitch = packet.getRotation().getX();

        player.rotation = packet.getRotation();
        player.interactRotation = packet.getInteractRotation().clone();

        player.inputMode = packet.getInputMode();

        if (processInputData) {
            processInputData(player);

            // Player isn't moving forward but is sprinting and their flag sync, this shouldn't happen.
            if (player.input.z <= 0 && player.getFlagTracker().has(EntityFlag.SPRINTING) && player.desyncedFlag.get() == -1) {
                player.getFlagTracker().set(EntityFlag.SPRINTING, false);

                // Tell geyser that the player "want" to stop sprinting.
                packet.getInputData().add(PlayerAuthInputData.STOP_SPRINTING);
            }
        }
    }

    public static void processInputData(final BoarPlayer player) {
        if (!player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            player.sinceTridentUse = 0;
        }

        if (player.getFlagTracker().has(EntityFlag.SWIMMING)) {
            player.ticksSinceSwimming++;
        } else {
            player.ticksSinceSwimming = 0;
        }

        if (player.getFlagTracker().has(EntityFlag.CRAWLING)) {
            player.ticksSinceCrawling++;
        } else {
            player.ticksSinceCrawling = 0;
        }

        final Iterator<PlayerAuthInputData> iterator = player.getInputData().iterator();
        while (iterator.hasNext()) {
            final PlayerAuthInputData input = iterator.next();
            switch (input) {
                case START_GLIDING -> {
                    final ContainerCache cache = player.compensatedInventory.armorContainer;

                    // Prevent player from spoofing elytra gliding, could false, considering that the compensated inventory is a bit half-baked but should works in most case.
                    player.getFlagTracker().set(EntityFlag.GLIDING, player.compensatedInventory.translate(cache.get(1).getData()).getId() == Items.ELYTRA.javaId());
                    if (!player.getFlagTracker().has(EntityFlag.GLIDING)) {
                        iterator.remove();
                    }
                }
                case STOP_GLIDING -> player.getFlagTracker().set(EntityFlag.GLIDING, false);

                // Don't let player do backwards sprinting!
                case START_SPRINTING -> {
                    boolean forwardMovement = player.input.getZ() > 0;
                    player.setSprinting(forwardMovement);

                    // Don't let player send an START_SPRINTING to force server to send back a sprinting attribute.
                    // or trick Geyser in any way, since it's not really reliable...
                    if (!forwardMovement) {
                        iterator.remove();
                    }
                }
                case STOP_SPRINTING -> player.setSprinting(false);
                case START_SNEAKING -> player.getFlagTracker().set(EntityFlag.SNEAKING, true);
                case STOP_SNEAKING -> player.getFlagTracker().set(EntityFlag.SNEAKING, false);

                case START_SWIMMING -> player.getFlagTracker().set(EntityFlag.SWIMMING, true);
                case STOP_SWIMMING -> player.getFlagTracker().set(EntityFlag.SWIMMING, false);

                case START_FLYING -> player.getFlagTracker().setFlying(player.abilities.contains(Ability.MAY_FLY) || player.abilities.contains(Ability.FLYING));
                case STOP_FLYING -> player.getFlagTracker().setFlying(false);

                case STOP_SPIN_ATTACK -> {
                    if (player.dirtySpinStop) {
                        player.stopRiptide();
                        player.velocity = player.velocity.multiply(-0.2F);
                    } else {
                        iterator.remove();
                    }
                }

                case START_USING_ITEM -> {
                    // Seems to be the case, this should only be taken seriously when it's trigger by sever metadata
                    // or actual item use from inventory transaction packet.
                    if (player.getItemUseTracker().getDirtyUsing() == ItemUseTracker.DirtyUsing.NONE) {
                        iterator.remove();
                        return;
                    }

                    final ItemData itemData = player.compensatedInventory.inventoryContainer.getHeldItemData();
                    ItemStack item = player.compensatedInventory.translate(itemData);

                    // The player in fact CAN use an item that is not air even if that item is eg: dirt for 1 tick.
                    // However, this likely will only happen when flag de-sync.
                    if (item.getId() == Items.AIR_ID) {
                        iterator.remove();
                        return;
                    }

//                    System.out.println("Start using item: " + itemData);
                    player.getFlagTracker().set(EntityFlag.USING_ITEM, true);
                    player.getItemUseTracker().use(itemData, item.getId(), true);
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
        if (dirtyUsing != ItemUseTracker.DirtyUsing.NONE) {
            // Shit hack, I know I'm too lazy to properly check for when the item is actually usable eg: riptide trident in water.
            // Also, there are bugs in bedrock where the player can still use even tho they're not supposed to so what we get will never
            // be reliable (https://bugs.mojang.com/browse/MCPE/issues/MCPE-178647), call me out for being lazy but blame bugrock.
            if (dirtyUsing == ItemUseTracker.DirtyUsing.INVENTORY_TRANSACTION || dirtyUsing == ItemUseTracker.DirtyUsing.METADATA && !player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
                player.getSession().releaseItem();
            }

            player.getFlagTracker().set(EntityFlag.USING_ITEM, false);
            player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
        }
        player.dirtySpinStop = false;
    }
}
