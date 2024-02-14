package dev.carbons.carpet_dap.adapter;

import net.minecraft.util.math.Vec3d;

import javax.annotation.Nonnull;
import java.util.UUID;

public record EntityValue(@Nonnull String name, @Nonnull UUID uuid, int id, @Nonnull Vec3d pos) {
}
