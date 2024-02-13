package dev.carbons.carpet_dap.adapter;

import carpet.script.Context;
import carpet.script.Module;
import dev.carbons.carpet_dap.debug.ModuleSource;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.Variable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

public class StackTrace {
    private final List<CarpetStackFrame> stack = new ArrayList<>();

    private final AtomicInteger idNext;

    public StackTrace(AtomicInteger idNext) {
        this.idNext = idNext;
    }

    public CarpetStackFrame addStackFrame(@Nonnull Context context, @Nonnull String name, @Nonnull Module module, @Nonnull ModuleSource moduleSource, int sourceReference, int line, int character) {
        var stackFrame = new CarpetStackFrame(idNext.getAndIncrement(), context, name, module, moduleSource, sourceReference, line, character);
        stack.add(stackFrame);
        return stackFrame;
    }

    public void popStackFrame() {
        stack.remove(stack.size() - 1);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public void updateStackFrame(@Nonnull Context context, @Nonnull Module module, @Nonnull ModuleSource moduleSource, int sourceReference, int line, int character) {
        if (stack.isEmpty()) {
            addStackFrame(context, "global", module, moduleSource, sourceReference, line, character);
            return;
        }
        var stackFrame = stack.get(stack.size() - 1);
        stackFrame.context = context;
        stackFrame.line = line;
        stackFrame.character = character;
    }

    public StackFrame[] getStackFrames(boolean linesStartAt1, boolean columnsStartAt1) {
        var stackFrames = new StackFrame[stack.size()];
        int i = stack.size();
        for (CarpetStackFrame stackFrame : stack) {
            stackFrames[--i] = stackFrame.toStackFrame(linesStartAt1, columnsStartAt1);
        }
        return stackFrames;
    }

    public static class CarpetStackFrame {
        @Nonnull
        protected Context context;
        @Nonnull
        private final Module module;
        @Nonnull
        private final ModuleSource moduleSource;
        @Nonnull
        private final String name;
        private final int startLine;
        private final int startCharacter;
        protected int line;
        protected int character;
        public int id;
        private final int sourceReference;

        CarpetStackFrame(int id, @Nonnull Context context, @Nonnull String name, @Nonnull Module module, @Nonnull ModuleSource moduleSource, int sourceReference, int startLine, int startCharacter) {
            this.context = context;
            this.name = name;
            this.module = module;
            this.moduleSource = moduleSource;
            this.startLine = startLine;
            this.startCharacter = startCharacter;
            this.line = startLine;
            this.character = startCharacter;
            this.id = id;
            this.sourceReference = sourceReference;
        }

        @Nonnull
        StackFrame toStackFrame(boolean linesStartAt1, boolean columnsStartAt1) {
            var source = new Source();
            source.setName(module.name());
            if (sourceReference == 0) {
                source.setPath(moduleSource.path());
            }
            source.setSourceReference(sourceReference);

            var stackFrame = new StackFrame();
            stackFrame.setId(id);
            stackFrame.setName(name);
            stackFrame.setSource(source);
            stackFrame.setLine(line + (linesStartAt1 ? 1 : 0));
            stackFrame.setColumn(character + (columnsStartAt1 ? 1 : 0));
            return stackFrame;
        }

        @Nonnull
        Scope[] getScopes() {
            var args = new Scope();
            args.setNamedVariables(context.variables.size());
            return new Scope[]{args};
        }

        @Nonnull
        Variable[] getVariables() {
            Variable[] variables = new Variable[context.variables.size()];
            context.variables.forEach((name, value) -> {
                var val = value.evalValue(context);
                var variable = new Variable();
                variable.setName(name);
                variable.setValue(val.getPrettyString());
                variable.setType(val.getTypeString());
            });
            return variables;
        }
    }
}
