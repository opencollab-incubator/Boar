package ac.boar.anticheat.util;

public interface Referenced<T> {

    boolean is(T value);

    boolean is(Reference<T> reference);
}
