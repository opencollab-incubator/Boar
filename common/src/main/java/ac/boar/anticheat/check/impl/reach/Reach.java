package ac.boar.anticheat.check.impl.reach;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.api.BaseCheck;
import ac.boar.anticheat.check.api.impl.PacketCheck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.Pair;
import ac.boar.anticheat.util.math.ReachUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.api.anticheat.annotations.Experimental;
import ac.boar.protocol.api.CloudburstPacketEvent;
import io.netty.util.ReferenceCountUtil;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;

import java.util.ArrayList;
import java.util.List;


@Experimental
@CheckInfo(name = "Reach")
public final class Reach extends BaseCheck implements PacketCheck {

    private static final int TELEPORT_MITIGATION_ONLY_TICKS = 20;
    private static final int MAX_PENDING_ATTACKS = 2;

    private final List<PendingAttack> pending = new ArrayList<>();
    private float buffer = 0f;

    public Reach(BoarPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        // The legacy InteractPacket.DAMAGE path is no longer used by the client for attacks.
        // Suppress it so a spoofed one cannot bypass the deferred reach check below.
        if (event.getPacket() instanceof InteractPacket interact && interact.getAction() == InteractPacket.Action.DAMAGE) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getPacket() instanceof InventoryTransactionPacket packet)
                || packet.getActionType() != 1
                || packet.getTransactionType() != InventoryTransactionType.ITEM_USE_ON_ENTITY) {
            return;
        }

        final EntityCache entity = player.compensatedWorld.getEntity(packet.getRuntimeEntityId());
        // TODO: vehicle reach handling. For now we don't have the data to validate, so let the
        // attack through unchanged rather than guess.
        if (entity == null || entity.isInVehicle()) {
            return;
        }

        if (player.gameType == GameType.CREATIVE || player.gameType == GameType.SPECTATOR) {
            return;
        }

        final boolean invalidTouchRotation = player.inputMode == InputMode.TOUCH && MathUtil.wrapDegrees(Math.abs(player.yaw - player.interactRotation.getY())) > 90;
        if (invalidTouchRotation) {
            this.fail("invalid touch rotation, yaw=" + player.yaw + ", interactYaw=" + player.interactRotation.getY());
            if (!player.disableMitigations()) {
                event.setCancelled(true);
                return;
            }
        }

        event.setCancelled(true);
        ReferenceCountUtil.retain(packet);
        if (this.pending.size() == MAX_PENDING_ATTACKS) {
            this.resolveInvalid(this.pending.removeFirst());
        }
        this.pending.add(new PendingAttack(
                packet,
                entity,
                new Pair<>(player.prevPosition, player.position),
                new Pair<>(entity.getCurrent().getPrevPos(), entity.getCurrent().getPos()),
                player.position.squaredDistanceTo(player.unvalidatedPosition) > 0.000001 || player.prevPosition.squaredDistanceTo(player.prevUnvalidatedPosition) > 0.000001
        ));
    }

    public void invalidatePending() {
        if (this.pending.isEmpty()) {
            return;
        }

        for (PendingAttack attack : this.pending) {
            this.resolveInvalid(attack);
        }
        this.pending.clear();
    }

    public void validatePending() {
        if (this.pending.isEmpty()) {
            return;
        }

        // if the server position and client position are far enough, the reach calculation may be a bit off and cause some false flags
        // we can still mitigate for these hits though to prevent bypasses
        for (PendingAttack attack : this.pending) {
            final float reach = ReachUtil.calculateReach(player, attack.attackerPositions, attack.entity, attack.entityPositionsAtAttack);
            final boolean mitigateOnly = attack.hasPosDrift || player.getTeleportUtil().isTeleporting() || player.getTeleportUtil().correctedWithin(TELEPORT_MITIGATION_ONLY_TICKS);
            if (reach > Boar.getConfig().toleranceReach()) {
                if (!mitigateOnly && reach != Float.MAX_VALUE) {
                    this.fail("distance=" + reach);
                }
                this.resolveInvalid(attack);
            } else {
                player.injectClientPacket(attack.packet);
                this.buffer = Math.max(this.buffer - 0.01f, 0f);
            }
        }

        this.pending.clear();
    }

    private void resolveInvalid(PendingAttack attack) {
        if (player.disableMitigations()) {
            player.injectClientPacket(attack.packet);
        } else {
            ReferenceCountUtil.safeRelease(attack.packet);
        }
    }

    @Override
    public void fail(String verbose) {
        this.buffer = Math.min(this.buffer + 1, 2f);
        if (this.buffer >= 1.01f) {
            super.fail(verbose);
        }
    }

    private record PendingAttack(
            InventoryTransactionPacket packet,
            EntityCache entity,
            Pair<Vec3, Vec3> attackerPositions,
            Pair<Vec3, Vec3> entityPositionsAtAttack,
            boolean hasPosDrift
    ) {}
}
