package com.miguealguacil.butler.state;

import net.minecraft.core.BlockPos;

public final class ButlerState {
    private static BlockPos savedPosition = null;

    private ButlerState() {}

    public static void setSavedPosition(BlockPos pos) {
        savedPosition = pos;
    }

    public static BlockPos getSavedPosition() {
        return savedPosition;
    }

    public static boolean hasSavedPosition() {
        return savedPosition != null;
    }
}