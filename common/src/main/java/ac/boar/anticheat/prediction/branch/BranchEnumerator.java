package ac.boar.anticheat.prediction.branch;

import ac.boar.anticheat.data.vanilla.Attribute;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.player.data.PlayerData;
import ac.boar.anticheat.player.data.SimulationData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeOperation;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BranchEnumerator {

    public static final boolean BRANCHED_MOVEMENT_ENABLED = true;
    public static final int MAX_BRANCHES = 16;
    public static final int MAX_SURVIVORS = 3;

    private BranchEnumerator() {
    }

    public record EnumerationResult(List<MovementBranch> branches, int rawCount, int duplicatesRemoved) {
    }

    public static EnumerationResult enumerate(final PlayerData player, final List<MovementBranch> seeds, final int maxBranches) {
        final PlayerData.SprintContext sprintContext = player.sprintContext;
        final boolean sprintUncertain = sprintContext != null && sprintContext.uncertain();
        final boolean assumedSprinting = sprintContext != null
                ? sprintContext.assumedSprinting()
                : player.getFlagTracker().has(EntityFlag.SPRINTING);
        final boolean sneakUncertain = player.sneakContext != null && player.sneakContext.uncertain();
        final boolean assumedSneaking = assumedSneaking(player);

        final Map<String, MovementBranch> deduped = new LinkedHashMap<>();
        final Set<String> requiredKeys = new LinkedHashSet<>();
        int rawCount = 0;

        for (final MovementBranch seed : seeds) {
            final SimulationData seedData = seed.seedState();
            final boolean seedSprinting = seedData.getEntityFlags().contains(EntityFlag.SPRINTING);
            final boolean seedSneaking = seedData.getEntityFlags().contains(EntityFlag.SNEAKING);
            final float seedMovementSpeed = movementSpeed(seedData);

            final boolean sprintDimensionOpen = sprintUncertain || seed.isSprintAmbiguousLineage();
            final boolean[] sprintCandidates = sprintDimensionOpen
                    ? new boolean[]{assumedSprinting, !assumedSprinting}
                    : new boolean[]{seedSprinting};

            final boolean sneakDimensionOpen = sneakUncertain || seed.isSneakAmbiguousLineage();
            final boolean[] sneakCandidates = sneakDimensionOpen
                    ? new boolean[]{assumedSneaking, !assumedSneaking}
                    : new boolean[]{seedSneaking};

            final boolean speedDimensionOpen = sprintDimensionOpen || seed.isSpeedAmbiguousLineage();
            for (final boolean sprint : sprintCandidates) {
                final List<Float> speedCandidates = speedCandidates(player, sprint, speedDimensionOpen);
                addUnique(speedCandidates, seedMovementSpeed);

                for (final boolean sneak : sneakCandidates) {
                    for (final float speed : speedCandidates) {
                        final MovementBranch branch = new MovementBranch();
                        final SimulationData start = seedData.copy();
                        start.getEntityFlags().remove(EntityFlag.SPRINTING);
                        if (sprint) {
                            start.getEntityFlags().add(EntityFlag.SPRINTING);
                        }
                        start.getEntityFlags().remove(EntityFlag.SNEAKING);
                        if (sneak) {
                            start.getEntityFlags().add(EntityFlag.SNEAKING);
                        }
                        final AttributeInstance attribute = start.getAttributes().get(Attribute.MOVEMENT.getIdentifier());
                        if (attribute != null) {
                            attribute.setValue(speed);
                        }

                        branch.setStartState(start);
                        branch.setSprinting(sprint);
                        branch.setSneaking(sneak);
                        branch.setMovementSpeed(speed);
                        branch.setSeedId(seed.getSeedId());
                        branch.setLineageAge(seed.isCanonicalLineage() ? 0 : seed.getLineageAge() + 1);
                        branch.setCanonicalLineage(seed.isCanonicalLineage());
                        branch.setSprintAmbiguousLineage(sprintUncertain || seed.isSprintAmbiguousLineage());
                        branch.setSneakAmbiguousLineage(sneakUncertain || seed.isSneakAmbiguousLineage());
                        branch.setSpeedAmbiguousLineage(seed.isSpeedAmbiguousLineage());

                        rawCount++;
                        final String key = branch.dedupeKey();
                        deduped.putIfAbsent(key, branch);
                        if (isCanonicalAssumedBranch(player, branch, assumedSprinting, assumedSneaking)
                                || isSurvivorContinuation(seed, branch, seedSprinting, seedSneaking, seedMovementSpeed)) {
                            requiredKeys.add(key);
                        }
                    }
                }
            }
        }

        final List<MovementBranch> branches = capToMaxBranches(player, new ArrayList<>(deduped.values()), requiredKeys, maxBranches);
        return new EnumerationResult(branches, rawCount, rawCount - deduped.size());
    }

    public static boolean assumedSneaking(final PlayerData player) {
        return player.sneakContext != null
                ? player.sneakContext.assumedSneaking()
                : player.getFlagTracker().has(EntityFlag.SNEAKING);
    }

    public static Comparator<MovementBranch> selectionOrder(final PlayerData player, final float canonicalMovementSpeed) {
        return Comparator.comparingDouble(MovementBranch::getOffset)
                .thenComparingInt(branch -> branch.isCanonicalLineage() ? 0 : 1)
                .thenComparingInt(branch -> Float.floatToIntBits(branch.getMovementSpeed()) == Float.floatToIntBits(canonicalMovementSpeed) ? 0 : 1)
                .thenComparingInt(branch -> deviationsFromAssumedState(player, branch))
                .thenComparingInt(MovementBranch::getLineageAge);
    }

    public static int deviationsFromAssumedState(final PlayerData player, final MovementBranch branch) {
        final PlayerData.SprintContext sprintContext = player.sprintContext;
        final boolean assumedSprinting = sprintContext != null
                ? sprintContext.assumedSprinting()
                : player.getFlagTracker().has(EntityFlag.SPRINTING);
        int deviations = branch.isSprinting() == assumedSprinting ? 0 : 1;
        if (branch.isSneaking() != assumedSneaking(player)) {
            deviations++;
        }
        if (Float.floatToIntBits(branch.getMovementSpeed()) != Float.floatToIntBits(player.deriveMovementSpeed(branch.isSprinting()))) {
            deviations++;
        }
        return deviations;
    }

    private static List<Float> speedCandidates(final PlayerData player, final boolean sprint, final boolean speedDimensionOpen) {
        final List<Float> candidates = new ArrayList<>(2);
        candidates.add(player.deriveMovementSpeed(sprint));
        if (player.pendingServerMovementSpeed != null) {
            addUnique(candidates, player.pendingServerMovementSpeed);
        }

        final PlayerData.SprintContext sprintContext = player.sprintContext;
        if (sprintContext != null && sprintContext.uncertain() && sprintContext.serverSpeedUpdateActive()) {
            addUnique(candidates, sprintContext.preUpdateMovementSpeed());
        }

        if (speedDimensionOpen) {
            addUnique(candidates, player.deriveMovementSpeed(!sprint));

            final AttributeInstance movAttr = player.attributes.get(Attribute.MOVEMENT.getIdentifier());
            if (movAttr != null) {
                float addModSum = 0.0F;
                for (final AttributeModifierData modifier : movAttr.getModifiers().values()) {
                    if (modifier.getOperation() == AttributeOperation.ADDITION) {
                        addModSum += modifier.getAmount();
                    }
                }

                if (addModSum > 0.0F) {
                    addUnique(candidates, addModSum);
                    addUnique(candidates, movAttr.getNonModifiedBaseValue());
                }
            }
        }
        return candidates;
    }

    private static void addUnique(final List<Float> candidates, final Float value) {
        for (final Float candidate : candidates) {
            if (Objects.equals(candidate, value)) {
                return;
            }
            if (candidate != null && value != null && Float.floatToIntBits(candidate) == Float.floatToIntBits(value)) {
                return;
            }
        }
        candidates.add(value);
    }

    private static float movementSpeed(final SimulationData data) {
        final AttributeInstance attribute = data.getAttributes().get(Attribute.MOVEMENT.getIdentifier());
        return attribute == null ? 0.0F : attribute.getValue();
    }

    private static boolean isCanonicalAssumedBranch(final PlayerData player, final MovementBranch branch, final boolean assumedSprinting, final boolean assumedSneaking) {
        return branch.isCanonicalLineage()
                && branch.isSprinting() == assumedSprinting
                && branch.isSneaking() == assumedSneaking
                && Float.floatToIntBits(branch.getMovementSpeed()) == Float.floatToIntBits(player.deriveMovementSpeed(branch.isSprinting()));
    }

    private static boolean isSurvivorContinuation(
            final MovementBranch seed,
            final MovementBranch branch,
            final boolean seedSprinting,
            final boolean seedSneaking,
            final float seedMovementSpeed
    ) {
        return !seed.isCanonicalLineage()
                && branch.isSprinting() == seedSprinting
                && branch.isSneaking() == seedSneaking
                && Float.floatToIntBits(branch.getMovementSpeed()) == Float.floatToIntBits(seedMovementSpeed);
    }

    private static List<MovementBranch> capToMaxBranches(final PlayerData player, final List<MovementBranch> branches, final Set<String> requiredKeys, final int maxBranches) {
        if (branches.size() <= maxBranches) {
            return branches;
        }

        final List<MovementBranch> required = branches.stream()
                .filter(branch -> requiredKeys.contains(branch.dedupeKey()))
                .toList();
        final List<MovementBranch> optional = branches.stream()
                .filter(branch -> !requiredKeys.contains(branch.dedupeKey()))
                .sorted(Comparator.comparingInt((MovementBranch branch) -> deviationsFromAssumedState(player, branch))
                        .thenComparingInt(branch -> branch.isCanonicalLineage() ? 0 : 1)
                        .thenComparingInt(MovementBranch::getLineageAge))
                .toList();

        final List<MovementBranch> result = new ArrayList<>(Math.min(maxBranches, branches.size()));
        result.addAll(required);
        for (final MovementBranch branch : optional) {
            if (result.size() >= maxBranches) {
                break;
            }
            result.add(branch);
        }
        return result;
    }
}
