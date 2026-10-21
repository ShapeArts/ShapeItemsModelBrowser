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
import java.util.*;
import java.util.function.Consumer;

/**
 * The image-reference layout: two nested selectors, 5x5 folder/model chooser,
 * then an independent 8x7 item-grid with arrow buttons and a draggable track.
 * All icons are deliberate neutral placeholders until pack icons are supplied.
 */
public final class ModelGridWidget extends AbstractWidget {
    private static final int C=0xFF363636, LIGHT=0xFF676767, MID=0xFF4F4F4F,
            EDGE=0xFF080808, WHITE=0xFFDFDFDF, BG=0xFF222222;
    private final Consumer<String> chosen;
    private ItemStack base=ItemStack.EMPTY;
    private final Node root=new Node("","");
    private Node main, branch, selected;
    private boolean mainOpen=true,branchOpen=true, dragging=false;
    private double dragOffset;
    private int scroll=0, folderPage=0;
    private String filter="";
    private final List<Node> cards=new ArrayList<>();
    private final List<ModelCatalog.Entry> shown=new ArrayList<>();
    private final List<Rect> cardRects=new ArrayList<>();
    private final List<Rect> slotRects=new ArrayList<>();
    private Rect thumb=new Rect(0,0,0,0),track=new Rect(0,0,0,0),topArrow=new Rect(0,0,0,0),bottomArrow=new Rect(0,0,0,0);
    private record Rect(int x,int y,int w,int h) {
        boolean hit(double mx,double my) { return mx>=x && my>=y && mx<x+w && my<y+h; }
    }
    private static final class Node {
        final String id,title;
        final Map<String,Node> children=new LinkedHashMap<>();
        final List<ModelCatalog.Entry> direct=new ArrayList<>();
        Node(String id,String title){this.id=id;this.title=title;}
    }
    public ModelGridWidget(int x,int y,int w,int h,Consumer<String> chosen){
        super(x,y,w,h,Component.literal("Shape item collection"));
        this.chosen=chosen;
    }
    private static String title(String s){
        StringBuilder out=new StringBuilder();
        for(String word:s.split("[_\\- ]+")){
            if(word.isEmpty())continue;
            if(!out.isEmpty())out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
    private static String[] parts(ModelCatalog.Entry e){
        String p=e.model().getPath();
        if(p.startsWith("item/"))p=p.substring(5);
        int end=p.lastIndexOf('/');
        return end<0?new String[0]:p.substring(0,end).split("/");
    }
    public void setModels(ItemStack source,List<ModelCatalog.Entry> entries){
        base=source.copy();root.children.clear();root.direct.clear();
        for(var e:entries){
            Node cursor=root;
            String id="";
            for(String part:parts(e)){
                id=id.isEmpty()?part:id+"/"+part;
                final String full=id;
                cursor=cursor.children.computeIfAbsent(part,k->new Node(full,title(k)));
            }
            cursor.direct.add(e);
        }
        main=first(root);branch=first(main);selected=branch==null?main:branch;
        mainOpen=true;branchOpen=true;folderPage=0;scroll=0;refresh();
    }
    private static Node first(Node p){return p==null||p.children.isEmpty()?null:p.children.values().iterator().next();}
    public void setFilter(String text){filter=text.toLowerCase(Locale.ROOT).trim();scroll=0;refresh();}
    private void collect(Node n,List<ModelCatalog.Entry> results){
        if(n==null)return;
        for(var e:n.direct) if(filter.isEmpty()||e.name().toLowerCase(Locale.ROOT).contains(filter)||e.model().getPath().toLowerCase(Locale.ROOT).contains(filter))results.add(e);
        for(Node child:n.children.values())collect(child,results);
    }
    private void refresh(){
        cards.clear();shown.clear();
        Node location=selected==null?root:selected;
        cards.addAll(location.children.values());
        if(cards.isEmpty()&&branch!=null&&location==branch)cards.addAll(branch.children.values());
        collect(location,shown);
        int maxFolderPage=Math.max(0,(cards.size()-1)/25);
        folderPage=Math.min(folderPage,maxFolderPage);
        scroll=Math.max(0,Math.min(scroll,maxScroll()));
    }
    private int maxScroll(){return Math.max(0,shown.size()-56);}
    private void nextFolder(){
        if(main==null)return;
        if(branch!=null&&selected!=branch){selected=branch;}
        else if(selected==branch){selected=main;branch=null;}
        else{selected=root;main=null;}
        folderPage=0;scroll=0;refresh();
    }
    private static void bevel(GuiGraphicsExtractor g,int x,int y,int w,int h,int fill){
        g.fill(x,y,x+w,y+h,EDGE);
        g.fill(x+2,y+2,x+w-2,y+h-2,fill);
        g.fill(x+2,y+2,x+w-2,y+4,LIGHT);
        g.fill(x+2,y+h-4,x+w-2,y+h-2,0xFF181818);
        g.fill(x+2,y+2,x+4,y+h-2,LIGHT);
    }
    private static void down(GuiGraphicsExtractor g,int x,int y,boolean up){
        for(int row=0;row<5;row++){
            int spread=up?4-row:row;
            int yy=y+row;
            g.fill(x+4-spread,yy,x+5+spread,yy+1,EDGE);
        }
    }
    private static void placeholder(GuiGraphicsExtractor g,int x,int y){
        g.fill(x-5,y-5,x+5,y+5,0xFF929292);
        g.fill(x-5,y-5,x+4,y-3,0xFFD1D1D1);
        g.fill(x-5,y+3,x+5,y+5,0xFF525252);
    }
    private void header(GuiGraphicsExtractor g,Node node,int y,boolean first,boolean opened,int mx,int my){
        if(node==null)return;
        int x=getX(),w=width, inset=first?8:23;
        int rowX=x+inset, rowW=w-inset-7, h=29;
        bevel(g,rowX,y,rowW,h,C);
        int bx=rowX+4;
        bevel(g,bx-2,y-2,31,31,0xFF282828);
        placeholder(g,bx+13,y+13);
        int right=rowX+rowW-31;
        bevel(g,right,y,30,h,MID);
        down(g,right+10,y+10,false);
        String text=node.title;
        var font=Minecraft.getInstance().font;
        int limit=Math.max(12,right-(bx+34)-8);
        if(font.width(text)>limit)text=font.plainSubstrByWidth(text,Math.max(10,limit-9))+"…";
        g.text(font,Component.literal(text),bx+34,y+10,WHITE,false);
        if(!first){
            g.fill(x+14,y-6,x+16,y+4,EDGE);
            g.fill(x+14,y+2,x+26,y+4,EDGE);
        }
    }
    private int barTop(){return getY()+4;}
    private int barSecond(){return getY()+40;}
    private int chooserTop(){return getY()+83;}
    private int chooserBottom(){return getY()+Math.max(190,(int)(height*0.46));}
    private int slotsTop(){return chooserBottom()+4;}
    private int slotsBottom(){return getBottom()-4;}
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=getX(),y=getY(),w=width,h=height;
        // Major geometry follows the user's supplied reference rather than an inventory widget.
        bevel(g,x,y,w,h,0xFF545454);
        g.fill(x+5,y+73,x+w-5,chooserBottom(),BG);
        header(g,main,barTop(),true,mainOpen,mx,my);
        header(g,branch,barSecond(),false,branchOpen,mx,my);
        // Five column, five row upper chooser.
        cardRects.clear();
        int ct=chooserTop(), cb=chooserBottom();
        int left=x+11, usable=w-24;
        int sw=Math.max(12,(usable-4*3)/5);
        int sh=Math.max(15,(cb-ct-17)/5);
        for(int i=0;i<25;i++){
            int col=i%5,row=i/5;
            int xx=left+col*(sw+3), yy=ct+row*(sh+3);
            if(yy+sh>cb-3)break;
            Rect rect=new Rect(xx,yy,sw,sh);cardRects.add(rect);
            int idx=folderPage*25+i;
            Node folder=idx<cards.size()?cards.get(idx):null;
            bevel(g,xx,yy,sw,sh,folder!=null&&rect.hit(mx,my)?MID:C);
            if(folder!=null){
                placeholder(g,xx+sw/2,yy+Math.min(12,sh/3));
                String t=folder.title;
                var font=Minecraft.getInstance().font;
                if(font.width(t)>sw-7)t=font.plainSubstrByWidth(t,sw-14)+"…";
                g.text(font,Component.literal(t),xx+4,yy+sh-12,WHITE,false);
            }
        }
        // Explicit separation between selectors and scrollable inventory.
        int lower=slotsTop();
        bevel(g,x,lower,w,slotsBottom()-lower,0xFF575757);
        int sx=x+12, sy=lower+14;
        int gridW=w-56, gridH=Math.max(42,slotsBottom()-sy-10);
        int slotW=Math.max(12,gridW/8),slotH=Math.max(12,gridH/7);
        slotRects.clear();
        for(int i=0;i<56;i++){
            int col=i%8,row=i/8;
            int xx=sx+col*slotW, yy=sy+row*slotH;
            Rect r=new Rect(xx,yy,slotW,slotH);slotRects.add(r);
            g.fill(xx,yy,xx+slotW-1,yy+slotH-1,0xFF939393);
            g.fill(xx+2,yy+2,xx+slotW-3,yy+slotH-3,C);
            int idx=scroll+i;
            if(idx<shown.size()){
                // Placeholder item icon, later supplied by the corresponding pack folder.
                placeholder(g,xx+slotW/2,yy+slotH/2);
                if(r.hit(mx,my))g.setTooltipForNextFrame(Component.literal(shown.get(idx).name()),mx,my);
            }
        }
        int scX=x+w-38,scY=sy,scW=27,scH=gridH;
        bevel(g,scX,scY,scW,scH,0xFF353535);
        topArrow=new Rect(scX+3,scY+3,scW-6,21);
        bottomArrow=new Rect(scX+3,scY+scH-24,scW-6,21);
        bevel(g,topArrow.x,topArrow.y,topArrow.w,topArrow.h,MID);
        bevel(g,bottomArrow.x,bottomArrow.y,bottomArrow.w,bottomArrow.h,MID);
        down(g,topArrow.x+topArrow.w/2-4,topArrow.y+7,true);
        down(g,bottomArrow.x+bottomArrow.w/2-4,bottomArrow.y+7,false);
        track=new Rect(scX+6,scY+29,scW-12,Math.max(8,scH-58));
        g.fill(track.x,track.y,track.x+track.w,track.y+track.h,EDGE);
        g.fill(track.x+2,track.y+2,track.x+track.w-2,track.y+track.h-2,C);
        int thumbH=Math.max(16,(int)((double)track.h*56/Math.max(56,shown.size())));
        int travel=Math.max(0,track.h-thumbH);
        int thumbY=track.y+(maxScroll()==0?0:(int)Math.round(travel*(scroll/(double)maxScroll())));
        thumb=new Rect(track.x+1,thumbY,track.w-2,thumbH);
        bevel(g,thumb.x,thumb.y,thumb.w,thumb.h,dragging?0xFFDDDDDD:0xFFAAAAAA);
    }
    private void moveThumb(double my){
        int travel=Math.max(1,track.h-thumb.h);
        double pct=Math.max(0,Math.min(1,(my-track.y-dragOffset)/travel));
        scroll=(int)Math.round(pct*maxScroll());
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){
        if(!isMouseOver(e.x(),e.y()))return false;
        int xx=(int)e.x(),yy=(int)e.y();
        if(thumb.hit(xx,yy)&&maxScroll()>0){dragging=true;dragOffset=yy-thumb.y;return true;}
        if(track.hit(xx,yy)&&maxScroll()>0){dragging=true;dragOffset=thumb.h/2.0;moveThumb(yy);return true;}
        if(topArrow.hit(xx,yy)){scroll=Math.max(0,scroll-8);return true;}
        if(bottomArrow.hit(xx,yy)){scroll=Math.min(maxScroll(),scroll+8);return true;}
        if(yy>=barTop()&&yy<barTop()+34&&main!=null){mainOpen=!mainOpen;if(!mainOpen){branchOpen=false;selected=root;}else{selected=main;branch=first(main);selected=branch==null?main:branch;}refresh();return true;}
        if(yy>=barSecond()&&yy<barSecond()+33&&branch!=null){branchOpen=!branchOpen;selected=branchOpen?branch:main;refresh();return true;}
        for(int i=0;i<cardRects.size();i++)if(cardRects.get(i).hit(xx,yy)){
            int idx=folderPage*25+i;
            if(idx<cards.size()){
                Node clicked=cards.get(idx);
                if(main==null)main=clicked;
                else if(branch==null||selected==main)branch=clicked;
                selected=clicked;branchOpen=true;scroll=0;folderPage=0;refresh();
            }
            return true;
        }
        for(int i=0;i<slotRects.size();i++)if(slotRects.get(i).hit(xx,yy)){
            int idx=scroll+i;if(idx<shown.size())chosen.accept(shown.get(idx).name());return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(dragging){moveThumb(e.y());return true;}
        return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragging){dragging=false;return true;}
        return super.mouseReleased(e);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        if(!isMouseOver(mx,my))return false;
        if(my>=chooserTop()&&my<chooserBottom())folderPage=Math.max(0,Math.min(Math.max(0,(cards.size()-1)/25),folderPage-(int)Math.signum(dy)));
        else scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.round(dy*8)));
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
}
