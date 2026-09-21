package ac.boar.anticheat.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.prediction.ticker.impl.PlayerTicker;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public class PredictionRunner {
    private final BoarPlayer player;

    public void run() {
        if (!this.findBestTickStartVelocity()) {
            return;
        }

        new PlayerTicker(player).tick();
        player.predictionResult = new PredictionData(player.beforeCollision.clone(), player.afterCollision.clone(), player.velocity.clone());
        player.lastTickFinalVelocity = player.velocity.clone();

        player.getMovementTrace().log("prediction done: predictedPos=" + player.position
                + " finalVel=" + player.velocity + " beforeCollision=" + player.beforeCollision
                + " afterCollision=" + player.afterCollision);

        if (Boar.getConfig().debugMode() && !player.getTrackedDebugPlayers().isEmpty()) {
            Boar.debug("[prediction-witness] " + player.getSession().name() + " tick=" + player.tick
                    + " path=prediction predictedPos=" + player.position + " finalVel=" + player.velocity
                    + " beforeCollision=" + player.beforeCollision + " afterCollision=" + player.afterCollision,
                    Boar.DebugMessage.WARNING);
        }
    }

    private boolean findBestTickStartVelocity() {
        player.bestPossibility = Objects.requireNonNullElseGet(player.certainVelocity, () -> new Vector(VectorType.NORMAL, player.velocity.clone()));
        player.certainVelocity = null;
        player.velocity = player.bestPossibility.getVelocity().clone();
        player.getMovementTrace().log("start velocity: type=" + player.bestPossibility.getType() + " vel=" + player.velocity);
        return true;
    }
}