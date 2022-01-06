package codes.writeonce.launcher;

import codes.writeonce.templates.GenericAppendable;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

public class StringBuilderAppendable<T extends Throwable> implements GenericAppendable<T> {

    @Nonnull
    private final StringBuilder stringBuilder;

    public StringBuilderAppendable(@Nonnull StringBuilder stringBuilder) {
        this.stringBuilder = requireNonNull(stringBuilder);
    }

    @Nonnull
    @Override
    public GenericAppendable<T> append(@Nonnull CharSequence csq) {
        stringBuilder.append(requireNonNull(csq));
        return this;
    }

    @Nonnull
    @Override
    public GenericAppendable<T> append(@Nonnull CharSequence csq, int start, int end) {
        stringBuilder.append(requireNonNull(csq), start, end);
        return this;
    }

    @Nonnull
    @Override
    public GenericAppendable<T> append(char c) {
        stringBuilder.append(c);
        return this;
    }
}
