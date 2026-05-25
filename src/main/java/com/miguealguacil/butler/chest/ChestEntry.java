package com.miguealguacil.butler.chest;

import net.minecraft.core.BlockPos;
import java.util.List;

public record ChestEntry(
    String name,
    int x, int y, int z,
    String dimension,
    List<String> accepts
) {
    public BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }
}
