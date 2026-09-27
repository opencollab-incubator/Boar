package ac.boar.anticheat.prediction.ticker.impl;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.Map;

public class PlayerTicker extends LivingTicker {
    public PlayerTicker(BoarPlayer player) {
        super(player);
    }

    @Override
    public void applyInput() {
        final float rawInputLen = player.input.horizontalLength();

        super.applyInput();
        boolean sneaking = player.getFlagTracker().has(EntityFlag.SNEAKING) || player.getInputData().contains(PlayerAuthInputData.STOP_SNEAKING);
        final boolean sneakSlowdown = (sneaking || player.ticksSinceCrawling > 0 || player.getFlagTracker().has(EntityFlag.GLIDING)) && !player.isInLava() && !player.touchingWater;
        if (sneakSlowdown) {
            player.ticksSinceCanSlowdown++;

            float sneakingMultiplier = 0.3f;

            // Player don't get affected by swift sneak the first 2 ticks.
            if (player.ticksSinceCanSlowdown > 2) {
                final Map<Enchantment, Integer> enchantments = CompensatedInventory.getEnchantments(player.compensatedInventory.armorContainer.get(2).getData());
                if (enchantments.containsKey(Enchantment.SWIFT_SNEAK)) {
                    sneakingMultiplier += player.sneakingAttributeModifier;
                }
            }

            player.input = player.input.multiply(MathUtil.clamp(sneakingMultiplier, 0, 1));
            player.getMovementTrace().log("input: sneak slowdown x" + MathUtil.clamp(sneakingMultiplier, 0, 1)
                    + ", input=" + player.input);
        } else {
            player.ticksSinceCanSlowdown = 0;
        }

        final boolean usingFlag = player.getFlagTracker().has(EntityFlag.USING_ITEM);
        final boolean trackerHasItem = player.getItemUseTracker().getItem() != null;
        if (usingFlag != player.prevUsingItemFlag) {
            player.lastItemUseStateChangeTick = player.tick;
        }
        player.prevUsingItemFlag = usingFlag;

        final long sinceChange = player.tick - player.lastItemUseStateChangeTick;
        final boolean boarUsing = usingFlag && !player.getItemUseTracker().isUsingSpear();
        final boolean forced = boarUsing && player.getItemUseTracker().isSlowdownConfirmed() && !player.getItemUseTracker().isPastConsumeDuration();
        boolean applySlowdown = sneakSlowdown ? boarUsing : forced;
        final float inputLen = player.input.horizontalLength();
        final boolean unverifiedUse = usingFlag && !trackerHasItem;
        final float mx = player.clientMotion.getX(), my = player.clientMotion.getY();
        final float clientLen = (float) Math.sqrt(mx * mx + my * my);
        String reason = "state";
        if ((sinceChange < 5 || unverifiedUse) && inputLen > 1.0E-4F) {
            applySlowdown = clientLen * 0.98F < inputLen * 0.5F;
            reason = "client vector (just changed)";
        } else if (!sneakSlowdown && !forced && rawInputLen > 1.0E-4F) {
            // The client's move vector already has the item use slowdown in it (raw 1.0 -> 0.1225). For leniency, we cap
            // the client instead of re-creating its state here. Until the slowdown is forced, use the client's move vector
            // A slowed vector is always accepted (delayed server metadata can slow the client after it let go), and an unslowed one is fine
            // while the server hasn't confirmed the use yet
            applySlowdown = clientLen < rawInputLen * 0.5F;
            reason = boarUsing ? "client vector (server hasn't confirmed use yet)" : "client vector (slowed by server metadata?)";
        }

        if (applySlowdown) {
            player.input = player.input.multiply(0.122499995F);
            player.getMovementTrace().log("input: item use slowdown (" + reason + "), input=" + player.input + " clientVector=" + clientLen + " rawInput=" + rawInputLen);
        }
    }

    @Override
    public void aiStep() {
        if (player.touchingWater && player.getInputData().contains(PlayerAuthInputData.SNEAKING) /*&& this.isAffectedByFluids()*/) {
            player.velocity.y -= 0.04F;
            player.getMovementTrace().log("water: sneak sink, y velocity -0.04");
        }

        super.aiStep();
    }

    @Override
    protected void travel() {
        this.swimControl();
        super.travel();
    }

    public void applyWaterInputAfterTeleport() {
        final boolean jumping = player.getInputData().contains(PlayerAuthInputData.JUMPING) || player.getInputData().contains(PlayerAuthInputData.AUTO_JUMPING_IN_WATER);
        if (jumping && player.selectedFluid == Fluid.WATER) {
            this.updateHeadInWater();
            player.velocity = player.jump(player.velocity);
            player.getMovementTrace().log("teleport: liquid jump, vel=" + player.velocity);
        } else {
            this.swimControl();
        }
    }

    private void swimControl() {
        final boolean jumping = player.getInputData().contains(PlayerAuthInputData.JUMPING) || player.getInputData().contains(PlayerAuthInputData.AUTO_JUMPING_IN_WATER);
        if (player.getFlagTracker().has(EntityFlag.SWIMMING) && !jumping) { // SwimControlSystem::tick
            float d = MathUtil.getRotationVector(player.pitch, player.yaw).y;

            // Seems to be the case, on JE they check for fluid state 0.9 blocks up to prevent player from resurfacing when swimming
            // But on BE they seem to be setting the y motion to 0 instead (you can press space to swim up on JE but not on BE when near water surface)
            if (player.compensatedWorld.getFluidState(player.position.up(0.4F).toVector3i()).fluid() == Fluid.EMPTY && d > 0 && d < 0.55) {
                player.getMovementTrace().log("swim: at surface, y velocity set to 0 (pitchVecY=" + d + ")");
                player.velocity.y = 0;
            } else {
                float e = d < -0.2 ? 0.085F : 0.06F;
                final FluidState state = player.compensatedWorld.getFluidState(player.position.toVector3i());
                if (d <= 0.0 || state.fluid() != Fluid.EMPTY) {
                    player.velocity = player.velocity.add(0, (d - player.velocity.y) * e, 0);
                    player.getMovementTrace().log("swim: pitch adjust (pitchVecY=" + d + " e=" + e + "), vel=" + player.velocity);
                }
            }
        }
    }
}
