package dev.carbons.carpet_dap.mixin;

import carpet.script.*;
import carpet.script.Module;
import carpet.script.value.FunctionValue;
import carpet.script.value.ThreadValue;
import carpet.script.value.Value;
import dev.carbons.carpet_dap.CarpetDebugExtension;
import dev.carbons.carpet_dap.debug.CarpetDebugHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
@Mixin(value = FunctionValue.class, remap = false)
public class FunctionValueMixin {
    @Shadow(remap = false)
    private String name;
    @Shadow(remap = false)
    private List<String> args;
    @Shadow(remap = false)
    private String varArgs;

    private List<String> getArgs() {
        List<String> arguments = args;
        if (varArgs != null) {
            arguments = new ArrayList<>(arguments);
            arguments.add(varArgs);
        }
        // Make sure we don't modify the arguments list by accident
        return Collections.unmodifiableList(arguments);
    }

    @Inject(
            method = "Lcarpet/script/value/FunctionValue;execute(Lcarpet/script/Context;Lcarpet/script/Context$Type;Lcarpet/script/Expression;Lcarpet/script/Tokenizer$Token;Ljava/util/List;Lcarpet/script/value/ThreadValue;)Lcarpet/script/LazyValue;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcarpet/script/LazyValue;evalValue(Lcarpet/script/Context;Lcarpet/script/Context$Type;)Lcarpet/script/value/Value;"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            remap = false
    )
    private void atExecute(
            Context outerContext,
            Context.Type contextType,
            Expression expression,
            Tokenizer.Token token,
            List<Value> params,
            ThreadValue threadValue,
            CallbackInfoReturnable<LazyValue> ci,
            Context functionContext
    ) {
        Module module = expression.module;
        CarpetDebugHost debugHost = CarpetDebugExtension.getDebugHost();
        if (debugHost == null || module == null) return;
        // Set stack trace before executing the function
        debugHost.setStackTrace(functionContext, module, token.lineno, token.linepos);
        // About to execute, push the new stack frame
        debugHost.pushStackFrame(name == null || name.equals("_") ? "<lambda>" : name, getArgs(), functionContext, module, token.lineno, token.linepos);
    }

    @Inject(
            method = "Lcarpet/script/value/FunctionValue;execute(Lcarpet/script/Context;Lcarpet/script/Context$Type;Lcarpet/script/Expression;Lcarpet/script/Tokenizer$Token;Ljava/util/List;Lcarpet/script/value/ThreadValue;)Lcarpet/script/LazyValue;",
            at = @At("RETURN"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            remap = false
    )
    private void afterExecute(
            Context outerContext,
            Context.Type contextType,
            Expression expression,
            Tokenizer.Token token,
            List<Value> params,
            ThreadValue threadValue,
            CallbackInfoReturnable<LazyValue> ci,
            Context functionContext
    ) {
        CarpetDebugHost debugHost = CarpetDebugExtension.getDebugHost();
        if (debugHost == null || expression.module == null) return;
        // The function is returning; pop out one stack frame.
        debugHost.popStackFrame();
    }
}
