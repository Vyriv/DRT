package dev.vy.drt.client.screen;

import dev.vy.drt.config.ThemePreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class ThemePreviewDraw {
    static void item(GuiGraphicsExtractor g,net.minecraft.world.item.ItemStack item,int x,int y) {
        ThemePreview.record(x,y,16,16);
        g.item(item,x,y);
    }
    static void fill(GuiGraphicsExtractor g,int x,int y,int right,int bottom,int color) {
        ThemePreview.record(x,y,right-x,bottom-y,color);
        g.fill(x,y,right,bottom,ThemePreview.decode(color));
    }
    static void text(GuiGraphicsExtractor g,Font f,String s,int x,int y,int c) { text(g,f,s,x,y,c,false); }
    static void text(GuiGraphicsExtractor g,Font f,String s,int x,int y,int c,boolean shadow) {
        ThemePreview.record(x,y,f.width(s),f.lineHeight,c); g.text(f,s,x,y,ThemePreview.decode(c),shadow);
    }
    static void text(GuiGraphicsExtractor g,Font f,Component s,int x,int y,int c) { text(g,f,s,x,y,c,false); }
    static void text(GuiGraphicsExtractor g,Font f,Component s,int x,int y,int c,boolean shadow) {
        ThemePreview.record(x,y,f.width(s),f.lineHeight,c); g.text(f,s,x,y,ThemePreview.decode(c),shadow);
    }
}
