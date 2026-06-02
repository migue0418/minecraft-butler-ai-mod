package com.miguealguacil.butler.context;

import com.miguealguacil.butler.chest.ChestEntry;
import com.miguealguacil.butler.chest.ChestRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldContextCollector {

    private WorldContextCollector() {}

    public static WorldContext collect(ServerPlayer player, MinecraftServer server) {
        ServerLevel level = (ServerLevel) player.level();
        return new WorldContext(
                collectPlayer(player),
                collectChests(server),
                new WorldContext.NearbyContext(
                        collectAnimals(player, level),
                        collectCrops(player, level)
                )
        );
    }

    private static WorldContext.PlayerContext collectPlayer(ServerPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        // Use Container interface to access all inventory slots safely
        Container inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                counts.merge(id, stack.getCount(), Integer::sum);
            }
        }
        List<WorldContext.ItemEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            entries.add(new WorldContext.ItemEntry(e.getKey(), e.getValue()));
        }
        BlockPos pos = player.blockPosition();
        return new WorldContext.PlayerContext(entries, pos.getX(), pos.getY(), pos.getZ());
    }

    private static List<WorldContext.ChestContext> collectChests(MinecraftServer server) {
        Collection<ChestEntry> all = ChestRegistry.getAll();
        List<WorldContext.ChestContext> result = new ArrayList<>();
        for (ChestEntry entry : all) {
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.dimension())));
            if (level == null) continue;
            BlockPos pos = entry.blockPos();
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) continue;
            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    counts.merge(id, stack.getCount(), Integer::sum);
                }
            }
            List<WorldContext.ItemEntry> items = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                items.add(new WorldContext.ItemEntry(e.getKey(), e.getValue()));
            }
            result.add(new WorldContext.ChestContext(entry.name(), items));
        }
        return result;
    }

    private static List<WorldContext.AnimalGroup> collectAnimals(ServerPlayer player, ServerLevel level) {
        double r = 30.0;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        AABB box = new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box, e -> true);
        Map<String, Integer> counts = new HashMap<>();
        for (Animal animal : animals) {
            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString();
            counts.merge(typeId, 1, Integer::sum);
        }
        List<WorldContext.AnimalGroup> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            result.add(new WorldContext.AnimalGroup(e.getKey(), e.getValue()));
        }
        result.sort((a, b) -> b.count() - a.count());
        return result;
    }

    private static List<WorldContext.CropGroup> collectCrops(ServerPlayer player, ServerLevel level) {
        int r = 20;
        int dy = 5;
        int px = player.getBlockX(), py = player.getBlockY(), pz = player.getBlockZ();
        Map<String, int[]> counts = new HashMap<>();
        for (int bx = px - r; bx <= px + r; bx++) {
            for (int bz = pz - r; bz <= pz + r; bz++) {
                for (int by = py - dy; by <= py + dy; by++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (!level.isLoaded(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof CropBlock crop)) continue;
                    String blockId = BuiltInRegistries.BLOCK.getKey(crop).toString();
                    int age = crop.getAge(state);
                    int maxAge = crop.getMaxAge();
                    counts.computeIfAbsent(blockId, k -> new int[]{0, 0});
                    if (age >= maxAge) counts.get(blockId)[0]++;
                    else counts.get(blockId)[1]++;
                }
            }
        }
        List<WorldContext.CropGroup> result = new ArrayList<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            result.add(new WorldContext.CropGroup(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        return result;
    }
}
