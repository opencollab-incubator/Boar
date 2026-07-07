package ac.boar.anticheat.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.vanilla.Attribute;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.data.input.PredictionResult;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.SimulationData;
import ac.boar.anticheat.prediction.branch.BranchEnumerator;
import ac.boar.anticheat.prediction.branch.BranchTracker;
import ac.boar.anticheat.prediction.branch.MovementBranch;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.prediction.ticker.impl.PlayerTicker;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public class PredictionRunner {
    private final BoarPlayer player;

    public void run() {
        if (!this.findBestTickStartVelocity()) {
            return;
        }

        final BranchTracker branchTracker = player.getBranchTracker();
        branchTracker.recordSprintContext(player.tick, player.sprintContext);
        branchTracker.recordSneakContext(player.tick, player.sneakContext);

        if (MovementDebug.enabled()) {
            MovementDebug.log(player, "TICK-START", "startVel=" + MovementDebug.vec(player.velocity)
                    + " velType=" + player.bestPossibility.getType()
                    + " pos=" + MovementDebug.vec(player.position)
                    + " unvalidatedPos=" + MovementDebug.vec(player.unvalidatedPosition)
                    + " prevPos=" + MovementDebug.vec(player.prevPosition)
                    + " yaw=" + player.yaw + " pitch=" + player.pitch
                    + " input=" + MovementDebug.vec(player.input)
                    + " speed=" + player.getSpeed()
                    + " sprintUncertain=" + (player.sprintContext != null && player.sprintContext.uncertain())
                    + " sneakUncertain=" + (player.sneakContext != null && player.sneakContext.uncertain())
                    + " speedCandidates=" + this.speedCandidateDebug()
                    + " | " + MovementDebug.flags(player)
                    + " | inputData=" + player.getInputData());
        }

        if (!BranchEnumerator.BRANCHED_MOVEMENT_ENABLED) {
            branchTracker.resetAll();
            this.simulate();
            return;
        }

        final BranchTracker.Dormancy dormancy = this.dormancyKind(branchTracker);
        if (dormancy != BranchTracker.Dormancy.NONE) {
            branchTracker.enterDormancy(dormancy);
            this.simulate();
            return;
        }

        if (this.canUseFastPath()) {
            branchTracker.clearLastSelectionDebug();
            this.simulate();
            return;
        }

        this.runBranched(branchTracker);
    }

    private BranchTracker.Dormancy dormancyKind(final BranchTracker branchTracker) {
        if (!this.sneakStateCertain(branchTracker)) {
            return BranchTracker.Dormancy.NONE;
        }

        final boolean sprintUncertainThisTick = player.sprintContext != null && player.sprintContext.uncertain();
        if (sprintUncertainThisTick) {
            return BranchTracker.Dormancy.NONE;
        }

        final boolean sprintDimensionOpen = branchTracker.isSprintDormant() ||
                branchTracker.wasSprintUncertainLastTick(player.tick) ||
                branchTracker.getSurvivors().stream().anyMatch(MovementBranch::isSprintAmbiguousLineage);
        if (sprintDimensionOpen) {
            if (this.canTickDiscriminateSprint()) {
                return BranchTracker.Dormancy.NONE;
            }
            return this.survivorSpeedsRederivable(branchTracker) ? BranchTracker.Dormancy.SPRINT : BranchTracker.Dormancy.NONE;
        }

        final boolean speedDimensionOpen = branchTracker.isSpeedDormant() || !branchTracker.getSurvivors().isEmpty();
        if (!speedDimensionOpen || this.canTickDiscriminateSpeed()) {
            return BranchTracker.Dormancy.NONE;
        }
        return this.survivorSpeedsRederivable(branchTracker) ? BranchTracker.Dormancy.SPEED : BranchTracker.Dormancy.NONE;
    }

    private boolean survivorSpeedsRederivable(final BranchTracker branchTracker) {
        final Float withoutAdditions = player.movementSpeedWithoutAdditions();
        return branchTracker.getSurvivors().stream().allMatch(survivor ->
                Float.floatToIntBits(survivor.getMovementSpeed()) == Float.floatToIntBits(player.deriveMovementSpeed(true))
                        || Float.floatToIntBits(survivor.getMovementSpeed()) == Float.floatToIntBits(player.deriveMovementSpeed(false))
                        || Float.floatToIntBits(survivor.getMovementSpeed()) == Float.floatToIntBits(player.movementAdditionModifierSum())
                        || (withoutAdditions != null && Float.floatToIntBits(survivor.getMovementSpeed()) == Float.floatToIntBits(withoutAdditions)));
    }

    private boolean canTickDiscriminateSprint() {
        return player.input.lengthSquared() > 0
                || player.getInputData().contains(PlayerAuthInputData.START_JUMPING)
                || player.touchingWater
                || player.isInLava();
    }

    private boolean sprintStateCertain(final BranchTracker branchTracker) {
        if (player.sprintContext != null && player.sprintContext.uncertain()) {
            return false;
        }
        return !branchTracker.wasSprintUncertainLastTick(player.tick)
                && !branchTracker.isSprintDormant();
    }

    private boolean sneakStateCertain(final BranchTracker branchTracker) {
        if (player.sneakContext != null && player.sneakContext.uncertain()) {
            return false;
        }
        return !branchTracker.wasSneakUncertainLastTick(player.tick);
    }

    private boolean canTickDiscriminateSpeed() {
        if (player.input.lengthSquared() <= 0) {
            return false;
        }
        return player.getInputData().contains(PlayerAuthInputData.START_JUMPING) || player.onGround || player.touchingWater || player.isInLava();
    }

    public SimulationData runBranch(final SimulationData branchStartState) {
        final SimulationData canonical = SimulationData.from(player);
        return this.runBranch(branchStartState, canonical);
    }

    public SimulationData runBranch(final SimulationData branchStartState, final SimulationData canonical) {
        branchStartState.apply(player);
        this.simulate();
        final SimulationData result = SimulationData.from(player);

        canonical.apply(player);
        return result;
    }

    private void runBranched(final BranchTracker branchTracker) {
        final SimulationData canonical = SimulationData.from(player);
        final List<MovementBranch> seeds = this.buildSeeds(canonical, branchTracker);
        final BranchEnumerator.EnumerationResult enumeration = BranchEnumerator.enumerate(player, seeds, BranchEnumerator.MAX_BRANCHES);
        final List<MovementBranch> branches = enumeration.branches();

        final long simStart = System.nanoTime();
        for (int i = 0; i < branches.size(); i++) {
            final MovementBranch branch = branches.get(i);
            branch.setEndState(this.runBranch(branch.getStartState(), canonical));
            branch.setOffset(branch.getEndState().getPosition().distanceTo(player.unvalidatedPosition));
            branch.setWithinAcceptanceThreshold(branch.getOffset() < this.acceptanceThreshold(branch.getEndState().getPosition()));
            MovementDebug.branch(player, i, branch);
        }
        final long simNanos = System.nanoTime() - simStart;

        final AttributeInstance canonicalMovement = canonical.getAttributes().get(Attribute.MOVEMENT.getIdentifier());
        final float canonicalMovementSpeed = canonicalMovement == null ? 0.0F : canonicalMovement.getValue();
        final MovementBranch winner = branches.stream()
                .min(BranchEnumerator.selectionOrder(player, canonicalMovementSpeed))
                .orElseThrow();
        final boolean winnerMatchesAssumedState = BranchEnumerator.deviationsFromAssumedState(player, winner) == 0;
        final List<MovementBranch> survivors = this.selectSurvivors(branches, winner, canonicalMovementSpeed);

        if (this.sprintDimensionRefuted(branches, winner, enumeration)) {
            branchTracker.clearDormancy();
            for (final MovementBranch branch : branches) {
                branch.setSprintAmbiguousLineage(false);
            }
        }

        final float offsetSpread = (float) (branches.stream().mapToDouble(MovementBranch::getOffset).max().orElse(0) - branches.stream().mapToDouble(MovementBranch::getOffset).min().orElse(0));
        if (offsetSpread > TIE_EPSILON && this.canTickDiscriminateSpeed()) {
            branchTracker.clearDormancy();
        }
        branchTracker.replaceSurvivors(survivors);
        branchTracker.recordSelection(player.tick, branches.size(), winner, winnerMatchesAssumedState);

        MovementDebug.log(player, "BRANCH-SELECT", "count=" + branches.size()
                + " duplicatesRemoved=" + enumeration.duplicatesRemoved()
                + " winner={" + winner.describe() + "}"
                + " survivors=" + survivors.size()
                + " simNanos=" + simNanos
                + " sprintCertain=" + this.sprintStateCertain(branchTracker)
                + " sneakCertain=" + this.sneakStateCertain(branchTracker)
                + " deviatingWinnerStreak=" + branchTracker.getDeviatingWinnerStreak()
                + " forcedAmbiguityTicks=" + branchTracker.getForcedAmbiguityTickCount());

        this.sendBranchDebugMessage(branches, simNanos);
        this.sendConvergenceDebugMessage(branches, winner, survivors, branchTracker);

        winner.getEndState().apply(player);
        player.airSpeedOverride = null;
    }

    private void sendBranchDebugMessage(final List<MovementBranch> branches, final long simNanos) {
        final StringBuilder message = new StringBuilder("§eBranched movement! simTick=").append(player.tick);
        for (int i = 0; i < branches.size(); i++) {
            final MovementBranch branch = branches.get(i);
            final Vec3 diff = branch.getEndState().getPosition().subtract(player.unvalidatedPosition);
            message.append("\n§e[").append(i).append("] ").append(branch.shortDescribe())
                    .append(" diff=[").append(String.format(Locale.ROOT, "%.4f", diff.x))
                    .append(", ").append(String.format(Locale.ROOT, "%.4f", diff.y))
                    .append(", ").append(String.format(Locale.ROOT, "%.4f", diff.z)).append(']');
        }
        message.append('\n').append(String.format(Locale.ROOT, "§e%d branches took %.4fms to simulate",
                branches.size(), simNanos / 1_000_000.0));
        player.getSession().sendMessage(message.toString());
    }

    private void sendConvergenceDebugMessage(final List<MovementBranch> branches, final MovementBranch winner, final List<MovementBranch> survivors, final BranchTracker branchTracker) {
        if (branches.size() <= 1 || !winner.isWithinAcceptanceThreshold() || !survivors.isEmpty() || branchTracker.isSprintDormant()) {
            return;
        }
        player.getSession().sendMessage("§6Branch convergence! simTick=" + player.tick
                + " selected=" + winner.shortDescribe()
                + " off=" + String.format(Locale.ROOT, "%.5f", winner.getOffset())
                + " eliminated=" + (branches.size() - 1));
    }

    private List<MovementBranch> buildSeeds(final SimulationData canonical, final BranchTracker branchTracker) {
        final List<MovementBranch> seeds = new ArrayList<>(1 + branchTracker.getSurvivors().size());
        final boolean sprintCertain = this.sprintStateCertain(branchTracker);
        final boolean canonicalSprintAmbiguousLineage = branchTracker.wasSprintUncertainLastTick(player.tick)
                || branchTracker.isSprintDormant()
                || (!sprintCertain && branchTracker.getSurvivors().stream().anyMatch(MovementBranch::isSprintAmbiguousLineage));
        final boolean sneakCertain = this.sneakStateCertain(branchTracker);
        final boolean canonicalSneakAmbiguousLineage = branchTracker.wasSneakUncertainLastTick(player.tick)
                || (!sneakCertain && branchTracker.getSurvivors().stream().anyMatch(MovementBranch::isSneakAmbiguousLineage));
        seeds.add(this.seed(canonical, 0, 0, true, canonicalSprintAmbiguousLineage, canonicalSneakAmbiguousLineage, branchTracker.isSpeedDormant()));

        int seedId = 1;
        for (final MovementBranch survivor : branchTracker.getSurvivors()) {
            if (survivor.getEndState() == null) {
                continue;
            }
            final SimulationData seedData = survivor.getEndState().copy();
            this.refreshSeedFromCanonical(seedData, canonical);
            seeds.add(this.seed(
                    seedData,
                    seedId++,
                    survivor.getLineageAge(),
                    false,
                    !sprintCertain && survivor.isSprintAmbiguousLineage(),
                    !sneakCertain && survivor.isSneakAmbiguousLineage(),
                    survivor.isSpeedAmbiguousLineage()
            ));
        }
        return seeds;
    }

    private MovementBranch seed(
            final SimulationData data,
            final int seedId, final int lineageAge,
            final boolean canonicalLineage,
            final boolean sprintAmbiguousLineage,
            final boolean sneakAmbiguousLineage,
            final boolean speedAmbiguousLineage
    ) {
        final MovementBranch seed = new MovementBranch();
        seed.setSeedState(data);
        seed.setSeedId(seedId);
        seed.setLineageAge(lineageAge);
        seed.setSneakAmbiguousLineage(sneakAmbiguousLineage);
        seed.setCanonicalLineage(canonicalLineage);
        seed.setSprintAmbiguousLineage(sprintAmbiguousLineage);
        seed.setSpeedAmbiguousLineage(speedAmbiguousLineage);
        return seed;
    }

    private void refreshSeedFromCanonical(final SimulationData seed, final SimulationData canonical) {
        final AttributeInstance seedMovement = seed.getAttributes().get(Attribute.MOVEMENT.getIdentifier());
        seed.setAttributes(this.copyAttributes(canonical.getAttributes()));
        final AttributeInstance overlaidMovement = seed.getAttributes().get(Attribute.MOVEMENT.getIdentifier());
        if (seedMovement != null && overlaidMovement != null) {
            overlaidMovement.setValue(seedMovement.getValue());
        }
        seed.setInputData(new HashSet<>(canonical.getInputData()));
        seed.setInput(canonical.getInput().clone());
        seed.setBestPossibility(this.copyOf(canonical.getBestPossibility()));
        seed.setVelocity(seed.getBestPossibility().getVelocity());
        seed.setVelocityAliasedToBestPossibility(true);
        seed.setPendingServerMovementSpeed(canonical.getPendingServerMovementSpeed());
        seed.setSprintContext(canonical.getSprintContext());
        seed.setSneakContext(canonical.getSneakContext());
        seed.setServerSprinting(canonical.isServerSprinting());
        seed.setServerSprintingApplied(canonical.isServerSprintingApplied());
        seed.setServerSneaking(canonical.isServerSneaking());
        seed.setServerSneakingApplied(canonical.isServerSneakingApplied());
        seed.setServerUpdatedMovementSpeed(canonical.isServerUpdatedMovementSpeed());
    }

    private Map<String, AttributeInstance> copyAttributes(final Map<String, AttributeInstance> attributes) {
        final Map<String, AttributeInstance> copy = new HashMap<>(attributes.size());
        for (final Map.Entry<String, AttributeInstance> entry : attributes.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    private Vector copyOf(final Vector vector) {
        return new Vector(vector.getType(), vector.getVelocity().clone());
    }

    private static final float TIE_EPSILON = 1.0E-6F;

    private List<MovementBranch> selectSurvivors(final List<MovementBranch> branches, final MovementBranch winner, final float canonicalMovementSpeed) {
        if (!winner.isWithinAcceptanceThreshold()) {
            return List.of();
        }
        final boolean sprintCertain = this.sprintStateCertain(player.getBranchTracker());
        final boolean sneakCertain = this.sneakStateCertain(player.getBranchTracker());
        final List<MovementBranch> kept = branches.stream()
                .filter(branch -> branch != winner)
                .filter(MovementBranch::isWithinAcceptanceThreshold)
                .filter(branch -> !sprintCertain || branch.isSprinting() == winner.isSprinting())
                .filter(branch -> !sneakCertain || branch.isSneaking() == winner.isSneaking())
                .filter(branch -> branch.getLineageAge() == 0 || branch.getOffset() <= winner.getOffset() + TIE_EPSILON)
                .sorted(BranchEnumerator.selectionOrder(player, canonicalMovementSpeed))
                .limit(BranchEnumerator.MAX_SURVIVORS)
                .toList();

        for (final MovementBranch survivor : kept) {
            if (survivor.getOffset() <= winner.getOffset() + TIE_EPSILON) {
                survivor.setLineageAge(0);
            }
        }
        return kept;
    }

    private boolean sprintDimensionRefuted(final List<MovementBranch> branches, final MovementBranch winner, final BranchEnumerator.EnumerationResult enumeration) {
        if (!winner.isWithinAcceptanceThreshold()) {
            return false;
        }
        if (enumeration.rawCount() - enumeration.duplicatesRemoved() != branches.size()) {
            return false;
        }

        boolean pureCounterfactualRefuted = false;
        for (final MovementBranch branch : branches) {
            if (branch.isSprinting() == winner.isSprinting()) {
                continue;
            }
            if (branch.isWithinAcceptanceThreshold()) {
                return false;
            }
            if (branch.isSneaking() == winner.isSneaking() && Float.floatToIntBits(branch.getMovementSpeed()) == Float.floatToIntBits(winner.getMovementSpeed())) {
                pureCounterfactualRefuted = true;
            }
        }
        return pureCounterfactualRefuted;
    }

    private float acceptanceThreshold(final Vec3 predictedPosition) {
        final float ulpX = Math.ulp(Math.max(Math.abs(predictedPosition.x), Math.abs(player.unvalidatedPosition.x)));
        final float ulpY = Math.ulp(Math.max(Math.abs(predictedPosition.y), Math.abs(player.unvalidatedPosition.y)));
        final float ulpZ = Math.ulp(Math.max(Math.abs(predictedPosition.z), Math.abs(player.unvalidatedPosition.z)));
        final float precision = (float) Math.sqrt(ulpX * ulpX + ulpY * ulpY + ulpZ * ulpZ) * 2.0F;
        return Math.max(player.getMaxOffset(), precision);
    }

    private boolean canUseFastPath() {
        if (!player.getBranchTracker().getSurvivors().isEmpty()) {
            return false;
        }
        if (player.sprintContext != null && player.sprintContext.uncertain()) {
            return false;
        }
        if (player.getBranchTracker().wasSprintUncertainLastTick(player.tick)) {
            return false;
        }
        if (player.sneakContext != null && player.sneakContext.uncertain()) {
            return false;
        }
        if (player.getBranchTracker().wasSneakUncertainLastTick(player.tick)) {
            return false;
        }
        if (player.getBranchTracker().getDormancy() != BranchTracker.Dormancy.NONE) {
            return false;
        }
        if (player.pendingServerMovementSpeed != null) {
            final boolean sprinting = player.getFlagTracker().has(EntityFlag.SPRINTING);
            return Float.floatToIntBits(player.pendingServerMovementSpeed) == Float.floatToIntBits(player.deriveMovementSpeed(sprinting));
        }
        return true;
    }

    private String speedCandidateDebug() {
        final LinkedHashSet<Float> candidates = new LinkedHashSet<>();
        if (player.sprintContext != null && player.sprintContext.uncertain()) {
            candidates.add(player.deriveMovementSpeed(player.sprintContext.assumedSprinting()));
            candidates.add(player.deriveMovementSpeed(!player.sprintContext.assumedSprinting()));
            final Float withoutAdditions = player.movementSpeedWithoutAdditions();
            if (withoutAdditions != null) {
                candidates.add(withoutAdditions);
            }
            final float additionSum = player.movementAdditionModifierSum();
            if (additionSum > 0.0F) {
                candidates.add(additionSum);
            }
        } else {
            candidates.add(player.deriveMovementSpeed(player.getFlagTracker().has(EntityFlag.SPRINTING)));
        }
        if (player.pendingServerMovementSpeed != null) {
            candidates.add(player.pendingServerMovementSpeed);
        }
        return candidates.toString();
    }

    private void simulate() {
        new PlayerTicker(player).tick();
        player.predictionResult = new PredictionResult(player.beforeCollision.clone(), player.afterCollision.clone(), player.velocity.clone());
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
