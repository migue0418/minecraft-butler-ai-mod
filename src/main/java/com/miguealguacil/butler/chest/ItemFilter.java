package com.miguealguacil.butler.chest;

import com.miguealguacil.butler.AIButler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ItemFilter {

    private ItemFilter() {}

    /**
     * Returns true if {@code stack} matches any entry in {@code accepts}.
     * Empty list = accept nothing (no filter configured).
     * "#generic" = accept any item (explicit catch-all).
     * "#namespace:tag" = accept items belonging to that tag (e.g. "#minecraft:logs").
     * "namespace:item" = accept that exact item ID (e.g. "minecraft:oak_log").
     */
    public static boolean matches(ItemStack stack, List<String> accepts) {
        if (accepts.isEmpty()) return false;
        for (String filter : accepts) {
            if (filter.equals("#generic")) return true;
            try {
                if (filter.startsWith("#")) {
                    Identifier tagId = Identifier.parse(filter.substring(1));
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                    if (stack.is(tag)) return true;
                } else {
                    Identifier itemId = Identifier.parse(filter);
                    Item found = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                    if (found != null && stack.is(found)) return true;
                }
            } catch (Exception e) {
                AIButler.LOGGER.warn("Butler: filtro inválido en accepts: '{}'", filter);
            }
        }
        return false;
    }
}
