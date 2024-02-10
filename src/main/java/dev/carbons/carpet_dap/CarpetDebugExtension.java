package dev.carbons.carpet_dap;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.script.Module;
import carpet.script.*;
import carpet.script.external.Vanilla;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.carbons.carpet_dap.dap.CarpetDebugAdapter;
import dev.carbons.carpet_dap.dap.DapLayer;
import dev.carbons.carpet_dap.dap.LaunchParams;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import org.eclipse.lsp4j.debug.StoppedEventArguments;

import javax.annotation.Nullable;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

import static dev.carbons.carpet_dap.CarpetDapMod.LOGGER;
import static dev.carbons.carpet_dap.CarpetDapMod.MOD_ID;

/**
 *
 */
public class CarpetDapExtension implements CarpetExtension {
    @Nullable
    private static DapLayer dapLayer;
    @Nullable
    private static ServerCommandSource source;

    private CarpetDapExtension() {
    }

    public static void evalHook(Context c, Context.Type t, Tokenizer.Token token) {
        if (dapLayer == null) return;
        LOGGER.info("evalHook");
        LOGGER.info("at {}", token.lineno);
        var stopped = new StoppedEventArguments();
        stopped.setReason("step");
        stopped.setHitBreakpointIds(new Integer[]{1});
        dapLayer.getClient().stopped(stopped);
    }

    /**
     * Initialize the extension and register it to the Carpet Mod.
     */
    static void initialize() {
        // Register as Carpet Mod extension
        CarpetServer.manageExtension(new CarpetDapExtension());
    }

    private int debugCommand(CommandContext<ServerCommandSource> context) {
        if (dapLayer != null) {
            // The debugger is already running
            return 0;
        }
        source = context.getSource();
        // TODO: implement command

        LOGGER.info("Starting debugging server");
        try (ServerSocket serverSocket = new ServerSocket(6090)) {
            Socket socket = serverSocket.accept();
            dapLayer = CarpetDebugAdapter.start(socket.getInputStream(), socket.getOutputStream());
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
        if (dapLayer == null) return;
        // Each tick we check if the debugger requested a launch
        var request = dapLayer.getQueue().poll();
        if (request == null) return;
        var scriptServer = Vanilla.MinecraftServer_getScriptServer(server);
        if (request instanceof LaunchParams launchParams) {
            LOGGER.info("launch()");
            var host = CarpetScriptHost.create(scriptServer, Module.fromPath(Path.of(launchParams.program())), false, source, null, false, null);
//            dapLayer.getClient().output(OutputEventArguments);
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandBuildContext) {
        // Register the `/script debugger` command
        var debugCommand = LiteralArgumentBuilder.<ServerCommandSource>literal("debugger")
                .executes(this::debugCommand);
        dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("script")
                .requires(Vanilla::ServerPlayer_canScriptGeneral)
                .then(debugCommand));
    }

    @Override
    public void scarpetApi(CarpetExpression expression) {
        ScarpetDebugOverrides.registerScarpetApi(expression);

    }
}
