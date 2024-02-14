package dev.carbons.carpet_dap.adapter;

import net.minecraft.util.math.Vec3d;

import javax.annotation.Nonnull;

/**
 * A player value as viewed by the debugging client.
 * @param name
 * @param pos
 */
public record PlayerValue(@Nonnull String name, @Nonnull Vec3d pos) {
}
