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
    private void injectedExecute(
            Context outerContext,
            Context.Type contextType,
            Expression expression,
            Tokenizer.Token token,
            List<Value> params,
            ThreadValue threadValue,
            CallbackInfoReturnable<LazyValue> ci,
            Context functionContext
    ) {
        LOGGER.info("function call");
        if (debugHost == null) return;
        Module module = expression.module;
        if (module == null) return;
        debugHost.pushStackFrame(name, functionContext, module, token.lineno, token.linepos);
    }

    @Inject(
            method = "Lcarpet/script/value/FunctionValue;execute(Lcarpet/script/Context;Lcarpet/script/Context$Type;Lcarpet/script/Expression;Lcarpet/script/Tokenizer$Token;Ljava/util/List;Lcarpet/script/value/ThreadValue;)Lcarpet/script/LazyValue;",
            at = @At("RETURN"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            remap = false
    )
    private void injectedExecuteAfterEval(
            Context outerContext,
            Context.Type contextType,
            Expression expression,
            Tokenizer.Token token,
            List<Value> params,
            ThreadValue threadValue,
            CallbackInfoReturnable<LazyValue> ci,
            Context functionContext
    ) {
        if (debugHost == null) return;
        Module module = expression.module;
        if (module == null) return;
        debugHost.popStackFrame();
//        debugHost.pushStackFrame(name, functionContext, module, token.lineno, token.linepos);
    }

}
