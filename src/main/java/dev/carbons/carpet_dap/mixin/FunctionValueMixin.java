package dev.carbons.carpet_dap.mixin;

import carpet.script.*;
import carpet.script.Module;
import carpet.script.value.FunctionValue;
import carpet.script.value.ThreadValue;
import carpet.script.value.Value;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;

import static dev.carbons.carpet_dap.CarpetDebugExtension.debugHost;
import static dev.carbons.carpet_dap.CarpetDebugMod.LOGGER;

@SuppressWarnings("unused")
@Mixin(value = FunctionValue.class, remap = false)
public class FunctionValueMixin {
    @Shadow
    private String name;
    @Shadow
    private List<String> args;
    @Shadow
    private String varArgs;

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
        if (debugHost == null || module == null) return;
        // Stop before executing the function
        debugHost.setStackTrace(functionContext, module, token.lineno, token.linepos);
        List<String> newParams = args;
        if (varArgs != null) {
            newParams = new ArrayList<>(newParams);
            newParams.add(varArgs);
        }
        debugHost.pushStackFrame(name == null || name.equals("_") ? "<anonymous>" : name, newParams, functionContext, module, token.lineno, token.linepos);
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
        if (debugHost == null || expression.module == null) return;
        // Got to the return of the function pop out one stack frame.
        debugHost.popStackFrame();
    }
}
