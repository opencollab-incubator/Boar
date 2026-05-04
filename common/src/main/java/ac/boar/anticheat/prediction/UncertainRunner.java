package ac.boar.anticheat.prediction;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.List;

// Things that I don't even bother account for...
@RequiredArgsConstructor
public class UncertainRunner {
    private final BoarPlayer player;

    // For now this will only be use for pushing player out of block, mojang making my life harder by make it differ from JE ofc.
    /**
        Note: for anticheat developers on other platform, this code if when the player have the flag PUSH_TOWARDS_CLOSEST_SPACE
        if there isn't, the player can be instantly push out by bruteforce direction to push out then getting the direction with the smallest value
        (<a href="https://github.com/GeyserMC/Geyser/blob/43467f7d19d5a86611c47b2d42b03357b319aac4/core/src/main/java/org/geysermc/geyser/translator/collision/BlockCollision.java#L77">...</a>)
        I can't remember the max value to be pushed out, maybe around 0.8+? but yep. You can either do uncertainty or calculate it that way.
        However, without the flag getting into crawling mode is annoying and who knows is mojang going to break it or not.
    **/
    public void uncertainPushTowardsTheClosetSpace() {
        // Close enough, no uncertainty here, we're sure.
        if (player.velocity.distanceTo(player.unvalidatedTickEnd) <= 1.0E-4) {
            // TODO: Should we enforce this to prevent cheater from not being pushed out of block?
            return;
        }

        // Now let's account for pushing out of block uncertainty.
        // Since block push will only affect X and Z direction so this case no need to account for push, player velocity is wrong.
        if (Math.abs(player.unvalidatedTickEnd.y - player.velocity.y) > 1.0E-4) {
            return;
        }
        // Assuming that we're missing the push out of block velocity, then subtracting velocity will do the job.
        Vec3 pushTowardsClosetSpaceVel = player.unvalidatedTickEnd.subtract(player.velocity);

        // The push towards velocity should ALWAYS be normalized in a way that the squared length will always be smaller than 0.01 or at least equals (unless small floating point errors).
        // So if the player push towards velocity is any larger than this... They're cheating.
        if (pushTowardsClosetSpaceVel.horizontalLengthSquared() > 0.01 + 1.0E-3F) {
            return;
        }

        player.nearBamboo = false;

        // Let's check to see if the player is actually inside a block...
        final List<Box> collisions = player.compensatedWorld.collectColliders(new ArrayList<>(), player.boundingBox.expand(1.0E-3F));
        if (collisions.isEmpty() && !player.nearBamboo) {
            // Nope, again the player is likely cheating, or we're falsing something else, also allow bamboo to bypass this.
            return;
        }

        final int signX = MathUtil.sign(pushTowardsClosetSpaceVel.x), signZ = MathUtil.sign(pushTowardsClosetSpaceVel.z);
        final int targetX = GenericMath.floor(player.position.x) + signX, targetZ = GenericMath.floor(player.position.z) + signZ;
        final Box box = new Box(targetX, player.boundingBox.minY, targetZ, targetX + 1, player.boundingBox.maxY, targetZ + 1).contract(1.0E-3F);

        // The direction player is trying to move to is also not empty, so this is likely wrong.
        // TODO: Not reliable.... Well let's rely on vanilla to prevent phase then...
//        if (!player.compensatedWorld.noCollision(box)) {
//            return;
//        }

        // TODO: Enforce this further.
        player.velocity = player.unvalidatedTickEnd.clone();
    }

    public float extraOffsetNonTickEnd(float offset) {
        float extra = 0;

        Vec3 actual = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        Vec3 predicted = player.position.subtract(player.prevUnvalidatedPosition);
        boolean validYOffset = Math.abs(player.position.y - player.unvalidatedPosition.y) - extra <= player.getMaxOffset();
        boolean actualSpeedSmallerThanPredicted = actual.horizontalLengthSquared() < predicted.horizontalLengthSquared();
        boolean sameDirection = MathUtil.sameDirection(actual, predicted);
        boolean sameDirectionOrZero = (MathUtil.sign(actual.x) == MathUtil.sign(predicted.x) || actual.x == 0)
                && MathUtil.sign(actual.y) == MathUtil.sign(predicted.y) && (MathUtil.sign(actual.z) == MathUtil.sign(predicted.z) || actual.z == 0);
        if (validYOffset && (sameDirection || sameDirectionOrZero) && actualSpeedSmallerThanPredicted && player.nearBamboo && player.horizontalCollision) {
            extra = offset;
        }

        return extra;
    }

    public float extraOffset(float offset) {
        float extra = 0;
        if (player.thisTickSpinAttack) {
            extra += player.thisTickOnGroundSpinAttack ? 0.08F : 0.008F;
        }

        Vec3 actual = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        Vec3 predicted = player.position.subtract(player.prevUnvalidatedPosition);

        boolean validYOffset = Math.abs(player.position.y - player.unvalidatedPosition.y) - extra <= player.getMaxOffset();
        boolean sameDirection = MathUtil.sameDirection(actual, predicted);
        boolean actualSpeedSmallerThanPredicted = actual.horizontalLengthSquared() < predicted.horizontalLengthSquared();

        boolean haveSoulSpeed = CompensatedInventory.getEnchantments(player.compensatedInventory.armorContainer.get(3).getData()).containsKey(Enchantment.SOUL_SPEED);
        if (player.soulSandBelow && !haveSoulSpeed && validYOffset && actualSpeedSmallerThanPredicted && sameDirection) {
            extra = offset;
        }

        if (player.beingPushByLava && validYOffset) {
            extra += 0.004F;

            if (sameDirection) {
                if (player.input.horizontalLengthSquared() > 0) {
                    Vec3 subtractedSpeed = actual.subtract(MathUtil.sign(player.afterCollision.x) * 0.02F, 0, MathUtil.sign(player.afterCollision.z) * 0.02F);

                    if (subtractedSpeed.horizontalLengthSquared() < predicted.horizontalLengthSquared()) {
                        extra = offset;
                    }
                } else {
                    if (actual.horizontalLengthSquared() < predicted.horizontalLengthSquared()) {
                        extra = offset;
                    }
                }
            }
        }

        // .... This is weird, no idea why.
        if (player.hasDepthStrider) {
            if (actualSpeedSmallerThanPredicted && validYOffset) {
                extra = offset;
            }
        }

        if (player.getFlagTracker().has(EntityFlag.GLIDING)) {
            extra += 1.0E-4F; // gliding accuracy is... yuck.

            if (offset <= 8.0E-4 && player.glideBoostTicks >= 0) {
                extra = offset;
            }
        }

        return extra;
    }
}
