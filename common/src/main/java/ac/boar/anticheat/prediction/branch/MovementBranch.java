package ac.boar.anticheat.prediction.branch;

import ac.boar.anticheat.player.data.SimulationData;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class MovementBranch {
    private SimulationData startState, endState;
    private boolean sprinting, sneaking;
    private float movementSpeed;
    private int seedId;
    private int lineageAge;
    private float offset;
    private boolean withinAcceptanceThreshold, canonicalLineage, sprintAmbiguousLineage, sneakAmbiguousLineage;
    private boolean speedAmbiguousLineage;

    // A branch that survives a tick gets reused as a "seed" for the next tick, and its end state
    // becomes that tick's starting point. So "seed state" is just another name for endState:
    // one field, viewed from two directions in time. (startState stays what THIS branch started
    // its own tick from — don't confuse the two.)
    public SimulationData seedState() {
        return this.endState;
    }

    public void setSeedState(final SimulationData state) {
        this.endState = state;
    }

    public String describe() {
        return "seed=" + this.seedId
                + " sprint=" + (this.sprinting ? "T" : "F")
                + " sneak=" + (this.sneaking ? "T" : "F")
                + " speed=" + this.movementSpeed
                + " air=" + this.effectiveAirSpeed()
                + " age=" + this.lineageAge
                + " off=" + this.offset
                + " within=" + this.withinAcceptanceThreshold
                + " canonical=" + this.canonicalLineage
                + " sprintAmbiguous=" + this.sprintAmbiguousLineage
                + " sneakAmbiguous=" + this.sneakAmbiguousLineage
                + " speedAmbiguous=" + this.speedAmbiguousLineage;
    }

    public String shortDescribe() {
        return (this.canonicalLineage ? "C" : "S" + this.seedId)
                + "/" + (this.sprinting ? "T" : "F")
                + (this.sneaking ? "+sn" : "")
                + "/" + this.movementSpeed
                + "/" + this.effectiveAirSpeed()
                + "/a" + this.lineageAge;
    }

    public String dedupeKey() {
        return this.sprinting + ":"
                + this.sneaking + ":"
                + Float.floatToIntBits(this.movementSpeed);
    }

    public float effectiveAirSpeed() {
        return this.sprinting ? 0.026F : 0.02F;
    }
}
