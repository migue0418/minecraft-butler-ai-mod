package com.miguealguacil.butler.command;

import com.miguealguacil.butler.AIButler;
import com.miguealguacil.butler.action.ButlerAction;
import com.miguealguacil.butler.action.ButlerActionExecutor;
import com.miguealguacil.butler.chest.ChestEntry;
import com.miguealguacil.butler.chest.ChestItemMover;
import com.miguealguacil.butler.chest.ChestRegistry;
import com.miguealguacil.butler.chest.ItemFilter;
import com.miguealguacil.butler.entity.AlfredEntities;
import com.miguealguacil.butler.entity.AlfredEntity;
import com.miguealguacil.butler.context.WorldContext;
import com.miguealguacil.butler.context.WorldContextCollector;
import com.miguealguacil.butler.http.ButlerHttpClient;
import com.miguealguacil.butler.state.ButlerState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ButlerCommand {
    private ButlerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("butler")
                .then(Commands.literal("ping")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Alfred está operativo."), false);
                        return 1;
                    }))
                .then(Commands.literal("pos")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        BlockPos pos = player.blockPosition();
                        source.sendSuccess(
                            () -> Component.literal("Tu posición es: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(Commands.literal("here")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        BlockPos pos = player.blockPosition();
                        ButlerState.setSavedPosition(pos);
                        source.sendSuccess(
                            () -> Component.literal("Posición guardada para Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(Commands.literal("target")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        BlockPos pos = ButlerState.getSavedPosition();
                        if (pos != null) {
                            source.sendSuccess(
                                () -> Component.literal("Objetivo actual de Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                                false);
                        } else {
                            source.sendSuccess(
                                () -> Component.literal("No hay posición guardada. Usa /butler here primero."),
                                false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("mock")
                    .executes(ctx -> {
                        ButlerActionExecutor.execute(
                            new ButlerAction("speak", "He recibido una acción mock correctamente.", null, null, null),
                            ctx.getSource());
                        return 1;
                    }))
                .then(Commands.literal("spawn")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        ServerLevel level = source.getLevel();
                        Optional<AlfredEntity> existing = ButlerState.findAlfred(level);
                        if (existing.isPresent()) {
                            existing.get().teleportTo(player.getX(), player.getY(), player.getZ());
                            source.sendSuccess(() -> Component.literal("Alfred ha sido teleportado a tu posición."), false);
                        } else {
                            AlfredEntity alfred = new AlfredEntity(AlfredEntities.ALFRED, level);
                            alfred.setPos(player.getX(), player.getY(), player.getZ());
                            level.addFreshEntity(alfred);
                            source.sendSuccess(() -> Component.literal("Alfred ha aparecido."), false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("follow")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        source.getPlayerOrException();
                        Optional<AlfredEntity> alfred = ButlerState.findAlfred(source.getLevel());
                        if (alfred.isEmpty()) {
                            source.sendSuccess(() -> Component.literal("Alfred no está en el mundo. Usa /butler spawn primero."), false);
                        } else {
                            alfred.get().setFollowing(true);
                            source.sendSuccess(() -> Component.literal("[Alfred] Siguiéndote."), false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("stop")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        source.getPlayerOrException();
                        Optional<AlfredEntity> alfred = ButlerState.findAlfred(source.getLevel());
                        if (alfred.isEmpty()) {
                            source.sendSuccess(() -> Component.literal("Alfred no está en el mundo."), false);
                        } else {
                            alfred.get().setFollowing(false);
                            source.sendSuccess(() -> Component.literal("[Alfred] Me detengo."), false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("ask")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            MinecraftServer server = source.getServer();
                            String message = StringArgumentType.getString(ctx, "message");
                            ServerPlayer player = source.getPlayerOrException();

                            source.sendSuccess(
                                () -> Component.literal("[Alfred] Procesando..."), false);

                            WorldContext worldCtx = WorldContextCollector.collect(player, server);

                            ButlerHttpClient.sendAsync(message, worldCtx)
                                .thenAccept(actions ->
                                    server.execute(() ->
                                        actions.forEach(action ->
                                            ButlerActionExecutor.execute(action, source))
                                    )
                                )
                                .exceptionally(ex -> {
                                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                    String msg;
                                    if (cause instanceof ButlerHttpClient.AuthException) {
                                        msg = "[Alfred] No pude autenticarme con el servidor.";
                                    } else if (cause instanceof ButlerHttpClient.ServerException) {
                                        msg = "[Alfred] Error del servidor.";
                                    } else {
                                        msg = "[Alfred] No pude contactar con el servidor.";
                                    }
                                    AIButler.LOGGER.error("Butler ask error", cause);
                                    server.execute(() ->
                                        source.sendSuccess(() -> Component.literal(msg), false));
                                    return null;
                                });

                            return 1;
                        })))
                .then(Commands.literal("chest")
                    .then(Commands.literal("register")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> registerChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("unregister")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> unregisterChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("list")
                        .executes(ctx -> listChests(ctx.getSource())))
                    .then(Commands.literal("inspect")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> inspectChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("setaccepts")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("items", StringArgumentType.greedyString())
                                .executes(ctx -> setAccepts(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "items"))))))
                    .then(Commands.literal("move")
                        .then(Commands.argument("from", StringArgumentType.word())
                            .then(Commands.argument("to", StringArgumentType.word())
                                .executes(ctx -> moveChest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "from"),
                                    StringArgumentType.getString(ctx, "to"))))))
                    .then(Commands.literal("distribute")
                        .then(Commands.argument("src", StringArgumentType.word())
                            .executes(ctx -> distributeChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "src")))))
                )
        );
    }

    private static int registerChest(CommandSourceStack source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(4.5));
        BlockHitResult hit = level.clip(
            new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS
                || !(level.getBlockEntity(hit.getBlockPos()) instanceof Container)) {
            source.sendSuccess(() -> Component.literal("[Alfred] No hay ningún cofre ahí."), false);
            return 0;
        }
        if (ChestRegistry.get(name).isPresent()) {
            source.sendSuccess(() -> Component.literal(
                "[Alfred] Ya existe un cofre con el nombre '" + name + "'. Usa unregister primero."), false);
            return 0;
        }
        BlockPos pos = hit.getBlockPos();
        String dimRaw = level.dimension().toString();
        int dimSep = dimRaw.lastIndexOf(" / ");
        String dim = (dimSep >= 0) ? dimRaw.substring(dimSep + 3, dimRaw.length() - 1) : "minecraft:overworld";
        ChestRegistry.register(new ChestEntry(name, pos.getX(), pos.getY(), pos.getZ(), dim, new ArrayList<>()));
        source.sendSuccess(() -> Component.literal(
            "[Alfred] Cofre registrado como '" + name + "' en " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "."), false);
        return 1;
    }

    private static int unregisterChest(CommandSourceStack source, String name) {
        if (ChestRegistry.get(name).isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                "[Alfred] No hay ningún cofre registrado con el nombre '" + name + "'."), false);
            return 0;
        }
        ChestRegistry.unregister(name);
        source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + name + "' eliminado del registro."), false);
        return 1;
    }

    private static int listChests(CommandSourceStack source) {
        Collection<ChestEntry> all = ChestRegistry.getAll();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Alfred] No hay cofres registrados."), false);
            return 0;
        }
        for (ChestEntry e : all) {
            source.sendSuccess(() -> Component.literal(
                "- " + e.name() + "  (" + e.x() + ", " + e.y() + ", " + e.z() + ")  "
                + e.dimension() + "  acepta: " + e.accepts().size() + " items"), false);
        }
        return 1;
    }

    private static int inspectChest(CommandSourceStack source, String name) {
        Optional<ChestEntry> opt = ChestRegistry.get(name);
        if (opt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + name + "' no registrado."), false);
            return 0;
        }
        ChestEntry entry = opt.get();
        ServerLevel level = source.getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.dimension())));
        if (level == null) {
            source.sendSuccess(() -> Component.literal("[Alfred] Dimensión no disponible."), false);
            return 0;
        }
        BlockEntity be = level.getBlockEntity(entry.blockPos());
        if (!(be instanceof Container container)) {
            source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en esa posición."), false);
            return 0;
        }
        boolean empty = true;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                empty = false;
                final int slot = i;
                final String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                final int count = stack.getCount();
                source.sendSuccess(() -> Component.literal(
                    "[slot " + slot + "] " + itemId + " x" + count), false);
            }
        }
        if (empty)
            source.sendSuccess(() -> Component.literal("[Alfred] El cofre '" + name + "' está vacío."), false);
        return 1;
    }

    private static int setAccepts(CommandSourceStack source, String name, String itemsStr) {
        if (ChestRegistry.get(name).isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + name + "' no registrado."), false);
            return 0;
        }
        List<String> items = Arrays.asList(itemsStr.trim().split("\\s+"));
        ChestRegistry.setAccepts(name, items);
        source.sendSuccess(() -> Component.literal(
            "[Alfred] Cofre '" + name + "' ahora acepta " + items.size() + " tipos de items."), false);
        return 1;
    }

    private static int moveChest(CommandSourceStack source, String from, String to) {
        Optional<ChestEntry> fromOpt = ChestRegistry.get(from);
        Optional<ChestEntry> toOpt   = ChestRegistry.get(to);
        if (fromOpt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + from + "' no registrado."), false); return 0;
        }
        if (toOpt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + to + "' no registrado."), false); return 0;
        }
        ServerLevel fromLevel = source.getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, Identifier.parse(fromOpt.get().dimension())));
        ServerLevel toLevel = source.getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, Identifier.parse(toOpt.get().dimension())));
        if (fromLevel == null || toLevel == null) {
            source.sendSuccess(() -> Component.literal("[Alfred] Dimensión no disponible."), false); return 0;
        }
        BlockEntity fromBe = fromLevel.getBlockEntity(fromOpt.get().blockPos());
        BlockEntity toBe   = toLevel.getBlockEntity(toOpt.get().blockPos());
        if (!(fromBe instanceof Container fromCont)) {
            source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en '" + from + "'."), false); return 0;
        }
        if (!(toBe instanceof Container toCont)) {
            source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en '" + to + "'."), false); return 0;
        }
        int[] result = ChestItemMover.tryMoveAll(fromCont, toCont);
        final int moved = result[0], remaining = result[1];
        source.sendSuccess(() -> Component.literal(
            "[Alfred] Movidos " + moved + " items de '" + from + "' a '" + to + "'."), false);
        if (remaining > 0)
            source.sendSuccess(() -> Component.literal(
                "[Alfred] El cofre '" + to + "' está lleno. Quedan " + remaining + " items sin colocar."), false);
        return 1;
    }

    private static int distributeChest(CommandSourceStack source, String srcName) {
        Optional<ChestEntry> srcOpt = ChestRegistry.get(srcName);
        if (srcOpt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + srcName + "' no registrado."), false);
            return 0;
        }
        ChestEntry srcEntry = srcOpt.get();
        ServerLevel srcLevel = source.getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, Identifier.parse(srcEntry.dimension())));
        if (srcLevel == null) {
            source.sendSuccess(() -> Component.literal("[Alfred] Dimensión no disponible."), false); return 0;
        }
        BlockEntity srcBe = srcLevel.getBlockEntity(srcEntry.blockPos());
        if (!(srcBe instanceof Container srcCont)) {
            source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en '" + srcName + "'."), false); return 0;
        }

        int totalMoved = 0, unassigned = 0, didNotFit = 0;

        for (int i = 0; i < srcCont.getContainerSize(); i++) {
            ItemStack slot = srcCont.getItem(i);
            if (slot.isEmpty()) continue;

            Optional<ChestEntry> destOpt = ChestRegistry.getAll().stream()
                .filter(e -> !e.name().equals(srcName) && ItemFilter.matches(slot, e.accepts()))
                .findFirst();

            if (destOpt.isEmpty()) { unassigned += slot.getCount(); continue; }

            ChestEntry destEntry = destOpt.get();
            ServerLevel destLevel = source.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(destEntry.dimension())));
            if (destLevel == null) { unassigned += slot.getCount(); continue; }
            BlockEntity destBe = destLevel.getBlockEntity(destEntry.blockPos());
            if (!(destBe instanceof Container destCont)) { unassigned += slot.getCount(); continue; }

            int count = slot.getCount();
            int moved = ChestItemMover.tryInsert(slot.copy(), destCont);

            if (moved >= count) {
                srcCont.setItem(i, ItemStack.EMPTY);
            } else if (moved > 0) {
                slot.shrink(moved);
                srcCont.setItem(i, slot);
                didNotFit += (count - moved);
            } else {
                didNotFit += count;
            }
            totalMoved += moved;
        }

        if (totalMoved > 0) srcCont.setChanged();
        final int fm = totalMoved, fu = unassigned, fd = didNotFit;
        source.sendSuccess(() -> Component.literal(
            "[Alfred] Distribuidos " + fm + " items. " + fu + " sin asignar. " + fd + " no cupieron en destino."), false);
        return 1;
    }
}
