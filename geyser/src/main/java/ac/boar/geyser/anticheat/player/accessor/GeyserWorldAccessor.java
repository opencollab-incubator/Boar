package ac.boar.geyser.anticheat.player.accessor;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.accessor.WorldAccessor;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.session.GeyserSession;

public record GeyserWorldAccessor(GeyserSession session) implements WorldAccessor {

    @Override
    public BoarBlockState blockStateAt(Vector3i position, int layer) {
        return BoarBlockState.create(this.session.getGeyser().getWorldManager().getBlockAt(this.session, position), position, layer);
    }

    @Override
    public boolean isItemFrameAt(Vector3i position) {
        return ItemFrameEntity.getItemFrameEntity(this.session, position) != null;
    }
}
