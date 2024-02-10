package dev.carbons.carpet_dap.dap;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class DapLayer {
    private final CarpetDebugAdapter adapter;
    private final IDebugProtocolClient client;
    private final BlockingQueue<Record> queue;

    DapLayer(CarpetDebugAdapter adapter, IDebugProtocolClient client, BlockingQueue<Record> queue) {
        this.client = client;
        this.queue = queue;
        this.adapter = adapter;
    }

    public Map<Source, SourceBreakpoint[]> getBreakpoints() {
        return adapter.getBreakpoints();
    }

    public IDebugProtocolClient getClient() {
        return client;
    }

    public BlockingQueue<Record> getQueue() {
        return queue;
    }
}
