package ac.boar.anticheat.check.impl.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.alert.AlertManager;
import ac.boar.anticheat.check.api.BaseCheck;
import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.anticheat.check.api.impl.OffsetHandlerCheck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

@CheckInfo(name = "DebugOffset")
public class DebugOffsetA extends BaseCheck implements OffsetHandlerCheck {
    public DebugOffsetA(BoarPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(float offset) {
        if (player.getTrackedDebugPlayers().isEmpty()) {
            return;
        }

        final AlertManager alertManager = Boar.getInstance().getAlertManager();

        final float maxOffset = player.getMaxOffset();
        // Position offsets use the coordinate-aware window; the EOT/delta offset stays on the flat base.
        final float positionOffset = player.getPositionOffset();
        float eotOffset = player.unvalidatedTickEnd.distanceTo(player.velocity);

        Vec3 predicted = player.position.subtract(player.prevUnvalidatedPosition);
        Vec3 actual = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        if (actual.length() > 1e-5 || offset > positionOffset || eotOffset > maxOffset) {
            String colorOffset = offset > positionOffset ? "§c" : offset > 1.0E-5 ? "§6" : "§a";
            if (player.position.distanceTo(player.unvalidatedPosition) > positionOffset && offset < positionOffset) {
                colorOffset = "§7";
            }

            String predDebug = colorOffset + "O:" + offset + ", T: " + player.bestPossibility.getType() + ", P: " + predicted.x + "," + predicted.y + "," + predicted.z + ", pos=" + player.position;
            alertManager.alertToPlayers(player.getTrackedDebugPlayers().values().stream().toList(), predDebug);
            alertManager.alertToPlayers(player.getTrackedDebugPlayers().values().stream().toList(), colorOffset + "A: " + actual.x + "," + actual.y + "," + actual.z + ", " + "SPRINTING=" + player.getFlagTracker().has(EntityFlag.SPRINTING) + ", SNEAKING=" + player.getFlagTracker().has(EntityFlag.SNEAKING) + ", water=" + player.touchingWater);
            alertManager.alertToPlayers(player.getTrackedDebugPlayers().values().stream().toList(), "A EOT: " + player.velocity.toVector3f().toString());
            alertManager.alertToPlayers(player.getTrackedDebugPlayers().values().stream().toList(), "EOT O: " + (eotOffset > 1e-4 ? "§b" : "§a") + eotOffset + "," + player.unvalidatedTickEnd.toVector3f().toString());
        }
    }
}
