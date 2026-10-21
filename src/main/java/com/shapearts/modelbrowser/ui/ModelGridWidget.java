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
    private static final int CELL = 51, HEADER = 30;
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
            tiles.add(new Tile(root.path, root.label, depth, null, 4, y, width - 17));
            y += HEADER;
            if (!expanded.contains(root.path) && filter.isEmpty()) return y;
        }
        int cellWidth = Math.max(18, (width - 21) / columns);
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
        // Dark stone / bevelled UI matching the supplied mockup.
        g.fill(getX(),getY(),getRight(),getBottom(),0xFF595959);
        g.fill(getX(),getY(),getRight(),getY()+2,0xFF919191);
        g.fill(getX(),getY(),getX()+2,getBottom(),0xFF919191);
        g.fill(getX(),getBottom()-2,getRight(),getBottom(),0xFF151515);
        g.fill(getRight()-2,getY(),getRight(),getBottom(),0xFF151515);
        g.fill(getX()+4,getY()+4,getRight()-14,getBottom()-4,0xFF202020);
        g.enableScissor(getX()+3,getY()+3,getRight()-13,getBottom()-3);
        var font = Minecraft.getInstance().font;
        for (Tile tile : tiles) {
            int x=getX()+tile.x(), y=getY()+tile.y()-scroll;
            int h=tile.entry()==null?HEADER:CELL;
            if (y+h<getY()+3 || y>getBottom()-3) continue;
            boolean hover = mx>=x && mx<x+tile.width() && my>=y && my<y+h;
            if (tile.entry()==null) {
                int indent = Math.min(Math.max(0,tile.depth()-1),3)*9;
                int left=x+indent;
                int right=x+tile.width();
                // Rounded-looking pixel bevel: label bar, icon, arrow button.
                g.fill(left,y+1,right,y+HEADER-2,0xFF090909);
                g.fill(left+2,y+2,right-2,y+HEADER-4,hover?0xFF494949:0xFF343434);
                g.fill(left+2,y+2,right-2,y+4,0xFF6E6E6E);
                g.fill(left+2,y+HEADER-6,right-2,y+HEADER-4,0xFF161616);
                int iconX=left+2;
                g.fill(iconX,y,iconX+HEADER,y+HEADER,0xFF0C0C0C);
                g.fill(iconX+2,y+2,iconX+HEADER-2,y+HEADER-2,0xFF303030);
                // Placeholder icon (replace with folder image later).
                g.fill(iconX+HEADER/2-4,y+HEADER/2-3,iconX+HEADER/2+4,y+HEADER/2+4,0xFFB5B5B5);
                g.fill(iconX+HEADER/2-4,y+HEADER/2-3,iconX+HEADER/2+1,y+HEADER/2-1,0xFFE4E4E4);
                boolean open=expanded.contains(tile.group()) || !filter.isEmpty();
                int arrowX=right-HEADER+2;
                g.fill(arrowX,y+2,right-2,y+HEADER-3,0xFF151515);
                g.fill(arrowX+2,y+4,right-4,y+HEADER-5,0xFF4B4B4B);
                g.text(font,Component.literal(open?"▼":"▶"),arrowX+6,y+8,0xFFEAEAEA,false);
                String title=tile.label();
                int avail=Math.max(20,arrowX-(iconX+HEADER+8));
                if(font.width(title)>avail) title=font.plainSubstrByWidth(title,avail-10)+"…";
                g.text(font,Component.literal(title),iconX+HEADER+6,y+8,0xFFE7E7E7,false);
            } else {
                // Five-column custom item cards; provisional model/name until icon assets exist.
                int right=x+tile.width()-2, bottom=y+CELL-2;
                g.fill(x,y,right,bottom,0xFF080808);
                g.fill(x+2,y+2,right-2,bottom-2,hover?0xFF555555:0xFF363636);
                g.fill(x+2,y+2,right-2,y+4,0xFF747474);
                g.fill(x+2,bottom-4,right-2,bottom-2,0xFF191919);
                // A placeholder glyph instead of low-resolution item snapshots.
                int cx=x+(tile.width()-2)/2, cy=y+17;
                g.fill(cx-5,cy-5,cx+5,cy+5,0xFF9A9A9A);
                g.fill(cx-5,cy-5,cx+4,cy-3,0xFFCFCFCF);
                String text=tile.entry().name();
                int maxWidth=Math.max(10,tile.width()-7);
                if(font.width(text)>maxWidth) text=font.plainSubstrByWidth(text,maxWidth-8)+"…";
                g.text(font,Component.literal(text),x+4,y+CELL-15,0xFFEAEAEA,false);
                if (hover) g.setTooltipForNextFrame(Component.literal(tile.entry().name()),mx,my);
            }
        }
        g.disableScissor();
        if (totalHeight>height) {
            int barHeight=Math.max(18,height*height/totalHeight);
            int track=height-barHeight;
            int pos=(int)(track*(scroll/(double)Math.max(1,totalHeight-height)));
            g.fill(getRight()-13,getY()+3,getRight()-2,getBottom()-3,0xFF101010);
            g.fill(getRight()-11,getY()+5,getRight()-4,getBottom()-5,0xFF3E3E3E);
            g.fill(getRight()-11,getY()+pos+3,getRight()-4,getY()+pos+barHeight+3,draggingScrollbar?0xFFDDDDDD:0xFFA7A7A7);
        }
    }
    private int maxScroll() { return Math.max(0, totalHeight - height); }
    private int barHeight() { return Math.max(18, height * height / Math.max(height, totalHeight)); }
    private boolean inScrollbar(double x, double y) {
        return totalHeight > height && x >= getRight() - 13 && x < getRight()
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
