package dev.carbons.carpet_dap.adapter;

/**
 * <p>Launch parameters sent by the debugger client.</p>
 * <p>These are also the reference debugger clients (extensions or plugins for editors and so on) should use for the
 * launch request.</p>
 * @param program Absolute path to the scarpet app to debug
 * @param noDebug Disable debugging and just execute the app
 * @param trace Enable debugging messages from the debug adapter
 * @param stopOnEvent Stop every time an event is emitted
 */
public record LaunchParams(String program, boolean noDebug, boolean trace, boolean stopOnEvent) {}
