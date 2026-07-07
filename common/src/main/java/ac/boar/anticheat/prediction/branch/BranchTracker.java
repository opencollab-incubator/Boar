package ac.boar.anticheat.prediction.branch;

import ac.boar.anticheat.data.vanilla.Attribute;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.PlayerData;
import ac.boar.anticheat.player.data.SimulationData;
import ac.boar.anticheat.prediction.MovementDebug;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
public final class BranchTracker {

    public enum Dormancy {
        // Not dormant: either everything is certain, or branching is actively running.
        NONE,
        // Unsure whether the player is sprinting (this also makes their movement speed unsure).
        SPRINT,
        // Sure about sprinting (or that's being branched along with SPEED), but unsure which movement speed value the client is using.
        SPEED
    }

    @Getter(AccessLevel.NONE)
    private final BoarPlayer player;

    private final List<MovementBranch> survivors = new ArrayList<>();

    private Dormancy dormancy = Dormancy.NONE;

    private long lastSprintUncertainTick = Long.MIN_VALUE;
    private long lastSneakUncertainTick = Long.MIN_VALUE;

    // Debug only stuff
    private final ArrayDeque<Boolean> forcedAmbiguityTicks = new ArrayDeque<>(10);
    private int forcedAmbiguityTickCount;
    private int deviatingWinnerStreak;
    private long lastBranchTick = -1L;
    private int lastBranchCount;
    private String lastWinnerSummary = "";

    public boolean isSprintDormant() {
        return this.dormancy == Dormancy.SPRINT;
    }

    public boolean isSpeedDormant() {
        return this.dormancy == Dormancy.SPEED;
    }

    public void enterDormancy(final Dormancy kind) {
        this.dormancy = kind;
        this.survivors.clear();
        this.clearLastSelectionDebug();
    }

    public void clearDormancy() {
        this.dormancy = Dormancy.NONE;
    }

    public void discardBranches(final String reason) {
        final int survivorsDropped = this.survivors.size();
        final boolean sprintStillOpen = this.uncertainThisOrLastTick(this.lastSprintUncertainTick)
                || this.survivors.stream().anyMatch(MovementBranch::isSprintAmbiguousLineage);
        //final boolean sneakStillOpen = this.uncertainThisOrLastTick(this.lastSneakUncertainTick) || this.survivors.stream().anyMatch(MovementBranch::isSneakAmbiguousLineage);

        if (sprintStillOpen) {
            this.dormancy = Dormancy.SPRINT;
        } else if (!this.survivors.isEmpty() && this.dormancy != Dormancy.SPRINT) {
            // Live branches disagree about (at least) movement speed, so that question stays open.
            this.dormancy = Dormancy.SPEED;
        }

        this.survivors.clear();
        this.deviatingWinnerStreak = 0;
        this.clearLastSelectionDebug();

        if (survivorsDropped > 0) {
            this.player.getSession().sendMessage("Branch state cleared! simTick=" + this.player.tick + " reason=" + reason + " survivorsDropped=" + survivorsDropped);
            MovementDebug.log(this.player, "BRANCH-CLEAR", "reason=" + reason + " survivorsDropped=" + survivorsDropped);
        }
    }

    public void resetAll() {
        this.survivors.clear();
        this.deviatingWinnerStreak = 0;
        this.lastSprintUncertainTick = Long.MIN_VALUE;
        this.lastSneakUncertainTick = Long.MIN_VALUE;
        this.clearDormancy();
        this.clearLastSelectionDebug();
    }

    private boolean uncertainThisOrLastTick(final long lastUncertainTick) {
        return lastUncertainTick != Long.MIN_VALUE && this.player.tick - lastUncertainTick <= 1;
    }

    public boolean wasSprintUncertainLastTick(final long tick) {
        return tick - this.lastSprintUncertainTick == 1;
    }

    public boolean wasSneakUncertainLastTick(final long tick) {
        return tick - this.lastSneakUncertainTick == 1;
    }

    public void clearLastSelectionDebug() {
        this.lastBranchTick = -1L;
        this.lastBranchCount = 0;
        this.lastWinnerSummary = "";
    }

    public void recordSneakContext(final long tick, final PlayerData.SneakContext context) {
        if (context != null && context.uncertain()) {
            this.lastSneakUncertainTick = tick;
        }
    }

    public void recordSprintContext(final long tick, final PlayerData.SprintContext context) {
        if (context != null && context.uncertain()) {
            this.lastSprintUncertainTick = tick;
        }

        // Debug only: track how often the client forces ambiguity by sending sprint start + stop in the same tick
        final boolean forcedAmbiguity = context != null && context.startEdge() && context.stopEdge();
        this.forcedAmbiguityTicks.addLast(forcedAmbiguity);
        if (forcedAmbiguity) {
            this.forcedAmbiguityTickCount++;
        }
        while (this.forcedAmbiguityTicks.size() > 10) {
            if (this.forcedAmbiguityTicks.removeFirst()) {
                this.forcedAmbiguityTickCount--;
            }
        }
    }

    public void recordSelection(final long tick, final int branchCount, final MovementBranch winner, final boolean winnerMatchesAssumedState) {
        this.lastBranchTick = tick;
        this.lastBranchCount = branchCount;
        this.lastWinnerSummary = winner.shortDescribe();
        if (winnerMatchesAssumedState) {
            this.deviatingWinnerStreak = 0;
        } else {
            this.deviatingWinnerStreak++;
        }
    }

    public boolean isBranchingEngagedThisTick(final long tick) {
        return this.lastBranchTick == tick && this.lastBranchCount > 0;
    }

    public void replaceSurvivors(final List<MovementBranch> branches) {
        this.survivors.clear();
        this.survivors.addAll(branches);
    }

    public void rebaseSurvivorsOntoAccepted(final BoarPlayer player) {
        if (this.survivors.isEmpty()) {
            return;
        }

        final SimulationData accepted = SimulationData.from(player);
        for (final MovementBranch survivor : this.survivors) {
            final SimulationData end = accepted.copy();
            end.getEntityFlags().remove(EntityFlag.SPRINTING);
            end.getEntityFlags().remove(EntityFlag.SNEAKING);

            if (survivor.isSprinting()) {
                end.getEntityFlags().add(EntityFlag.SPRINTING);
            }
            if (survivor.isSneaking()) {
                end.getEntityFlags().add(EntityFlag.SNEAKING);
            }

            final AttributeInstance movement = end.getAttributes().get(Attribute.MOVEMENT.getIdentifier());
            if (movement != null) {
                movement.setValue(survivor.getMovementSpeed());
            }

            end.setAirSpeedOverride(null);
            end.setPendingServerMovementSpeed(null);
            survivor.setEndState(end);
        }
    }
}
