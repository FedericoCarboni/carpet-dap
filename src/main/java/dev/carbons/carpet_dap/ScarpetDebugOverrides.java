package dev.carbons.carpet_dap;

import carpet.script.*;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.EntityValue;
import carpet.script.value.FormattedTextValue;
import carpet.script.value.ListValue;
import carpet.script.value.Value;
import dev.carbons.carpet_dap.debug.CarpetDebugHost;
import dev.carbons.carpet_dap.debug.ModuleSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class ScarpetDebugOverrides {
    @NotNull
    private static String getOutputMessage(Context context, Tokenizer.Token token, Value value) {
        String messageString = value.getString();
        StringBuilder builder = new StringBuilder();
//        builder.append('[');
//        // TODO: add the module source to the output message
////        ModuleSource moduleSource = CarpetDebugHost.getModuleSource(context.host.main);
////        if (moduleSource != null) {
////
////        }
////        builder.append(Objects.requireNonNullElseGet(moduleSource, () -> context.host.getName()));
//        if (context.host.main != null) {
//            builder.append(':');
//            builder.append(token.lineno + 1);
//        }
//        builder.append("] ");
        builder.append(messageString);
        builder.append('\n');
        return builder.toString();
    }

    static void registerScarpetApi(CarpetExpression carpetExpression) {
        Expression expression = carpetExpression.getExpr();

        // Override print and logger functions to also output to the debugger client
        // These should probably be mixins but ¯\_(ツ)_/¯
        // This needs to be a custom function to get and print the current to the client
        expression.addCustomFunction("print", new PrintFunction());
        expression.addCustomFunction("logger", new LoggerFunction());
        expression.addImpureFunction("logger", ScarpetDebugOverrides::logger);

        // The idea is to add a `debugger()` function here so that we can start a debugging session programmatically
        expression.addContextFunction("debugger", 0, (context, type, args) -> {
            // TODO: how do we actually block and create a debugging session?
            return Value.NULL;
        });
    }

    private static Value logger(List<Value> args) {
        Value res;
        if (args.size() == 1) {
            res = args.get(0);
            CarpetDebugExtension.addDebugOutput("stderr", res.getString());
            CarpetScriptServer.LOG.info(res.getString());
        } else if (args.size() == 2) {
            String level = args.get(0).getString().toLowerCase(Locale.ROOT);
            res = args.get(1);
            String message = res.getString();
            CarpetDebugExtension.addDebugOutput("stderr", message);
            switch (level) {
                case "debug" -> {
                    CarpetScriptServer.LOG.debug(message);
                }
                case "warn" -> {
                    CarpetScriptServer.LOG.warn(message);
                }
                case "info" -> {
                    CarpetScriptServer.LOG.info(message);
                }
                // Somehow issue deprecation
                case "fatal", "error" -> {
                    CarpetScriptServer.LOG.error(message);
                }
                default -> {
                    throw new InternalExpressionException("Unknown log level for 'logger': " + level);
                }
            }
        } else {
            throw new InternalExpressionException("logger takes 1 or 2 arguments");
        }

        return res; // pass through for variables

    }

    @NotNull
    private static List<ServerCommandSource> getServerCommandSources(Value res, MinecraftServer server) {
        List<Value> playerValues = (res instanceof ListValue list) ? list.getItems() : Collections.singletonList(res);
        List<ServerCommandSource> playerTargets = new ArrayList<>();
        playerValues.forEach(pv -> {
            ServerPlayerEntity player = EntityValue.getPlayerByValue(server, pv);
            if (player == null) {
                throw new InternalExpressionException("Cannot target player " + pv.getString() + " in print");
            }
            playerTargets.add(player.getCommandSource());
        });
        return playerTargets;
    }

    private static class LoggerFunction extends Fluff.AbstractLazyFunction {
        private LoggerFunction() {
            super(-1, "logger");
        }

        @Override
        public LazyValue lazyEval(Context c, Context.Type type, Expression expr, Tokenizer.Token token, List<LazyValue> lazyParams) {
            return null;
        }

        @Override
        public boolean pure() {
            return false;
        }

        @Override
        public boolean transitive() {
            return false;
        }
    }

    private static class PrintFunction extends Fluff.AbstractLazyFunction {
        private PrintFunction() {
            super(-1, "print");
        }

        @Override
        public boolean pure() {
            return false;
        }

        @Override
        public boolean transitive() {
            return false;
        }

        // We need information about the current line
        @Override
        public LazyValue lazyEval(Context context, Context.Type contextType, Expression expr, Tokenizer.Token token, List<LazyValue> args) {
            CarpetDebugExtension.evalHook(expr, context, contextType, token);
            if (args.isEmpty() || args.size() > 2) {
                throw new InternalExpressionException("'print' takes one or two arguments");
            }
            // Regular print() behavior straight from fabric-carpet
            CarpetContext carpetContext = (CarpetContext) context;
            ServerCommandSource s = carpetContext.source();
            MinecraftServer server = s.getServer();
            Value value = args.get(0).evalValue(context, contextType);
            List<ServerCommandSource> targets = null;
            if (args.size() == 2) {
                targets = getServerCommandSources(value, server);
                value = args.get(1).evalValue(context, contextType);
            } else if (context.host.user != null) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(carpetContext.host.user);
                if (player != null) {
                    targets = Collections.singletonList(player.getCommandSource());
                }
            } // optionally retrieve from CC.host.responsibleSource to print?
            Text message = FormattedTextValue.getTextByValue(value);
            if (targets == null) {
                s.sendFeedback(() -> message, false);
            } else {
                targets.forEach(p -> p.sendFeedback(() -> message, false));
            }

            // Extension
            CarpetDebugHost debugHost = CarpetDebugExtension.getDebugHost();
            if (debugHost != null && expr.module != null) {
                String outputMessage = getOutputMessage(context, token, value);
                debugHost.sendOutput(outputMessage, expr.module, token.lineno, token.linepos);
            }

            Value ret = value;
            return (c, t) -> ret; // pass through for variables
        }
    }
}
