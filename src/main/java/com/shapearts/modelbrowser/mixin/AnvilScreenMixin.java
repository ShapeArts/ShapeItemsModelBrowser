package com.shapearts.modelbrowser.mixin;

import com.shapearts.modelbrowser.catalog.ModelCatalog;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Native widgets share the vanilla anvil screen and preserve ordinary slot functionality. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {
    @Shadow private EditBox name;
    @Unique private final Set<String> shape$expanded = new HashSet<>();
    @Unique private List<ModelCatalog.Entry> shape$entries = List.of();
    @Unique private final List<Button> shape$dynamic = new ArrayList<>();
    @Unique private EditBox shape$search;
    @Unique private Button shape$toggle;
    @Unique private int shape$page = 0;
    @Unique private boolean shape$show = true;
    @Unique private ItemStack shape$last = ItemStack.EMPTY;
    @Unique private static final int SHAPE_ROWS = 7;

    protected AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, net.minecraft.resources.ResourceLocation texture) {
        super(menu, inventory, title, texture);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void shape$init(CallbackInfo ci) {
        shape$toggle = Button.builder(Component.literal("Models"), b -> {
            shape$show = !shape$show;
            shape$rebuild();
        }).bounds(this.leftPos - 73, this.topPos + 3, 70, 20).build();
        addRenderableWidget(shape$toggle);
        shape$search = new EditBox(font, leftPos - 169, topPos + 27, 164, 18, Component.literal("Search models"));
        shape$search.setHint(Component.literal("Search..."));
        shape$search.setResponder(value -> { shape$page = 0; shape$rebuild(); });
        addRenderableWidget(shape$search);
        shape$refresh();
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void shape$tick(CallbackInfo ci) {
        ItemStack stack = this.menu.getSlot(0).getItem();
        if (!ItemStack.isSameItemSameComponents(stack, shape$last)) {
            shape$last = stack.copy();
            shape$page = 0;
            shape$refresh();
        }
    }

    @Unique private void shape$refresh() {
        ItemStack stack = this.menu.getSlot(0).getItem();
        shape$entries = stack.isEmpty() ? List.of() : ModelCatalog.forItem(stack.getItem());
        shape$rebuild();
    }

    @Unique private static String shape$group(String name) {
        String[] known = {"Small Basket", "Medium Basket", "Tall Basket", "Closed Basket", "Market Basket",
            "Cutting Board", "Serving Board", "Small Serving Board", "Large Serving Board",
            "Small Crate", "Medium Crate", "Large Crate", "Short Pedestal", "Medium Pedestal",
            "Tall Pedestal", "Hanging Sack", "Hanging Rope", "Barrel Top", "Flat Plate", "Plate Kit"};
        String lowered = name.toLowerCase(Locale.ROOT);
        String best = "";
        for (String prefix : known) if (lowered.startsWith(prefix.toLowerCase(Locale.ROOT)) && prefix.length() > best.length()) best = prefix;
        if (!best.isEmpty()) return best;
        String[] parts = name.split(" ");
        return parts.length > 1 ? parts[0] + " " + parts[1] : name;
    }

    @Unique private void shape$rebuild() {
        for (Button b : shape$dynamic) removeWidget(b);
        shape$dynamic.clear();
        if (shape$search == null) return;
        shape$search.visible = shape$show;
        if (shape$toggle != null) shape$toggle.setMessage(Component.literal(shape$show ? "Hide Models" : "Models"));
        if (!shape$show) return;
        String query = shape$search.getValue().trim().toLowerCase(Locale.ROOT);
        Map<String, List<ModelCatalog.Entry>> groups = new LinkedHashMap<>();
        for (var entry : shape$entries) {
            if (!entry.name().toLowerCase(Locale.ROOT).contains(query)) continue;
            groups.computeIfAbsent(shape$group(entry.name()), unused -> new ArrayList<>()).add(entry);
        }
        List<ShapeRow> rows = new ArrayList<>();
        for (var group : groups.entrySet()) {
            rows.add(new ShapeRow(group.getKey(), null));
            if (shape$expanded.contains(group.getKey()) || !query.isEmpty())
                for (var entry : group.getValue()) rows.add(new ShapeRow(group.getKey(), entry));
        }
        int pages = Math.max(1, (rows.size() + SHAPE_ROWS - 1) / SHAPE_ROWS);
        shape$page = Math.min(shape$page, pages - 1);
        int x = leftPos - 169, y = topPos + 49;
        for (int i = shape$page * SHAPE_ROWS; i < Math.min(rows.size(), (shape$page + 1) * SHAPE_ROWS); i++) {
            ShapeRow row = rows.get(i);
            int yy = y + (i - shape$page * SHAPE_ROWS) * 20;
            if (row.entry() == null) {
                boolean expanded = shape$expanded.contains(row.group()) || !query.isEmpty();
                shape$button(Component.literal((expanded ? "▾ " : "▸ ") + row.group()), x, yy, 164, b -> {
                    if (!shape$expanded.add(row.group())) shape$expanded.remove(row.group());
                    shape$page = 0; shape$rebuild();
                });
            } else {
                String label = row.entry().name();
                shape$button(Component.literal("  " + (label.length() > 23 ? label.substring(0, 22) + "…" : label)), x, yy, 164, b -> {
                    if (name != null) name.setValue(row.entry().name());
                });
            }
        }
        shape$button(Component.literal("<"), x, y + SHAPE_ROWS * 20 + 2, 25, b -> {shape$page = Math.max(0, shape$page - 1);shape$rebuild();});
        shape$button(Component.literal((shape$page+1)+"/"+pages), x + 26, y + SHAPE_ROWS * 20 + 2, 112, b -> {});
        shape$button(Component.literal(">"), x + 139, y + SHAPE_ROWS * 20 + 2, 25, b -> {shape$page=Math.min(pages-1,shape$page+1);shape$rebuild();});
    }

    @Unique private record ShapeRow(String group, ModelCatalog.Entry entry) { }
    @Unique private void shape$button(Component label, int x, int y, int width, java.util.function.Consumer<Button> action) {
        Button b = Button.builder(label, action::accept).bounds(x, y, width, 18).build();
        addRenderableWidget(b);
        shape$dynamic.add(b);
    }
}
