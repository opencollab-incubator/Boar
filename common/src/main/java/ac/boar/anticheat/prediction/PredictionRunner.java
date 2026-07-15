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
    }

    private boolean findBestTickStartVelocity() {
        player.bestPossibility = Objects.requireNonNullElseGet(player.certainVelocity, () -> new Vector(VectorType.NORMAL, player.velocity.clone()));
        player.certainVelocity = null;

        if (player.bestPossibility == null) {
            return false;
        }

        if (player.bestPossibility.getType() == VectorType.VELOCITY) {
            Boar.debug("[velocity-debug] predict tick=" + player.tick + " velocity=" + player.bestPossibility.getVelocity() + " actualDelta=" + player.unvalidatedTickEnd + " pos=" + player.position + " unvalidated=" + player.unvalidatedPosition, Boar.DebugMessage.INFO);
        }

        // We can store the ACTUAL prediction now.
        player.velocity = player.bestPossibility.getVelocity().clone();
        return true;
    }
}