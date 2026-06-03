package com.miguealguacil.butler.context;

import java.util.List;

public record WorldContext(
        PlayerContext player,
        List<ChestContext> chests,
        NearbyContext nearby
) {
    public record PlayerContext(
            List<ItemEntry> inventory,
            int x, int y, int z
    ) {}

    public record ChestContext(
            String name,
            List<ItemEntry> items
    ) {}

    public record NearbyContext(
            List<AnimalGroup> animals,
            List<AnimalGroup> monsters,
            List<CropGroup> crops
    ) {}

    public record ItemEntry(
            String item,
            int count
    ) {}

    public record AnimalGroup(
            String type,
            int count
    ) {}

    public record CropGroup(
            String type,
            int mature,
            int growing
    ) {}
}
