package dev.carbons.carpet_dap.debug;

import carpet.script.CarpetScriptHost;
import carpet.script.CarpetScriptServer;
import carpet.script.Context;
import carpet.script.Module;
import carpet.script.exception.LoadException;
import carpet.script.external.Carpet;
import carpet.script.external.Vanilla;
import carpet.script.value.FunctionValue;
import dev.carbons.carpet_dap.adapter.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.carbons.carpet_dap.CarpetDebugMod.LOGGER;
import static dev.carbons.carpet_dap.debug.ModuleSource.getModuleSource;

public final class CarpetDebugHost {

    // Just used as a map index (sourceReference) -> Module, entries are never removed.
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, int[]> breakpoints = new HashMap<>();
    // Thread ID -> StackTrace, entries may be removed as threads start and close.
    private final Int2ObjectMap<StackTrace> stackTraces = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<StackTrace.CarpetStackFrame> stackFrames = new Int2ObjectOpenHashMap<>();
    private final AtomicInteger stackFrameNext = new AtomicInteger(1);
    private final BlockingQueue<Record> queue = new LinkedBlockingQueue<>(1);
    @Nonnull
    private final CarpetScriptServer scriptServer;
    @Nonnull
    private final CarpetDebugAdapter debugAdapter;
    @Nonnull
    private final IDebugProtocolClient debugClient;
    // The entrypoint of the debug host, e.g. the `program` in the launch request.
    @Nullable
    private Module entrypoint;
    // Suspended state for variables and scopes
    private SuspendedState suspendedState = new SuspendedState();
    // true when the target code is stopped
    private boolean stopped = false;

    public CarpetDebugHost(@Nonnull CarpetScriptServer scriptServer, @Nonnull InputStream is, @Nonnull OutputStream os) {
        this.scriptServer = scriptServer;
        debugAdapter = new CarpetDebugAdapter(queue, this);
        Launcher<IDebugProtocolClient> launcher = DSPLauncher.createServerLauncher(debugAdapter, is, os);
        debugClient = launcher.getRemoteProxy();
        launcher.startListening();
        debugClient.initialized();
    }

    public SuspendedState getSuspendedState() {
        return suspendedState;
    }

    public void sendToConsole(@Nonnull String message) {
        OutputEventArguments outputEvent = new OutputEventArguments();
        outputEvent.setCategory(OutputEventArgumentsCategory.CONSOLE);
        outputEvent.setOutput(message);
        debugClient.output(outputEvent);
    }

    public void sendOutput(@Nonnull Module module, int line, int character, @Nonnull String output, @Nullable Object data) {
        ModuleSource moduleSource = getModuleSource(module);
        OutputEventArguments outputEvent = new OutputEventArguments();
        outputEvent.setCategory(OutputEventArgumentsCategory.STDOUT);
        outputEvent.setOutput(output);
        outputEvent.setSource(getSource(module, moduleSource));
        outputEvent.setLine(line + (debugAdapter.getLinesStartAt1() ? 1 : 0));
        outputEvent.setColumn(character + (debugAdapter.getColumnsStartAt1() ? 1 : 0));
        outputEvent.setData(data);
        debugClient.output(outputEvent);
    }

    public void sendOutput(@Nonnull Module module, int line, int character, @Nonnull String output) {
        sendOutput(module, line, character, output, null);
    }

    private boolean shouldStop(@Nonnull Module module, int line) {
        if (stopped) return true;
        ModuleSource moduleSource = getModuleSource(module);
        int[] lines = breakpoints.get(moduleSource.path());
        if (lines == null) return false;
        for (int bp : lines) {
            if (bp == line) return true;
        }
        return false;
    }

    // Launch any program as requested by the debugger
    private void launch(@Nonnull LaunchParams params) {
        if (entrypoint != null) return;
        entrypoint = Module.fromPath(Path.of(params.program()));
        String name = entrypoint.name();

        StackTrace stackTrace = new StackTrace(stackFrameNext);
        stackTraces.put(1, stackTrace);

        // From CarpetScriptServer.addScriptHost(), we have to do it ourselves.
        Runnable token = Carpet.startProfilerSection("Scarpet debug load");
        ServerCommandSource source = scriptServer.server.getCommandSource();
        long start = System.nanoTime();
        boolean reload = false;
        if (scriptServer.modules.containsKey(name)) {
            scriptServer.removeScriptHost(source, name, false, false);
            reload = true;
        }
        CarpetScriptHost newHost;
        try {
            newHost = CarpetScriptHost.create(scriptServer, entrypoint, true, source, null, false, null);
        } catch (LoadException e) {
            Carpet.Messenger_message(source, "r Failed to add " + name + " app" + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return;
        }

        scriptServer.modules.put(name, newHost);
        scriptServer.unloadableModules.add(name);

        if (!newHost.persistenceRequired) {
            scriptServer.removeScriptHost(source, name, false, false);
            return;
        }
        String action = reload ? "reloaded" : "loaded";

        Boolean isCommandAdded = newHost.addAppCommands(s -> {
            Carpet.Messenger_message(source, "r Failed to add app '" + name + "': ", s);
        });
        if (isCommandAdded == null) {
            scriptServer.removeScriptHost(source, name, false, false);
            return;
        } else if (isCommandAdded) {
            Vanilla.MinecraftServer_notifyPlayersCommandsChanged(scriptServer.server);
            Carpet.Messenger_message(source, "gi " + name + " app " + action + " with /" + name + " command");
        } else {
            Carpet.Messenger_message(source, "gi " + name + " app " + action);
        }

        if (newHost.isPerUser()) {
            // that will provide player hosts right at the startup
            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                newHost.retrieveForExecution(player.getCommandSource(), player);
            }
        } else {
            // global app - calling start now.
            FunctionValue onStart = newHost.getFunction("__on_start");
            if (onStart != null) {
                newHost.callNow(onStart, Collections.emptyList());
            }
        }
        token.run();
        long end = System.nanoTime();
        LOGGER.info("App " + name + " loaded in " + (end - start) / 1000000 + " ms");
    }

