package dev.carbons.carpet_dap.mixin;

import carpet.script.Context;
import carpet.script.Expression;
import carpet.script.LazyValue;
import carpet.script.Tokenizer;
import dev.carbons.carpet_dap.CarpetDebugExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

// Unfortunately all eval methods are implemented in anonymous classes in the following methods
// addLazyUnaryOperator 1
// addLazyBinaryOperatorWithDelegation 2
// addLazyFunctionWithDelegation 3
// addFunctionWithDelegation 4
// addLazyBinaryOperator 5
// addBinaryContextOperator 6
// addUnaryOperator 7
// addBinaryOperator 8
// addUnaryFunction 9
// addImpureUnaryFunction 10
// addBinaryFunction 11
// addFunction 12
// addImpureFunction 13
// addLazyFunction 14
// addPureLazyFunction 15
// addContextFunction 16
// addTypedContextFunction 17
@SuppressWarnings("unused")
@Mixin(targets = {
//        "carpet.script.Expression$1",
//        "carpet.script.Expression$2",
        "carpet.script.Expression$3",
        "carpet.script.Expression$4",
//        "carpet.script.Expression$5",
//        "carpet.script.Expression$6",
//        "carpet.script.Expression$7",
//        "carpet.script.Expression$8",
//        "carpet.script.Expression$9",
//        "carpet.script.Expression$10",
//        "carpet.script.Expression$11",
//        "carpet.script.Expression$12",
//        "carpet.script.Expression$13",
        "carpet.script.Expression$14",
        "carpet.script.Expression$15",
        "carpet.script.Expression$16",
        "carpet.script.Expression$17",
}, remap = false)
public class ExpressionLazyEvalMixin {
    @Inject(method = "lazyEval", at = @At("HEAD"), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private void injected(Context context, Context.Type contextType, Expression expression, Tokenizer.Token token, List<LazyValue> lv, CallbackInfoReturnable<LazyValue> ci) {
        CarpetDebugExtension.evalHook(expression, context, contextType, token);
    }
}
