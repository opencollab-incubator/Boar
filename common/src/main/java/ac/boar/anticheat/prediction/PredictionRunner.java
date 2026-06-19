package ac.boar.anticheat.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.prediction.ticker.impl.PlayerTicker;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public class PredictionRunner {
    private final BoarPlayer player;

    public void run() {
        if (!this.findBestTickStartVelocity()) {
            return;
        }

        if (MovementDebug.enabled()) {
            MovementDebug.log(player, "TICK-START", "startVel=" + MovementDebug.vec(player.velocity)
                    + " velType=" + player.bestPossibility.getType()
                    + " pos=" + MovementDebug.vec(player.position)
                    + " unvalidatedPos=" + MovementDebug.vec(player.unvalidatedPosition)
                    + " prevPos=" + MovementDebug.vec(player.prevPosition)
                    + " yaw=" + player.yaw + " pitch=" + player.pitch
                    + " input=" + MovementDebug.vec(player.input)
                    + " speed=" + player.getSpeed()
                    + " | " + MovementDebug.flags(player)
                    + " | inputData=" + player.getInputData());
        }

        new PlayerTicker(player).tick();
        player.predictionResult = new PredictionData(player.beforeCollision.clone(), player.afterCollision.clone(), player.velocity.clone());
        player.lastTickFinalVelocity = player.velocity.clone();

        if (MovementDebug.enabled()) {
            final Vec3 predictedDelta = player.afterCollision;
            final Vec3 actualDelta = player.unvalidatedTickEnd;
            final Vec3 offset = predictedDelta.subtract(actualDelta);
            MovementDebug.log(player, "TICK-END", "predictedMove(beforeCollision)=" + MovementDebug.vec(player.beforeCollision)
                    + " predictedMove(afterCollision)=" + MovementDebug.vec(predictedDelta)
                    + " clientMove(unvalidatedTickEnd)=" + MovementDebug.vec(actualDelta)
                    + " offset=" + MovementDebug.vec(offset)
                    + " offsetLen=" + offset.length()
                    + " nextTickVel=" + MovementDebug.vec(player.velocity)
                    + " newPos=" + MovementDebug.vec(player.position)
                    + " | " + MovementDebug.flags(player));
        }
    }

    private boolean findBestTickStartVelocity() {
        player.bestPossibility = Objects.requireNonNullElseGet(player.certainVelocity, () -> new Vector(VectorType.NORMAL, player.velocity.clone()));
        player.certainVelocity = null;

        if (player.bestPossibility.getType() == VectorType.VELOCITY) {
            Boar.debug("[velocity-debug] predict tick=" + player.tick + " velocity=" + player.bestPossibility.getVelocity() + " actualDelta=" + player.unvalidatedPosition.clone().subtract(player.prevUnvalidatedPosition.clone()) + " pos=" + player.position + " unvalidated=" + player.unvalidatedPosition, Boar.DebugMessage.INFO);
        }

        // We can start the ACTUAL prediction now.
        player.velocity = player.bestPossibility.getVelocity();
        return true;
    }
}