package ac.boar.geyser.anticheat.data.block.impl;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.geyser.anticheat.data.block.GeyserBoarBlockState;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.level.block.type.BlockState;

public class HoneyBlockState extends GeyserBoarBlockState {
    public HoneyBlockState(BlockState state, Vector3i position, int layer) {
        super(state, position, layer);
    }

    @Override
    public void onSteppedOn(BoarPlayer player, Vector3i vector3i) {
        float d = Math.abs(player.velocity.y);
        if (d < 0.1 && !player.getFlagTracker().has(EntityFlag.SNEAKING)) {
            float e = 0.4F + d * 0.2F;
            player.velocity = player.velocity.multiply(e, 1, e);
        }
    }

    @Override
    public void entityInside(BoarPlayer player, Mutable pos) {
        player.velocity = player.velocity.multiply(0.40000001F, 1, 0.40000001F);
        player.velocity.y = Math.max(-0.12F, player.velocity.y);
    }
}
