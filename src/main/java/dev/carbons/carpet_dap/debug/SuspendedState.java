package dev.carbons.carpet_dap.debug;

import org.eclipse.lsp4j.debug.Variable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SuspendedState {
    // An array of Scarpet values, this is used for the Variables requests.
    private final List<Supplier<Variable[]>> state = new ArrayList<>();

    public int setVariables(@Nonnull Supplier<Variable[]> value) {
        int reference;
        synchronized (state) {
            reference = state.indexOf(value);
            if (reference == -1) {
                reference = state.size();
                state.add(value);
            }
        }
        return reference + 1;
    }

    @Nullable
    public Variable[] get(int reference) {
        Supplier<Variable[]> supplier;
        synchronized (state) {
            supplier = state.get(reference - 1);
        }
        if (supplier == null)
            return null;
        return supplier.get();
    }
}
