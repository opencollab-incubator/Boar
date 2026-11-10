package ac.boar.geyser.mappings.block;

import ac.boar.mappings.block.Property;

import java.util.function.Function;

/**
 * Wraps a real Geyser {@link org.geysermc.geyser.level.block.property.Property} singleton. The handle MUST be the
 * registry singleton, because Geyser's {@code BlockState#get} matches properties by instance identity ({@code ==});
 * wrapping it in a fresh anonymous Property makes every lookup return null. When Boar's value type differs from
 * Geyser's (Direction, ChestType), {@code converter} translates the raw Geyser value on read, and {@code reverter}
 * translates a Boar value back to the Geyser value on write ({@code with}).
 */
public record GeyserProperty<T extends Comparable<T>>(
        org.geysermc.geyser.level.block.property.Property<?> handle,
        Function<Object, T> converter,
        Function<T, Object> reverter) implements Property<T> {

    public GeyserProperty(org.geysermc.geyser.level.block.property.Property<T> handle) {
        this(handle, null, null);
    }
}
