package dev.carbons.carpet_dap.mixin;

import carpet.script.Module;
import dev.carbons.carpet_dap.debug.ModuleSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.nio.file.Path;

// This is necessary to get the right information about
@SuppressWarnings("unused")
@Mixin(value = Module.class, remap = false)
public class ModuleMixin {
    @Inject(method = "fromPath", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private static void injectedFromPath(Path path, CallbackInfoReturnable<Module> ci) {
        Module module = ci.getReturnValue();
        String modulePath = path.toAbsolutePath().toString();
        ModuleSource.setModuleSource(module, new ModuleSource(ModuleSource.Type.FILESYSTEM, modulePath));
    }

    @Inject(method = "fromJarPathWithCustomName", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private static void injectedFromJarPath(String fullPath, String customName, boolean isLibrary, CallbackInfoReturnable<Module> ci) {
        Module module = ci.getReturnValue();
        ModuleSource.setModuleSource(module, new ModuleSource(ModuleSource.Type.JAR, fullPath));
    }
}
