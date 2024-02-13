package dev.carbons.carpet_dap.debug;

import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;

public class CarpetDebugCommandOutput implements CommandOutput {
    @Override
    public void sendMessage(Text message) {

    }

    @Override
    public boolean shouldReceiveFeedback() {
        return false;
    }

    @Override
    public boolean shouldTrackOutput() {
        return false;
    }

    @Override
    public boolean shouldBroadcastConsoleToOps() {
        return false;
    }
}
