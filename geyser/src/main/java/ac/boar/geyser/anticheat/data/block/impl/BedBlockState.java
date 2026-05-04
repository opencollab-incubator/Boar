package ac.boar.geyser.anticheat.data.block.impl;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.geyser.anticheat.data.block.GeyserBoarBlockState;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.level.block.type.BlockState;

public class BedBlockState extends GeyserBoarBlockState {
    public BedBlockState(BlockState state, Vector3i position, int layer) {
        super(state, position, layer);
    }

    @Override
    public void updateEntityMovementAfterFallOn(BoarPlayer player, boolean living) {
        if (player.velocity.y < 0 && !player.getFlagTracker().has(EntityFlag.SNEAKING)) {
            final float d = living ? 1.0F : 0.8F;
            player.velocity.y = -player.velocity.y * 0.75F * d;
            if (player.velocity.y > 0.75) {
                player.velocity.y = 0.75F;
            }
        } else {
            player.velocity.y = 0;
        }
    }
}
