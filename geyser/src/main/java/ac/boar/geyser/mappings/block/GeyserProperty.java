package ac.boar.geyser.mappings.block;

import ac.boar.mappings.block.Property;

public record GeyserProperty<T extends Comparable<T>>(org.geysermc.geyser.level.block.property.Property<T> handle) implements Property<T> {
}
