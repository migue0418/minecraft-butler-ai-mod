package com.miguealguacil.butler.state;

import com.miguealguacil.butler.entity.AlfredEntities;
import com.miguealguacil.butler.entity.AlfredEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

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

    public static Optional<AlfredEntity> findAlfred(ServerLevel level) {
        List<? extends AlfredEntity> found = level.getEntities(AlfredEntities.ALFRED, e -> true);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }
}