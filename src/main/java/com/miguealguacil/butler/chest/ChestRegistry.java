package com.miguealguacil.butler.chest;

import com.google.gson.*;
import com.miguealguacil.butler.AIButler;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ChestRegistry {

    private static final Path FILE = Path.of("butler_chests.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ChestEntry> registry = new LinkedHashMap<>();

    private ChestRegistry() {}

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("chests")) return;
            registry.clear();
            for (JsonElement el : root.getAsJsonArray("chests")) {
                JsonObject obj = el.getAsJsonObject();
                List<String> accepts = new ArrayList<>();
                if (obj.has("accepts"))
                    for (JsonElement a : obj.getAsJsonArray("accepts"))
                        accepts.add(a.getAsString());
                registry.put(obj.get("name").getAsString(), new ChestEntry(
                    obj.get("name").getAsString(),
                    obj.get("x").getAsInt(),
                    obj.get("y").getAsInt(),
                    obj.get("z").getAsInt(),
                    obj.get("dimension").getAsString(),
                    accepts
                ));
            }
            AIButler.LOGGER.info("Butler: loaded {} chest(s)", registry.size());
        } catch (IOException e) {
            AIButler.LOGGER.error("Butler: failed to load {}", FILE, e);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        JsonArray chests = new JsonArray();
        for (ChestEntry e : registry.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", e.name());
            obj.addProperty("x", e.x());
            obj.addProperty("y", e.y());
            obj.addProperty("z", e.z());
            obj.addProperty("dimension", e.dimension());
            JsonArray accepts = new JsonArray();
            e.accepts().forEach(accepts::add);
            obj.add("accepts", accepts);
            chests.add(obj);
        }
        root.add("chests", chests);
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            AIButler.LOGGER.error("Butler: failed to save {}", FILE, e);
        }
    }

    public static void register(ChestEntry entry)  { registry.put(entry.name(), entry); save(); }
    public static void unregister(String name)     { registry.remove(name); save(); }
    public static Optional<ChestEntry> get(String name) { return Optional.ofNullable(registry.get(name)); }
    public static Collection<ChestEntry> getAll()  { return registry.values(); }

    public static void setAccepts(String name, List<String> items) {
        get(name).ifPresent(e -> {
            registry.put(name, new ChestEntry(e.name(), e.x(), e.y(), e.z(), e.dimension(), items));
            save();
        });
    }
}
