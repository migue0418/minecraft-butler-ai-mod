package com.miguealguacil.butler.chest;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class ChestItemMover {

    private ChestItemMover() {}

    /**
     * Inserts as many items from {@code stack} as possible into {@code dest}.
     * Modifies {@code dest} in place. Does NOT modify {@code stack}.
     * Calls {@code dest.setChanged()} if any items were inserted.
     *
     * @return number of items actually inserted
     */
    public static int tryInsert(ItemStack stack, Container dest) {
        int inserted = 0;
        int remaining = stack.getCount();

        // Pass 1: merge with existing matching stacks
        for (int i = 0; i < dest.getContainerSize() && remaining > 0; i++) {
            ItemStack slot = dest.getItem(i);
            if (!slot.isEmpty() && slot.getItem() == stack.getItem()) {
                int canFit = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                if (canFit > 0) {
                    slot.grow(canFit);
                    dest.setItem(i, slot);
                    remaining -= canFit;
                    inserted += canFit;
                }
            }
        }

        // Pass 2: fill empty slots
        for (int i = 0; i < dest.getContainerSize() && remaining > 0; i++) {
            if (dest.getItem(i).isEmpty()) {
                int canFit = Math.min(remaining, stack.getMaxStackSize());
                dest.setItem(i, stack.copyWithCount(canFit));
                remaining -= canFit;
                inserted += canFit;
            }
        }

        if (inserted > 0) dest.setChanged();
        return inserted;
    }

    /**
     * Moves as much of each slot in {@code source} as possible into {@code dest}.
     * Modifies both containers in place.
     *
     * @return int[]{totalMoved, totalRemaining}
     */
    public static int[] tryMoveAll(Container source, Container dest) {
        int totalMoved = 0;
        int totalRemaining = 0;

        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack slot = source.getItem(i);
            if (slot.isEmpty()) continue;

            int count = slot.getCount();
            int moved = tryInsert(slot.copy(), dest);

            if (moved >= count) {
                source.setItem(i, ItemStack.EMPTY);
            } else if (moved > 0) {
                slot.shrink(moved);
                source.setItem(i, slot);
            }

            totalMoved     += moved;
            totalRemaining += (count - moved);
        }

        if (totalMoved > 0) source.setChanged();
        return new int[]{totalMoved, totalRemaining};
    }
}
