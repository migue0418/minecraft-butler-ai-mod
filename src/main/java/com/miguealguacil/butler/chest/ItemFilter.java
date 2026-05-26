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
     * Empty list = accept everything.
     * Entries starting with '#' are treated as item tags (e.g. "#minecraft:logs").
     * All other entries are treated as exact item IDs (e.g. "minecraft:oak_log").
     */
    public static boolean matches(ItemStack stack, List<String> accepts) {
        if (accepts.isEmpty()) return true;
        for (String filter : accepts) {
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
