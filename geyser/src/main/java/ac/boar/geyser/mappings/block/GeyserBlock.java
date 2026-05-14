package ac.boar.geyser.mappings.block;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.util.Reference;
import ac.boar.mappings.block.Block;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.Objects;
import java.util.Optional;

public record GeyserBlock(org.geysermc.geyser.level.block.type.Block handle) implements Block {

    @Override
    public boolean is(Block block) {
        return ((GeyserBlock) block).handle().javaId() == this.handle.javaId();
    }

    @Override
    public boolean is(Reference<Block> reference) {
        Optional<Block> opt = reference.find();
        return opt.isPresent() ? is(opt.get()) : false;
    }

    @Override
    public float destroyTime() {
        return this.handle.destroyTime();
    }

    @Override
    public BoarBlockState defaultBlockState(Vector3i position, int layer) {
        return BoarBlockState.create(this.handle.defaultBlockState().javaId(), position, layer);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GeyserBlock that = (GeyserBlock) o;
        return Objects.equals(this.handle, that.handle);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.handle);
    }
}
