package ac.boar.anticheat.prediction.ticker.impl;

import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.level.block.Fluid;

public class PlayerTicker extends LivingTicker {
    public PlayerTicker(BoarPlayer player) {
        super(player);
    }

    @Override
    public void applyInput() {
        super.applyInput();
        boolean sneaking = player.getFlagTracker().has(EntityFlag.SNEAKING) || player.getInputData().contains(PlayerAuthInputData.STOP_SNEAKING);
        if ((sneaking || player.ticksSinceCrawling > 0) && !player.getFlagTracker().has(EntityFlag.GLIDING) && !player.isInLava() && !player.touchingWater) {
            player.input = player.input.multiply(0.3F);
        }

        if (player.getFlagTracker().has(EntityFlag.USING_ITEM) && !player.getItemUseTracker().isUsingSpear()) {
            player.input = player.input.multiply(0.122499995F);
        }
    }

    @Override
    public void aiStep() {
        if (player.touchingWater && player.getInputData().contains(PlayerAuthInputData.SNEAKING) /*&& this.isAffectedByFluids()*/) {
            player.velocity.y -= 0.04F;
        }

        super.aiStep();
    }

    @Override
    protected void travel() {
        if (player.getFlagTracker().has(EntityFlag.SWIMMING)) {
            float d = MathUtil.getRotationVector(player.pitch, player.yaw).y;

            // Seems to be the case, on JE they check for fluid state 0.9 blocks up to prevent player from resurfacing when swimming
            // But on BE they seem to be setting the y motion to 0 instead (you can press space to swim up on JE but not on BE when near water surface)
            if (player.compensatedWorld.getFluidState(player.position.up(0.4F).toVector3i()).fluid() == Fluid.EMPTY && d > 0 && d < 0.55) {
                player.velocity.y = 0;
            } else {
                float e = d < -0.2 ? 0.085F : 0.06F;
                final FluidState state = player.compensatedWorld.getFluidState(player.position.toVector3i());
                if ((d <= 0.0 || state.fluid() != Fluid.EMPTY) && !player.getInputData().contains(PlayerAuthInputData.JUMPING)) {
                    player.velocity = player.velocity.add(0, (d - player.velocity.y) * e, 0);
                }
            }

            // No fucking idea why, but if it's the case then it's the case, hacks but works.
            if (player.unvalidatedTickEnd.y == 0 && player.ticksSinceSwimming > 0 && player.ticksSinceSwimming < 10 && player.getInputData().contains(PlayerAuthInputData.JUMPING)) {
                player.velocity.y = 0;
            }
        }
        super.travel();
    }
}