package com.maxkapral.union;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final
class UnionMatchResult<T> implements Supplier<T> {
    private static final UnionMatchResult<?> UNMATCHED = new UnionMatchResult<>();

    private final boolean matched;
    private final T value;

    private
    UnionMatchResult () {
        this.matched = false;
        this.value = null;
    }

    private
    UnionMatchResult (boolean matched, T value) {
        this.matched = matched;
        this.value = value;
    }

    public static
    <T> UnionMatchResult<T> of(T value) {
        return new UnionMatchResult<>(true, value);
    }

    public static
    <T> UnionMatchResult<T> empty() {
        @SuppressWarnings("unchecked")
        var t = (UnionMatchResult<T>) UNMATCHED;
        return t;
    }

    public boolean isPresent() {
        return matched;
    }

    @Override
    public T get() {
        if (!matched) {
            throw new NoSuchElementException("Unmatched result");
        }
        return value;
    }

    public
    T orElseGet (Supplier<? extends T> defaultValue) {
        if (!matched) {
            return defaultValue.get();
        }
        return value;
    }

    public
    T orElse (T defaultValue) {
        return orElseGet(() -> defaultValue);
    }

    public
    void ifPresentOrElse (Consumer<T> consumer, Runnable action) {
        if (matched) {
            consumer.accept(value);
            return;
        }
        action.run();
    }

    public
    void ifPresent (Consumer<T> consumer) {
        ifPresentOrElse(consumer, () -> {});
    }

    @Override
    public String toString() {
        return matched
            ? String.format("UnionMatchResult[%s]", value)
            : "UnionMatchResult[unmatched]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UnionMatchResult<?> other)) {
            return false;
        }
        return matched == other.matched && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        if (!matched) {
            return 1;
        }
        if (value == null) {
            return 2;
        }
        return 31 * 17 + value.hashCode();
    }
}
