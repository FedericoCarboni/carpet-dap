package dev.carbons.carpet_dap;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.script.Module;
import carpet.script.*;
import carpet.script.external.Vanilla;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.carbons.carpet_dap.adapter.CarpetDebugAdapter;
import dev.carbons.carpet_dap.adapter.DebugAdapterTransport;
import dev.carbons.carpet_dap.debug.CarpetDebugHost;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Deque;

import static dev.carbons.carpet_dap.CarpetDebugMod.LOGGER;
import static dev.carbons.carpet_dap.CarpetDebugMod.MOD_ID;

/**
 *
 */
public class CarpetDebugExtension implements CarpetExtension {
//    @Nullable
//    private static CarpetDebugAdapter dapLayer;
    @Nullable
    private static ServerCommandSource source;
    @Nullable
    private static Module debuggee;
    @Nullable
    public static CarpetDebugHost debugHost;

    private CarpetDebugExtension() {
    }

    /**
     * Initialize the extension and register it to the Carpet Mod.
     */
    static void initialize() {
        // Register as Carpet Mod extension
        CarpetServer.manageExtension(new CarpetDebugExtension());
    }

    @Nullable
    public static CarpetDebugHost getDebugHost() {
        return debugHost;
    }

    public static void evalHook(Expression expr, Context c, Context.Type t, Tokenizer.Token token) {
        if (debugHost == null) return;
        LOGGER.info("Hook");
//        if (expr.module == null) return;
        debugHost.setStackTrace(c, expr.module, token.lineno, token.linepos);
    }

    private int debuggerCommand(CommandContext<ServerCommandSource> context) {
//        if (dapLayer != null) {
//            // The debugger is already running
//            return 0;
//        }
        source = context.getSource();
        // TODO: implement command

        LOGGER.info("Starting debugging server");
        try (ServerSocket serverSocket = new ServerSocket(6090)) {
            Socket socket = serverSocket.accept();
            debugHost = new CarpetDebugHost(Vanilla.MinecraftServer_getScriptServer(source.getServer()), socket.getInputStream(), socket.getOutputStream());
        } catch (Exception ex) {
            LOGGER.error(ex.toString());
        }

        return 1;
    }

    @Override
    public String version() {
        return MOD_ID;
    }

    @Override
    public void onTick(MinecraftServer server) {
        if (debugHost != null) debugHost.onTick();
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandBuildContext) {
        // Register the `/script debugger` command
        var debugCommand = LiteralArgumentBuilder.<ServerCommandSource>literal("debugger")
                .requires(Vanilla::ServerPlayer_canScriptACE)
                .executes(this::debuggerCommand);
        dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("script")
                .requires(Vanilla::ServerPlayer_canScriptGeneral)
                .then(debugCommand));
    }

    @Override
    public void scarpetApi(CarpetExpression expression) {
        ScarpetDebugOverrides.registerScarpetApi(expression);
    }
}
