package dev.carbons.carpet_dap.debug;

import carpet.script.Module;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 *
 * @param type
 * @param path
 */
public record ModuleSource(@Nonnull Type type, @Nonnull String path) {
    /**
     * Since carpet.script.Module can't be extended (it's a record), it is associated with its source as soon as it's
     * created. This is a weak map not to prevent garbage collection.
     */
    // TODO: Is synchronize needed? Can modules even be instantiated from multiple threads?
    //  Maybe other extensions can do it?
    private static final Map<Module, ModuleSource> moduleSources = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Add the path of a Scarpet module.
     * @param module scarpet module.
     * @param moduleSource module source.
     */
    public static void setModuleSource(@Nonnull Module module, @Nonnull ModuleSource moduleSource) {
        moduleSources.put(module, moduleSource);
    }

    /**
     * Returns the source of the given module.
     * @param module a Scarpet module.
     * @return source of the given module.
     */
    @Nonnull
    public static ModuleSource getModuleSource(@Nonnull Module module) {
        return Objects.requireNonNull(moduleSources.get(module));
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
