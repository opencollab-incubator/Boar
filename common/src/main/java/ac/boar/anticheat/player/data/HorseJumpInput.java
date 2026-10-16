package ac.boar.anticheat.player.data;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.vehicle.HorseJumpPhysics;
import ac.boar.anticheat.prediction.vehicle.VehicleTicker;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

// The client's charged jump bar for the horse the player steers. One per player (BoarPlayer.horseJumpInput), stepped once per input (tick).
// The bar fills while jump is held. On release it sets the ride's pending jump power (horsePendingScale) and marks the horse as standing.
// known() is false after a skipped input tick or an invalidate(). HorseTicker then tries every jump power on the next release.
public final class HorseJumpInput {
    private int counter;
    private float bar;
    private boolean previousHeld;
    private boolean counterKnown, barKnown;
    private long lastFrame = Long.MIN_VALUE;

    public boolean known() {
        return this.counterKnown && this.barKnown;
    }

    public void invalidate() {
        this.counterKnown = this.barKnown = false;
    }

    public void localDismount() {
        this.counter = 0;
        this.bar = 0;
        this.counterKnown = this.barKnown = true;
        // LocalPlayer::stopRiding does not clear the previous input bit.
    }

    public void tick(final BoarPlayer player, final long frame, final boolean held) {
        if (this.lastFrame != Long.MIN_VALUE && frame != this.lastFrame + 1) {
            invalidate();
            player.acceptedInputEpoch++;
            if (player.vehicleData != null) {
                player.vehicleData.invalidateHorseJump();
                player.vehicleData.invalidateHorseStand();
            }
            for (EntityCache actor : player.compensatedWorld.getEntities().values()) {
                actor.getHorseJumpEffect().invalidateClock();
                actor.getHorseGround().markUnknown();
            }
            for (EntityCache actor : player.compensatedWorld.getPendingEntities().values()) {
                actor.getHorseGround().markUnknown();
            }
        }
        this.lastFrame = frame;
        for (EntityCache actor : player.compensatedWorld.getEntities().values()) {
            actor.getHorseGround().inputBoundary();
        }
        final VehicleData vehicle = player.vehicleData;
        final EntityCache cache = vehicle == null ? null : player.compensatedWorld.getEntity(vehicle.vehicleRuntimeId);
        final boolean controlling = vehicle != null && !vehicle.pendingLeave && vehicle.type == VehicleType.HORSE && vehicle.matchesMountedEntity(cache)
                && cache.isControlledBy(player.uniqueEntityId) && VehicleTicker.clientSimulates(player, vehicle);
        final boolean gate = controlling && HorseJumpPhysics.hasControlFlag(cache) && !cache.hasFlag(EntityFlag.HAS_DASH_COOLDOWN);
        advance(held, gate, vehicle);
    }

    // Steps in order: reset, passenger trigger, request, then StorePreviousClientInput.
    // vehicle is never null when gate is true. tick() sets gate only for a ride (controlling needs vehicle != null).
    private void advance(final boolean held, final boolean gate, final VehicleData vehicle) {
        final int oldCounter = this.counter;
        if (oldCounter < 0) {
            this.counter = oldCounter + 1;
            if (oldCounter == -1 && this.counterKnown) {
                this.bar = 0;
                this.barKnown = true;
            }
        }
        if (gate && this.previousHeld) {
            if (held) {
                final int old = this.counter;
                this.counter = old + 1;
                this.bar = old < 9 ? (float) this.counter * 0.1F : (2.0F / (float) (old - 8)) * 0.1F + 0.8F;
                this.barKnown = this.counterKnown;
            } else {
                if (vehicle.ticks == 0 || vehicle.horseMountWindow != VehicleData.HorseMountWindow.NONE) {
                    vehicle.horseMountWindow = VehicleData.HorseMountWindow.RELEASED;
                }
                // VehicleData.matchesMountedEntity(null) is false.
                if (vehicle.matchesMountedEntity(vehicle.mountedEntity)) {
                    vehicle.mountedEntity.getHorseImmobility().markSlidingPossible();
                }
                this.counter = -10;
                this.counterKnown = true;
                if (this.barKnown) {
                    final int request = (int) Math.floor(this.bar * 100.0F);
                    if (request >= 0) {
                        vehicle.horsePendingScale = HorseJumpPhysics.scale(request);
                        vehicle.horseStanding = vehicle.horseStandSliding = true;
                    }
                } else {
                    vehicle.invalidateHorseJump();
                    vehicle.invalidateHorseStand();
                }
            }
        }
        this.previousHeld = held;
    }
}
