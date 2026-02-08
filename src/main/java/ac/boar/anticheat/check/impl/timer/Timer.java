package ac.boar.anticheat.check.impl.timer;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.api.annotations.CheckInfo;
import ac.boar.anticheat.check.api.annotations.Experimental;
import ac.boar.anticheat.check.api.impl.PingBasedCheck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.LatencyUtil;

@Experimental
@CheckInfo(name = "Timer")
public final class Timer extends PingBasedCheck {
    private static final long AVERAGE_DISTANCE = (long) 5e+7;

    private long lastNS, balance, prevTick;
    private long loseBalance;
    private boolean beforeAuthInput;

    public Timer(final BoarPlayer player) {
        super(player);
    }

    @Override
    public void onLatencyAccepted(LatencyUtil.Latency latency) {
        if (!this.beforeAuthInput) {
            return;
        }

        this.beforeAuthInput = false;
        if (latency.ns() > System.nanoTime() + this.balance) {
            long distance = (latency.ns() - (System.nanoTime() + this.balance)) - (AVERAGE_DISTANCE / 2);
            this.balance += distance;
            this.loseBalance = Math.max(0, this.loseBalance - distance);

//            Boar.debug(getDisplayName() + " is behind, likely fake lagging, distance=" + distance, Boar.DebugMessage.INFO);
        }
    }

    public boolean isInvalid() {
        if (this.lastNS == 0 || player.inLoadingScreen || player.sinceLoadingScreen < 20) {
            this.lastNS = System.nanoTime();
            this.prevTick = player.tick;
            this.balance = 0;
            return false;
        }

        boolean valid = true;

        long distance = System.nanoTime() - this.lastNS;
        long neededDistance = (player.tick - this.prevTick) * AVERAGE_DISTANCE;

        final long limit = (long) (AVERAGE_DISTANCE + 1e+7 + 3e+6);
        if (this.balance > limit) {
            if (this.balance - this.loseBalance <= limit) {
                this.loseBalance -= AVERAGE_DISTANCE;
                Boar.debug(getDisplayName() + " failed timer check due to balance limiter, but won't flag since player could actually be lagging.", Boar.DebugMessage.INFO);
            } else {
                this.fail("balance=" + this.balance + ", player is ahead!");
            }

            player.getTeleportUtil().teleportTo(player.getTeleportUtil().getLastKnowValid());
            this.balance -= AVERAGE_DISTANCE;
            valid = false;
        } else {
            long maxBalanceAdvantage = (long) Math.max(0, Boar.getConfig().maxBalanceAdvantage() * 1e+6);
            if (this.balance <= -Math.abs(maxBalanceAdvantage + AVERAGE_DISTANCE) && Boar.getConfig().maxBalanceAdvantage() > 0) {
                this.loseBalance = Math.abs(this.balance);
                this.balance = -AVERAGE_DISTANCE;
            }
        }

        this.balance -= distance - neededDistance;
        this.lastNS = Math.max(this.lastNS, System.nanoTime());
        this.prevTick = player.tick;

        this.beforeAuthInput = true;
        return !valid;
    }
}