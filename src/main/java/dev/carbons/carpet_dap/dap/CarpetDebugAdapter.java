package dev.carbons.carpet_dap.dap;

import dev.carbons.carpet_dap.CarpetDebugExtension;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static dev.carbons.carpet_dap.CarpetDebugMod.LOGGER;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 *
 */
public class CarpetDebugAdapter implements IDebugProtocolServer {
    private final BlockingQueue<Record> sender;

    // When true the debugger client sends lines and columns starting from 1 instead of 0
    private boolean linesStartAt1 = false;
    private boolean columnsStartAt1 = false;

    private final Map<Source, SourceBreakpoint[]> breakpoints = new HashMap<>();

    private Source source;

    public Map<Source, SourceBreakpoint[]> getBreakpoints() {
        return breakpoints;
    }

    public CarpetDebugAdapter(BlockingQueue<Record> sender) {
        this.sender = sender;
    }

    public static DapLayer start(InputStream is, OutputStream os) {
        BlockingQueue<Record> queue = new LinkedBlockingQueue<>(1);
        var adapter = new CarpetDebugAdapter(queue);
        var launcher = DSPLauncher.createServerLauncher(adapter, is, os);
        var client = launcher.getRemoteProxy();
        var layer = new DapLayer(adapter, client, queue);
        launcher.startListening();
        client.initialized();
        return layer;
    }

    @Nullable
    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String string) return string;
        return null;
    }

    @Nullable
    private static Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) return bool;
        return null;
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
    public CompletableFuture<ThreadsResponse> threads() {
        LOGGER.info("[DAP]: threads()");
        var threads = new ThreadsResponse();
        var thread = new Thread();
        thread.setId(1);
        thread.setName("main");
        threads.setThreads(new Thread[]{thread});
        return completedFuture(threads);
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
        var trace = getBoolean(args, "trace");
        var stopOnEvent = getBoolean(args, "stopOnEvent");
        if (!Objects.equals(type, "scarpet")) {
            LOGGER.info("[DAP]: unknown launch type {}", type);
        }
        LOGGER.info("[DAP]: launch program {}", program);
        LOGGER.info("[DAP]: noDebug {}", noDebug);
        try {
            // Put makes sure to wait until the onTick handler picks up the launch event.
            sender.put(new LaunchParams(program, noDebug != null && noDebug, trace == null || trace, stopOnEvent != null && stopOnEvent));
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
    public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
        return IDebugProtocolServer.super.continue_(args);
    }

    @Override
    public CompletableFuture<Void> next(NextArguments args) {
        LOGGER.info("[DAP]: next()");
        try {
            sender.put(new NextParams());
        } catch (InterruptedException ex) {
            //
        }
        return completedFuture(null);
    }

    @Override
    public CompletableFuture<BreakpointLocationsResponse> breakpointLocations(BreakpointLocationsArguments args) {
        return IDebugProtocolServer.super.breakpointLocations(args);
    }

    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        LOGGER.info("[DAP]: setBreakpoints()");
        LOGGER.info(args.toString());
        source = args.getSource();
        var res = new SetBreakpointsResponse();
        var breakpoint = new Breakpoint();
        breakpoint.setId(1);
        breakpoint.setLine(args.getBreakpoints()[0].getLine());
        res.setBreakpoints(new Breakpoint[]{breakpoint});
        return completedFuture(res);
    }

    @Override
    public CompletableFuture<SetExceptionBreakpointsResponse> setExceptionBreakpoints(SetExceptionBreakpointsArguments args) {
        return completedFuture(new SetExceptionBreakpointsResponse());
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        return IDebugProtocolServer.super.evaluate(args);
    }

    @Override
    public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
        var frame = new StackFrame();
        frame.setId(1);
        frame.setSource(source);
        frame.setLine(CarpetDebugExtension.currentLine + (linesStartAt1 ? 1 : 0));
        var res = new StackTraceResponse();
        res.setStackFrames(new StackFrame[]{frame});
        return completedFuture(res);
    }

    @Override
    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
        return completedFuture(new ScopesResponse());
    }

    @Override
    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
        return completedFuture(new VariablesResponse());
    }

    //    @Override
//    public CompletableFuture<Void> launch(Map<String, Object> args) {
//        return IDebugProtocolServer.super.launch(args);
//    }
}
