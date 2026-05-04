package ac.boar.anticheat.util;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

public interface Reference<T> {

    Optional<T> find();

    static <T> Reference<T> direct(@NotNull T value) {
        return new Direct<>(value);
    }

    static <T> Reference<T> lazy(@NotNull Supplier<T> value) {
        return new Lazy<>(value);
    }

    static <T> Reference<T> deferred(@NotNull String key) {
        return new Deferred<>(key);
    }

    record Direct<T>(@NotNull T value) implements Reference<T> {

        @Override
        public Optional<T> find() {
            return Optional.of(this.value);
        }
    }

    record Lazy<T>(@NotNull Supplier<T> value) implements Reference<T> {

        @Override
        public Optional<T> find() {
            return Optional.ofNullable(this.value.get());
        }
    }

    class Deferred<T> implements Reference<T> {
        private final String key;
        private T value;

        public Deferred(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        void set(T value) {
            this.value = value;
        }

        @Override
        public Optional<T> find() {
            return Optional.ofNullable(this.value);
        }
    }
}
