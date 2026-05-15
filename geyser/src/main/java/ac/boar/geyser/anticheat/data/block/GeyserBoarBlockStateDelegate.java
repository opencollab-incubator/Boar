package ac.boar.geyser.anticheat.data.block;

import ac.boar.anticheat.data.block.AbstractBoarBlockState;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.block.BoarBlockStateDelegate;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Box;
import ac.boar.geyser.anticheat.util.block.GeyserBlockUtil;
import ac.boar.geyser.mappings.block.GeyserBlock;
import ac.boar.geyser.mappings.block.GeyserProperty;
import ac.boar.geyser.model.GeyserNetworkSession;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.Property;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.block.type.SkullBlock;
import org.geysermc.geyser.level.physics.Axis;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.SkullCache;
import org.geysermc.geyser.translator.collision.BlockCollision;
import org.geysermc.geyser.translator.collision.SolidCollision;
import org.geysermc.geyser.util.BlockUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Getter
public class GeyserBoarBlockStateDelegate implements BoarBlockStateDelegate {
    private final BlockState state;
    private final Vector3i position;
    private final int layer;

    public static GeyserBoarBlockStateDelegate unwrap(BoarBlockState state) {
        return (GeyserBoarBlockStateDelegate) ((AbstractBoarBlockState) state).delegate();
    }

    @Override
    public int intermediaryId() {
        return this.state.javaId();
    }

    @Override
    public Block block() {
        return new GeyserBlock(this.state.block());
    }

    @Override
    public BlockDefinition definition(BoarPlayer player) {
        GeyserSession session = ((GeyserNetworkSession) player.getSession()).session();
        if (this.state.block() instanceof SkullBlock skullBlock && skullBlock.skullType() == SkullBlock.Type.PLAYER) {
            SkullCache.Skull skull = session.getSkullCache().getSkulls().get(this.position);
            if (skull != null && skull.getBlockDefinition() != null) {
                return skull.getBlockDefinition();
            }
        }

        return session.getBlockMappings().getBedrockBlock(this.state);
    }

    @Override
    public <T extends Comparable<T>> BoarBlockState with(Property<T> property, T value) {
        BlockState newState = this.state.withValue(((GeyserProperty<T>) property).handle(), value);
        return BoarBlockState.create(newState.javaId(), this.position, this.layer);
    }

    @Override
    public boolean isWaterlogged() {
        return BlockRegistries.WATERLOGGED.get().get(this.state.javaId());
    }

    @Override
    public boolean is(Block block) {
        return this.state.is(((GeyserBlock) block).handle());
    }

    @Override
    public <T extends Comparable<T>> T get(Property<T> property) {
        return this.state.getValue(((GeyserProperty<T>) property).handle());
    }

    @Override
    public List<Box> getCollisionBoxes() {
        // Geyser returns null for blocks it has no collision data for (unmapped/unknown block IDs).
        // Treat as a non-collidable block (like air) instead of NPEing the prediction tick — that
        // failure historically blackholed doPostPrediction, freezing player.position at spawn.
        BlockCollision collision = BlockUtils.getCollision(state.javaId());
        if (collision == null) {
            return Collections.emptyList();
        }

        List<Box> collisions = new ArrayList<>();
        for (BoundingBox boundingBox : collision.getBoundingBoxes()) {
            collisions.add(new Box(
                    (float) boundingBox.getMin(Axis.X),
                    (float) boundingBox.getMin(Axis.Y),
                    (float) boundingBox.getMin(Axis.Z),
                    (float) boundingBox.getMax(Axis.X),
                    (float) boundingBox.getMax(Axis.Y),
                    (float) boundingBox.getMax(Axis.Z)
            ));
        }
        return collisions;
    }

    @Override
    public boolean isFaceSturdy(BoarPlayer player) {
        return BlockUtils.getCollision(state.javaId()) instanceof SolidCollision;
    }

    @Override
    public BoarBlockState applyConnectionShape(BoarPlayer player, BoarBlockState self, Vector3i pos) {
        BlockState newState;
        if (BlockMappings.get().getFenceBlocks().contains(this.block())) {
            newState = GeyserBlockUtil.findFenceBlockState(player, this.state, pos);
        } else if (this.state.is(Blocks.IRON_BARS) || this.state.toString().toLowerCase(Locale.ROOT).contains("glass_pane")) {
            newState = GeyserBlockUtil.findIronBarsBlockState(player, this.state, pos);
        } else if (this.state.is(Blocks.CHEST) || this.state.is(Blocks.TRAPPED_CHEST)) {
            newState = GeyserBlockUtil.findChestState(player, this.state, pos);
        } else if (BlockMappings.get().getStairsBlocks().contains(this.block())) {
            newState = this.state.withValue(Properties.STAIRS_SHAPE, GeyserBlockUtil.getStairShape(player, this.state, pos));
        } else {
            return self;
        }

        if (newState.javaId() == this.state.javaId()) {
            return self;
        }
        return BoarBlockState.create(newState.javaId(), this.position, this.layer);
    }
}
