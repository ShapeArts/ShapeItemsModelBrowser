package com.shapearts.modelbrowser.ui;

import com.shapearts.modelbrowser.catalog.ModelCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Scrollable 3D item grid, using Minecraft's own item renderer and the active resource pack. */
public final class ModelGridWidget extends AbstractWidget {
    private static final int CELL = 22, HEADER = 21;
    private final Set<String> expanded = new HashSet<>();
    private final Consumer<String> chosen;
    private final List<Tile> tiles = new ArrayList<>();
    private ItemStack source = ItemStack.EMPTY;
    private List<ModelCatalog.Entry> entries = List.of();
    private String filter = "";
    private int scroll = 0, totalHeight = 0;
    private final int columns;

    private record Tile(String group, ModelCatalog.Entry entry, int x, int y, int width) {}

    public ModelGridWidget(int x, int y, int width, int height, Consumer<String> chosen) {
        super(x, y, width, height, Component.literal("Shape model collection"));
        this.chosen = chosen;
        this.columns = Math.max(1, (width - 16) / CELL);
    }
    public void setModels(ItemStack stack, List<ModelCatalog.Entry> models) {
        source = stack.copy();
        entries = models;
        scroll = 0;
        expanded.clear();
        for (ModelCatalog.Entry entry : entries) expanded.add(group(entry.name()));
        layout();
    }
    public void setFilter(String text) {
        filter = text.toLowerCase(Locale.ROOT).trim();
        scroll = 0;
        layout();
    }
    private static String group(String name) {
        String[] known = {"Small Basket", "Medium Basket", "Tall Basket", "Closed Basket", "Market Basket",
            "Small Serving Board", "Large Serving Board", "Cutting Board", "Serving Board",
            "Small Crate", "Medium Crate", "Large Crate", "Short Pedestal", "Medium Pedestal",
            "Tall Pedestal", "Hanging Sack", "Hanging Rope", "Barrel Top", "Flat Plate", "Plate Kit"};
        String lowered = name.toLowerCase(Locale.ROOT);
        String best = "";
        for (String prefix : known) if (lowered.startsWith(prefix.toLowerCase(Locale.ROOT)) && prefix.length() > best.length()) best = prefix;
        if (!best.isEmpty()) return best;
        String[] parts = name.split(" ");
        return parts.length > 1 ? parts[0] + " " + parts[1] : name;
    }
    private void layout() {
        tiles.clear();
        Map<String,List<ModelCatalog.Entry>> grouped = new LinkedHashMap<>();
        for (ModelCatalog.Entry e : entries) {
            if (!e.name().toLowerCase(Locale.ROOT).contains(filter)) continue;
            grouped.computeIfAbsent(group(e.name()), unused -> new ArrayList<>()).add(e);
        }
        int y = 4;
        int cellWidth = Math.max(CELL, (width - 12) / columns);
        for (var group : grouped.entrySet()) {
            tiles.add(new Tile(group.getKey(), null, 4, y, width - 8));
            y += HEADER;
            if (expanded.contains(group.getKey()) || !filter.isEmpty()) {
                int index = 0;
                for (var e : group.getValue()) {
                    tiles.add(new Tile(group.getKey(), e, 6 + (index % columns)*cellWidth,
                            y + (index / columns)*CELL, cellWidth));
                    index++;
                }
                y += ((index + columns - 1)/columns)*CELL + 4;
            }
        }
        totalHeight = y;
        clampScroll();
    }
    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, Math.max(0,totalHeight-height))); }
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // Classic Minecraft inventory palette: light grey frame, recessed grey slots.\n        g.fill(getX(),getY(),getRight(),getBottom(),0xFFC6C6C6);\n        g.outline(getX(),getY(),width,height,0xFF373737);
        g.enableScissor(getX()+2,getY()+2,getRight()-2,getBottom()-2);
        var font = Minecraft.getInstance().font;
        for (Tile tile : tiles) {
            int x=getX()+tile.x(), y=getY()+tile.y()-scroll;
            if (y+Math.max(HEADER,CELL)<getY() || y>getBottom()) continue;
            boolean hover = mx>=x && mx<x+tile.width() && my>=y && my<y+(tile.entry()==null?HEADER:CELL);
            if (tile.entry()==null) {
                g.fill(x,y,x+tile.width(),y+HEADER-2,hover?0xFFAAAAAA:0xFF8B8B8B);
                String mark = expanded.contains(tile.group()) || !filter.isEmpty() ? "▼ " : "▶ ";
                g.text(font, Component.literal(mark+tile.group()),x+7,y+7,0xFF202020);
            } else {
                g.fill(x,y,x+tile.width()-2,y+CELL-2,0xFF373737);\n                g.fill(x+1,y+1,x+tile.width()-3,y+CELL-3,hover?0xFFB6B6B6:0xFF8B8B8B);
                ItemStack preview = source.copy();
                if (!preview.isEmpty()) {
                    preview.set(DataComponents.CUSTOM_NAME,Component.literal(tile.entry().name()));
                    g.item(preview,x+(tile.width()-16)/2,y+2);
                }
                if (hover) g.setTooltipForNextFrame(Component.literal(tile.entry().name()),mx,my);
            }
        }
        g.disableScissor();
        if (totalHeight>height) {
            int barHeight=Math.max(18,height*height/totalHeight);
            int track=height-barHeight;
            int pos=(int)(track*(scroll/(double)Math.max(1,totalHeight-height)));
            g.fill(getRight()-4,getY()+pos,getRight()-2,getY()+pos+barHeight,0xFF777777);
        }
    }
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(),event.y())) return false;
        int localX=(int)event.x()-getX(), localY=(int)event.y()-getY()+scroll;
        for (Tile tile : tiles) {
            int h=tile.entry()==null?HEADER:CELL;
            if (localX>=tile.x()&&localX<tile.x()+tile.width()&&localY>=tile.y()&&localY<tile.y()+h) {
                if (tile.entry()==null) {
                    if (!expanded.add(tile.group())) expanded.remove(tile.group());
                    layout();
                } else chosen.accept(tile.entry().name());
                return true;
            }
        }
        return true;
    }
    @Override
    public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        if (!isMouseOver(mx,my)) return false;
        scroll -= (int)Math.round(dy*18);
        clampScroll();
        return true;
    }
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
