package ac.boar.anticheat.data.block.impl;

import ac.boar.anticheat.data.block.AbstractBoarBlockState;
import ac.boar.anticheat.data.block.BoarBlockStateDelegate;
import ac.boar.anticheat.player.BoarPlayer;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

public class BedBlockState extends AbstractBoarBlockState {
    public BedBlockState(BoarBlockStateDelegate delegate) {
        super(delegate);
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
