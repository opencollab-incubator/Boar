package ac.boar.mappings.block;

public interface PropertyProvider {

    <T extends Comparable<T>> Property<T> get(String key);
}
