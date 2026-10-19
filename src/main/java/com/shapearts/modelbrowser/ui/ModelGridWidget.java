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
    private static final int CELL = 36, HEADER = 15;
    private final Set<String> expanded = new HashSet<>();
    private final Consumer<String> chosen;
    private final List<Tile> tiles = new ArrayList<>();
    private ItemStack source = ItemStack.EMPTY;
    private List<ModelCatalog.Entry> entries = List.of();
    private String filter = "";
    private int scroll = 0, totalHeight = 0;
    private boolean draggingScrollbar = false;
    private double scrollbarDragOffset = 0;
    private final int columns;

    private record Tile(String group, String label, int depth, ModelCatalog.Entry entry, int x, int y, int width) {}
    private static final class Folder {
        final String path, label;
        final Map<String, Folder> children = new LinkedHashMap<>();
        final List<ModelCatalog.Entry> models = new ArrayList<>();
        Folder(String path, String label) { this.path=path; this.label=label; }
    }
    private static String label(String path) {
        String[] words = path.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
    private static String[] folders(ModelCatalog.Entry entry) {
        String path = entry.model().getPath();
        if (path.startsWith("item/")) path=path.substring(5);
        int last=path.lastIndexOf('/');
        return last < 0 ? new String[0] : path.substring(0,last).split("/");
    }

    public ModelGridWidget(int x, int y, int width, int height, Consumer<String> chosen) {
        super(x, y, width, height, Component.literal("Shape model collection"));
        this.chosen = chosen;
        this.columns = 5;
    }
    public void setModels(ItemStack stack, List<ModelCatalog.Entry> models) {
        source = stack.copy();
        entries = models;
        scroll = 0;
        expanded.clear();
        for (ModelCatalog.Entry entry : entries) {
            String path = "";
            for (String folder : folders(entry)) {
                path = path.isEmpty() ? folder : path + "/" + folder;
                expanded.add(path);
            }
        }
        layout();
    }
    public void setFilter(String text) {
        filter = text.toLowerCase(Locale.ROOT).trim();
        scroll = 0;
        layout();
    }
    private void layout() {
        tiles.clear();
        Folder root = new Folder("", "");
        for (ModelCatalog.Entry e : entries) {
            if (!e.name().toLowerCase(Locale.ROOT).contains(filter)
                && !e.model().getPath().toLowerCase(Locale.ROOT).contains(filter)) continue;
            Folder current = root;
            String full = "";
            for (String segment : folders(e)) {
                full = full.isEmpty() ? segment : full + "/" + segment;
                final String key=full, visible=label(segment);
                current = current.children.computeIfAbsent(segment, ignored -> new Folder(key,visible));
            }
            current.models.add(e);
        }
        totalHeight = layoutFolder(root, 0, 4);
        clampScroll();
    }
    private int layoutFolder(Folder root, int depth, int position) {
        int y = position;
        if (!root.path.isEmpty()) {
            tiles.add(new Tile(root.path, root.label, depth, null, 4, y, width - 13));
            y += HEADER;
            if (!expanded.contains(root.path) && filter.isEmpty()) return y;
        }
        int cellWidth = Math.max(18, (width - 16) / columns);
        int index = 0;
        for (ModelCatalog.Entry e : root.models) {
            tiles.add(new Tile(root.path, "",depth,e,6 + (index % columns)*cellWidth,
                y + (index / columns)*CELL,cellWidth));
            index++;
        }
        if (index > 0) y += ((index + columns - 1)/columns)*CELL + 2;
        for (Folder child : root.children.values()) y = layoutFolder(child,depth+1,y);
        return y;
    }
    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, Math.max(0,totalHeight-height))); }
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // Classic vanilla container frame: raised pale top/left and recessed bottom/right.
        g.fill(getX(),getY(),getRight(),getBottom(),0xFFC6C6C6);
        g.fill(getX(),getY(),getRight(),getY()+2,0xFFFFFFFF);
        g.fill(getX(),getY(),getX()+2,getBottom(),0xFFFFFFFF);
        g.fill(getX(),getBottom()-2,getRight(),getBottom(),0xFF555555);
        g.fill(getRight()-2,getY(),getRight(),getBottom(),0xFF555555);
        g.enableScissor(getX()+2,getY()+2,getRight()-2,getBottom()-2);
        var font = Minecraft.getInstance().font;
        for (Tile tile : tiles) {
            int x=getX()+tile.x(), y=getY()+tile.y()-scroll;
            if (y+Math.max(HEADER,CELL)<getY() || y>getBottom()) continue;
            boolean hover = mx>=x && mx<x+tile.width() && my>=y && my<y+(tile.entry()==null?HEADER:CELL);
            if (tile.entry()==null) {
                g.fill(x,y,x+tile.width(),y+HEADER-1,hover?0xFFE4E4E4:0xFFC6C6C6);
                g.fill(x,y+HEADER-2,x+tile.width(),y+HEADER-1,0xFF888888);
                String mark = expanded.contains(tile.group()) || !filter.isEmpty() ? "▼ " : "▶ ";
                g.text(font, Component.literal(mark+tile.label()),x+5+Math.min(tile.depth()-1,4)*8,y+2,0xFF202020,false);
            } else {
                g.fill(x,y,x+tile.width()-2,y+CELL-2,0xFF373737); 
                g.fill(x+1,y+1,x+tile.width()-3,y+CELL-3,hover?0xFFB6B6B6:0xFF8B8B8B);
                ItemStack preview = source.copy();
                if (!preview.isEmpty()) {
                    preview.set(DataComponents.CUSTOM_NAME,Component.literal(tile.entry().name()));
                    g.pose().pushMatrix();
                    g.pose().translate(x+(tile.width()-30)/2.0f,y+3);
                    g.pose().scale(1.875f,1.875f);
                    g.item(preview,0,0);
                    g.pose().popMatrix();
                }
                if (hover) g.setTooltipForNextFrame(Component.literal(tile.entry().name()),mx,my);
            }
        }
        g.disableScissor();
        if (totalHeight>height) {
            int barHeight=Math.max(18,height*height/totalHeight);
            int track=height-barHeight;
            int pos=(int)(track*(scroll/(double)Math.max(1,totalHeight-height)));
            g.fill(getRight()-9,getY(),getRight()-1,getBottom(),0xFF555555);
            g.fill(getRight()-8,getY()+pos,getRight()-2,getY()+pos+barHeight,draggingScrollbar?0xFFE8E8E8:0xFFC6C6C6);
        }
    }
    private int maxScroll() { return Math.max(0, totalHeight - height); }
    private int barHeight() { return Math.max(18, height * height / Math.max(height, totalHeight)); }
    private boolean inScrollbar(double x, double y) {
        return totalHeight > height && x >= getRight() - 9 && x < getRight()
                && y >= getY() && y < getBottom();
    }
    private void moveScrollbar(double mouseY) {
        int travel = Math.max(1, height - barHeight());
        double offset = mouseY - getY() - scrollbarDragOffset;
        scroll = (int) Math.round(Math.max(0, Math.min(travel, offset)) / travel * maxScroll());
        clampScroll();
    }
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            moveScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(),event.y())) return false;
        if (inScrollbar(event.x(), event.y())) {
            int thumbHeight = barHeight();
            int track = Math.max(1, height - thumbHeight);
            int thumbY = getY() + (int) Math.round(track * (scroll / (double) Math.max(1, maxScroll())));
            draggingScrollbar = true;
            scrollbarDragOffset = event.y() >= thumbY && event.y() < thumbY + thumbHeight
                    ? event.y() - thumbY : thumbHeight / 2.0;
            moveScrollbar(event.y());
            return true;
        }
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
