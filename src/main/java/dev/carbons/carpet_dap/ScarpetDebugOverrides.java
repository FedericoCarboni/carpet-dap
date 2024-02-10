package dev.carbons.carpet_dap;

import carpet.script.*;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.EntityValue;
import carpet.script.value.FormattedTextValue;
import carpet.script.value.ListValue;
import carpet.script.value.Value;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

interface ScarpetDebugOverrides {
    static void registerScarpetApi(CarpetExpression carpetExpression) {
        Expression expression = carpetExpression.getExpr();

        // Override print and logger functions to also output to the debugger client
        // These should arguably be mixins but ¯\_(ツ)_/¯
        expression.addContextFunction("print", -1, ScarpetDebugOverrides::print);
        expression.addImpureFunction("logger", ScarpetDebugOverrides::logger);

        // The idea is to add a `debugger()` function here so that we can start a debugging session programmatically
        expression.addContextFunction("debugger", 0, (context, type, args) -> {
            // TODO: how do we actually block and create a debugging session?
            return Value.NULL;
        });
    }

    private static Value print(Context context, Context.Type ignored, List<Value> args) {
        if (args.isEmpty() || args.size() > 2) {
            throw new InternalExpressionException("'print' takes one or two arguments");
        }
        //
        String messageString = args.size() == 1 ? args.get(0).getString() : args.get(1).getString();
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        builder.append(']');
        builder.append(messageString);
        builder.append('\n');
        CarpetDebugExtension.addDebugOutput("stdout", builder.toString());
        // Regular print() behavior straight from fabric-carpet
        CarpetContext carpetContext = (CarpetContext) context;
        ServerCommandSource s = carpetContext.source();
        MinecraftServer server = s.getServer();
        Value res = args.get(0);
        List<ServerCommandSource> targets = null;
        if (args.size() == 2) {
            targets = getServerCommandSources(res, server);
            res = args.get(1);
        } else if (context.host.user != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(carpetContext.host.user);
            if (player != null) {
                targets = Collections.singletonList(player.getCommandSource());
            }
        } // optionally retrieve from CC.host.responsibleSource to print?
        Text message = FormattedTextValue.getTextByValue(res);
        if (targets == null) {
            s.sendFeedback(() -> message, false);
        } else {
            targets.forEach(p -> p.sendFeedback(() -> message, false));
        }
        return res; // pass through for variables
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
}
