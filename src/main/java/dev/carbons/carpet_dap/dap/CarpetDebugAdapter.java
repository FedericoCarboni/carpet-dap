package dev.carbons.carpet_dap.dap;

import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static dev.carbons.carpet_dap.CarpetDapMod.LOGGER;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 *
 */
public class CarpetDapServer implements IDebugProtocolServer {
    private final BlockingQueue<Record> sender;

    // When true the debugger client sends lines and columns starting from 1 instead of 0
    private boolean linesStartAt1 = false;
    private boolean columnsStartAt1 = false;

    private final Map<Source, SourceBreakpoint[]> breakpoints = new HashMap<>();

    @Nonnull
    public Map<Source, SourceBreakpoint[]> getBreakpoints() {
        return breakpoints;
    }

    public CarpetDapServer(BlockingQueue<Record> sender) {
        this.sender = sender;
    }

    public static DapLayer start(InputStream is, OutputStream os) {
        BlockingQueue<Record> queue = new LinkedBlockingQueue<>(1);
        var launcher = DSPLauncher.createServerLauncher(new CarpetDapServer(queue), is, os);
        var client = launcher.getRemoteProxy();
        var layer = new DapLayer(client, queue);
        launcher.startListening();
        return layer;
    }

    @Nullable
    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String string) return string;
        return null;
    }

    private static boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) return bool;
        return value != null;
    }

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        LOGGER.info("[DAP]: initialize()");
        if (args.getLinesStartAt1()) linesStartAt1 = true;
        if (args.getColumnsStartAt1()) columnsStartAt1 = true;
        var capabilities = new Capabilities();
        // TODO: which capabilities do we support?
        return completedFuture(capabilities);
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        LOGGER.info("[DAP]: configurationDone()");
        return completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> launch(Map<String, Object> args) {
        LOGGER.info("[DAP]: launch()");
        var type = getString(args, "type");
        var program = getString(args, "program");
        var noDebug = getBoolean(args, "noDebug");
        if (!Objects.equals(type, "scarpet")) {
            LOGGER.info("[DAP]: unknown launch type {}", type);
        }
        LOGGER.info("[DAP]: launch program {}", program);
        LOGGER.info("[DAP]: noDebug {}", noDebug);
        try {
            // Put makes sure to wait until the onTick handler picks up the launch event.
            sender.put(new LaunchParams(program, noDebug));
        } catch (InterruptedException ex) {
            LOGGER.error("[DAP]: Had an oopsie: {}", ex.toString());
        }
        return completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        LOGGER.info("[DAP]: disconnect()");
        return completedFuture(null);
    }

    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        LOGGER.info("[DAP]: setBreakpoints()");
        return IDebugProtocolServer.super.setBreakpoints(args);
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        return IDebugProtocolServer.super.evaluate(args);
    }

    //    @Override
//    public CompletableFuture<Void> launch(Map<String, Object> args) {
//        return IDebugProtocolServer.super.launch(args);
//    }
}