    // To be executed on each tick
    public void onTick() {
        // If there's an entrypoint launch() already happened.
        if (entrypoint != null) return;
        // This is kind of smelly; but I didn't find a good point to catch the launch request from the minecraft
        // thread. This has to be from the main server thread since Scarpet execution always starts from there.
        // This is also technically not that great, since we may execute in different parts of the tick lifetime.
        Record record = queue.poll();
        if (record instanceof final LaunchParams params) {
            launch(params);
        }
    }

    /**
     * Push a new StackFrame.
     *
     * @param name           name of the StackFrame, e.g. a function name
     * @param context        context of the StackFrame
     * @param module         module containing the code of this stack frame
     * @param startLine      start line of the stack frame
     * @param startCharacter start character of the stack frame
     */
    public void pushStackFrame(@Nonnull String name, @Nonnull List<String> args, @Nonnull Context context, @Nonnull Module module, int startLine, int startCharacter) {
        int sourceReference = getSourceReference(module);
        StackTrace stackTrace = stackTraces.get(1);
        ModuleSource moduleSource = getModuleSource(module);
        if (moduleSource == null) throw new NullPointerException();
        StackTrace.CarpetStackFrame stackFrame = stackTrace.addStackFrame(args, context, name, module, moduleSource, sourceReference, startLine, startCharacter);
        stackFrames.put(stackFrame.id, stackFrame);
    }
    private int getSourceReference(@Nonnull Module module) {
        // Get or add to the modules to have a valid sourceReference.
        int sourceReference;
        synchronized (modules) {
            sourceReference = modules.indexOf(module);
            if (sourceReference == -1) {
                sourceReference = modules.size();
                modules.add(module);
            }
        }
        return sourceReference;
    }

    private Source getSource(@Nonnull Module module, @Nonnull ModuleSource moduleSource) {
        Source source = new Source();
        source.setName(module.name());
        if (moduleSource.type() == ModuleSource.Type.FILESYSTEM)
            source.setPath(moduleSource.path());
        else
            source.setOrigin("built-in scripts");
        source.setSourceReference(getSourceReference(module));
        return source;
    }

    public void popStackFrame() {
        StackTrace stackTrace = stackTraces.get(1);
        stackTrace.popStackFrame();
    }

    // Function called from within Scarpet evaluation to set all the important information to
    public void setStackTrace(@Nonnull Context context, @Nonnull Module module, int line, int character) {
        StackTrace stackTrace = stackTraces.get(1);
        if (stackTrace.isEmpty()) {
            StackTrace.CarpetStackFrame stackFrame = stackTrace.addStackFrame(null, context, "<global>", module, getModuleSource(module), getSourceReference(module), line, character);
            stackFrames.put(stackFrame.id, stackFrame);
        } else
        stackTrace.updateStackFrame(null, context, module, module == null ? null : getModuleSource(module), getSourceReference(module), line, character);
        LOGGER.info("trace at line {}", line);
        if (shouldStop(module, line)) {
            StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
            stoppedEventArguments.setReason("breakpoint");
            stoppedEventArguments.setThreadId(1);
            debugClient.stopped(stoppedEventArguments);
            while (true) {
                Record record = null;
                try {
                    record = queue.take();
                } catch (InterruptedException ex) {
                    //
                }
                if (record instanceof ContinueParams) {
                    stopped = false;
                    break;
                } else if (record instanceof NextParams) {
                    stopped = true;
                    break;
                }
            }
            suspendedState = new SuspendedState();
        }
    }

    public void setBreakpoints(String path, SourceBreakpoint[] bps, boolean linesStartAt1) {
        int[] lines = new int[bps.length];
        int i = 0;
        for (SourceBreakpoint bp : bps) {
            lines[i] = bp.getLine() - (linesStartAt1 ? 1 : 0);
            i++;
        }
        breakpoints.put(path, lines);
    }

    public StackFrame[] getStackTrace(int threadId, boolean linesStartAt1, boolean columnsStartAt1) {
        return stackTraces.get(threadId).getStackFrames(linesStartAt1, columnsStartAt1);
    }

    public Scope[] getScopes(int frameId, SuspendedState suspendedState) {
        return stackFrames.get(frameId).getScopes(suspendedState);
    }

    /**
     * @param sourceReference the reference of the source
     * @return the module represented by the given sourceReference
     */
    public Module getModule(int sourceReference) {
        synchronized (modules) {
            return modules.get(sourceReference);
        }
    }
}
