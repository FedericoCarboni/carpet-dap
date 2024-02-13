package dev.carbons.carpet_dap.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface DebugAdapterTransport {
    InputStream getInputStream();
    OutputStream getOutputStream();
}
