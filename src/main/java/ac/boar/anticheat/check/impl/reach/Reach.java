package ac.boar.anticheat.check.impl.reach;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.api.annotations.CheckInfo;
import ac.boar.anticheat.check.api.annotations.Experimental;
import ac.boar.anticheat.check.api.impl.PacketCheck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.Pair;
import ac.boar.anticheat.util.math.ReachUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;

import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.HashMap;
import java.util.Map;


@Experimental
@CheckInfo(name = "Reach")
public final class Reach extends PacketCheck {
    private final Map<Pair<Vec3, Vec3>, EntityCache> queuedHitAttacks = new HashMap<>();
    private boolean lastKnowHitWasValid;

    public Reach(BoarPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        // This should no longer be used to attack player, only transaction inventory so cancel this.
        if (event.getPacket() instanceof InteractPacket packet && packet.getAction() == InteractPacket.Action.DAMAGE) {
            event.setCancelled(true);
        }

        // Nope this is NOT an attack packet, do nothing.
        if (!(event.getPacket() instanceof InventoryTransactionPacket packet) || packet.getActionType() != 1 || packet.getTransactionType() != InventoryTransactionType.ITEM_USE_ON_ENTITY) {
            return;
        }

        final EntityCache entity = player.compensatedWorld.getEntity(packet.getRuntimeEntityId());
        // TODO: Implement reach check inside vehicle properly!
        if (entity == null || entity.isInVehicle()) {
            return;
        }

        if (player.gameType == GameType.CREATIVE || player.gameType == GameType.SPECTATOR) { // Exempted.
            return;
        }

        if (player.inputMode == InputMode.TOUCH) {
            // Don't let player spoof this and hit out of 110 FOV range, that is not possible.
            // However, I think this should be moved into a separate bad packet check since it's not vanilla behaviour.
            if (MathUtil.wrapDegrees(Math.abs(player.yaw - player.interactRotation.getY())) > 110) {
                this.lastKnowHitWasValid = false;
                event.setCancelled(true);
                return; // Invalid hit, no need to try to validate this.
            }
        }

        // So... here is the thing, we don't know the player new rotation yet, and it seems to be the case here...
        // Because of that we have to check when player HAVE sent us an auth input packet, so we have to cache this.
        final Pair<Vec3, Vec3> pair = new Pair<>(player.prevPosition, player.position);
        this.queuedHitAttacks.put(pair, entity);

        // We check in the auth input packet, but that is way too late to cancel the hit packet send to the server.
        // One way around this however, is still perform the reach check here, if the hit was invalid and the hit before that
        // is also invalid, then cancel. This way if player never flag then their hit won't cancel here, but if they HAD flag before
        // then the hit will be canceled. Now technically this can be abused, but I don't really see it as a big of a deal since
        // backtrack cheat (latency abuse) is already a thing.
        if (ReachUtil.calculateReach(player, pair, entity) > Boar.getConfig().toleranceReach()) {
            if (!this.lastKnowHitWasValid) {
                event.setCancelled(true);
            }
            this.lastKnowHitWasValid = false;
        } else {
            this.lastKnowHitWasValid = true;
        }
    }

    public void pollQueuedHits() {
        this.lastKnowHitWasValid = false;
        if (this.queuedHitAttacks.isEmpty()) {
            return;
        }

        float hitDistance = 0;
        for (Map.Entry<Pair<Vec3, Vec3>, EntityCache> entry : this.queuedHitAttacks.entrySet()) {
            final EntityCache entity = entry.getValue();
            if (entity == null) {
                continue;
            }

            Boar.debug("Step=" + entity.getCurrent().getInterpolator().getStep(), Boar.DebugMessage.INFO);
            Boar.debug("Prev=" + entity.getCurrent().getPrevPos() + ", current=" + entity.getCurrent().getPos() + ", lerpingTo=" + entity.getCurrent().getInterpolator().getTargetPos(), Boar.DebugMessage.INFO);

            // There are some edge cases when entity position is interpolating.
            final float newReachDistance = ReachUtil.calculateReach(player, entry.getKey(), entry.getValue());
            if (newReachDistance == Float.MAX_VALUE && entity.getCurrent().getInterpolator().getStep() > 1) {
                continue;
            }

            hitDistance = Math.max(newReachDistance, hitDistance);
        }

        if (hitDistance > Boar.getConfig().toleranceReach()) {
            if (hitDistance == Float.MAX_VALUE) {
                this.fail("failed to find entity in sight.");
            } else {
                this.fail("entity out of range, distance=" + hitDistance);
            }
        } else {
            Boar.debug("Valid hit distance=" + hitDistance, Boar.DebugMessage.INFO);
        }

        this.queuedHitAttacks.clear();
    }
}