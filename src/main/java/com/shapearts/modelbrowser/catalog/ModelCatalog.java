package com.shapearts.modelbrowser.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads active item definitions, not a hardcoded copy of the user's resource pack. */
public final class ModelCatalog {
    public record Entry(String name, Identifier model) { }
    private static final Map<Item, List<Entry>> CACHE = new HashMap<>();
    private ModelCatalog() { }

    public static List<Entry> forItem(Item item) {
        return CACHE.computeIfAbsent(item, ModelCatalog::load);
    }
    public static void invalidate() { CACHE.clear(); }

    private static List<Entry> load(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            walk(root.getAsJsonObject("model"), entries);
        } catch (Exception ignored) { return List.of(); }
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    private static void walk(JsonObject node, List<Entry> out) {
        if (node == null) return;
        String type = node.has("type") ? node.get("type").getAsString() : "";
        if ("minecraft:select".equals(type) && "minecraft:component".equals(string(node, "property"))
                && "minecraft:custom_name".equals(string(node, "component"))) {
            JsonArray cases = node.getAsJsonArray("cases");
            if (cases != null) for (JsonElement element : cases) {
                JsonObject option = element.getAsJsonObject();
                JsonObject model = option.getAsJsonObject("model");
                if (model == null || !"minecraft:model".equals(string(model, "type"))) continue;
                if (!option.has("when") || !option.get("when").isJsonPrimitive()) continue;
                try {
                    String name = option.get("when").getAsString();
                    Identifier modelId = Identifier.parse(model.get("model").getAsString());
                    out.add(new Entry(name, modelId));
                } catch (Exception ignored) { }
            }
        }
        if (node.has("fallback") && node.get("fallback").isJsonObject()) walk(node.getAsJsonObject("fallback"), out);
    }
    private static String string(JsonObject node, String key) {
        return node.has(key) ? node.get(key).getAsString() : "";
    }
}
