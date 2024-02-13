package dev.carbons.carpet_dap.debug;

import carpet.script.Module;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public record ModuleSource(@Nonnull Type type, @Nonnull String path) {
    /**
     * Since carpet.script.Module can't be extended (it's a record), it is associated with its source as soon as it's
     * created. This is a weak map not to prevent garbage collection.
     */
    // TODO: Is synchronize needed? Can modules even be instantiated from multiple threads?
    //  Maybe other extensions can do it?
    private static final Map<Module, ModuleSource> moduleSources = Collections.synchronizedMap(new WeakHashMap<>());

    public static void addModule(@Nonnull Module module, @Nonnull String path) {
        moduleSources.put(module, new ModuleSource(Type.FILESYSTEM, path));
    }

    public static void addInternalModule(@Nonnull Module module, @Nonnull String path) {
        moduleSources.put(module, new ModuleSource(Type.JAR, path));
    }

    @Nonnull
    public static ModuleSource getModuleSource(@Nonnull Module module) {
        ModuleSource moduleSource = moduleSources.get(module);
        if (moduleSource == null) {
            throw new NullPointerException();
        }
        return moduleSource;
    }

    public enum Type {
        /**
         * The module comes from a filesystem path, e.g. ~/.minecraft/scripts/example.sc.
         */
        FILESYSTEM,
        /**
         * The module is an internal Carpet module (or from a Carpet extension) and is loaded from within the mod's JAR
         * file.
         */
        JAR,
    }
}
