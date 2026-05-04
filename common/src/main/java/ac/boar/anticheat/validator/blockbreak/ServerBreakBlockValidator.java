package ac.boar.anticheat.validator.blockbreak;

import ac.boar.anticheat.data.BreakingData;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.block.BlockUtil;
import ac.boar.anticheat.util.math.Direction;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.cloudburstmc.protocol.bedrock.data.PlayerActionType.ABORT_BREAK;
import static org.cloudburstmc.protocol.bedrock.data.PlayerActionType.BLOCK_CONTINUE_DESTROY;
import static org.cloudburstmc.protocol.bedrock.data.PlayerActionType.BLOCK_PREDICT_DESTROY;
import static org.cloudburstmc.protocol.bedrock.data.PlayerActionType.START_BREAK;

@RequiredArgsConstructor
public class ServerBreakBlockValidator {
    private final static List<PlayerActionType> ALLOWED_ACTIONS = List.of(
            START_BREAK,
            ABORT_BREAK,
            BLOCK_PREDICT_DESTROY,
            BLOCK_CONTINUE_DESTROY
    );

    private final BoarPlayer player;

    private BreakingData breakingData;

    public void handle(final PlayerAuthInputPacket packet) {
        if (!packet.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
            return;
        }

        final List<PlayerBlockActionData> validActions = new ArrayList<>();

        for (final PlayerBlockActionData action : packet.getPlayerActions()) {
            final PlayerActionType actionType = action.getAction();
            final int face = action.getFace();

            // These action are shouldn't be process, and likely won't be process by Geyser anyway.
            if (!ALLOWED_ACTIONS.contains(actionType) || action.getBlockPosition() == null || !MathUtil.isValid(action.getBlockPosition())) {
                continue;
            }

            if (actionType != ABORT_BREAK && (face < 0 || face >= Direction.VALUES.length)) {
                continue;
            }

            final Vector3i blockPosition = action.getBlockPosition();

            if (blockPosition.distance(player.position.toVector3i()) > 12) {
                BlockUtil.restoreCorrectBlock(player, blockPosition);
                continue;
            }

            final BoarBlockState state = player.compensatedWorld.getBlockState(blockPosition, 0);
            if (!BlockUtil.determineCanBreak(player, state)) {
                continue;
            }

            switch (actionType) {
                case START_BREAK, BLOCK_CONTINUE_DESTROY  -> {
                    if (this.breakingData == null) {
                        this.breakingData = new BreakingData(START_BREAK, action.getBlockPosition(), face);
//                        System.out.println("Start break: " + this.breakingData);
                    } else {
                        this.breakingData.setState(BLOCK_CONTINUE_DESTROY);
                    }

                    if (!Objects.equals(blockPosition, this.breakingData.getPosition())) {
                        BlockUtil.restoreCorrectBlock(player, this.breakingData.getPosition());

                        this.breakingData = new BreakingData(START_BREAK, action.getBlockPosition(), face);
//                        System.out.println("Bedrock moment start break: " + this.breakingData);
                    }

                    // TODO: Properly implement breaking progress.
                    this.breakingData.setBreakingProcess(1F);
                }

                case ABORT_BREAK -> this.breakingData = null;
                case BLOCK_PREDICT_DESTROY -> {
                    if (this.breakingData == null || !Objects.equals(blockPosition, this.breakingData.getPosition())) {
                        continue;
                    }

                    if (this.breakingData.getBreakingProcess() >= 1) {
//                        System.out.println("Finish break: " + this.breakingData);
                        player.compensatedWorld.updateBlock(breakingData.getPosition(), 0, player.mappingInfo.airId());
                    } else {
                        continue;
                    }
                    this.breakingData = null;
                }
                default -> throw new IllegalStateException("Unexpected value: " + action);
            }

            validActions.add(action);
        }

        packet.getPlayerActions().clear();
        packet.getPlayerActions().addAll(validActions);

        if (packet.getPlayerActions().isEmpty()) {
            packet.getInputData().remove(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
        }
    }
}
