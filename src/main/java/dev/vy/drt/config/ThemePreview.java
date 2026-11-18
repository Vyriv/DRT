package dev.vy.drt.config;

import java.util.*;

/** Render-scoped draft colours and explicit semantic provenance, never inferred from RGB equality. */
public final class ThemePreview {
    public record Ink(String role, int original, int value) {}
    public record Element(int x, int y, int w, int h, List<Ink> properties) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x+w && py < y+h; }
    }
    private static Map<String,Integer> draft;
    private static final Map<Integer,Ink> inks = new HashMap<>();
    private static final List<Element> elements = new ArrayList<>();
    private ThemePreview() {}
    public static boolean active() { return draft != null; }
    public static void begin(Map<String,Integer> palette) { draft = palette; inks.clear(); elements.clear(); }
    public static void end() { draft = null; inks.clear(); }
    public static int resolve(String role, int original) {
        int value = ThemeProperties.resolve(draft, role, original);
        if (!ThemeProperties.SUPPORTED.contains(role)) return original;
        return tag(role, original, value);
    }
    public static int tag(String role,int original,int value) {
        if(!active())return value;
        int token = 0x01000000 + inks.size();
        inks.put(token, new Ink(role,original,value));
        return token;
    }
    public static int decode(int color) { Ink ink = inks.get(color); return active() && ink != null ? ink.value : color; }
    public static void record(int x,int y,int w,int h,int... colors) {
        if (!active()) return;
        List<Ink> found = new ArrayList<>();
        for(int color:colors) { Ink ink=inks.get(color); if(ink!=null && found.stream().noneMatch(i->i.role.equals(ink.role)))found.add(ink); }
        elements.add(new Element(x,y,w,h,List.copyOf(found)));
    }
    public static Element pick(double x,double y) {
        for(int i=elements.size()-1;i>=0;i--) {
            Element e=elements.get(i);
            if(e.contains(x,y)) {
                if(e.properties.isEmpty()) return null;
                Element component=e;
                for(Element candidate:elements) if(candidate!=e && candidate.contains(x,y) && candidate.h<=40
                    && candidate.w*candidate.h>component.w*component.h && !candidate.properties.isEmpty()) component=candidate;
                List<Ink> merged=new ArrayList<>(component.properties);
                for(Element child:elements) if(child.x>=component.x && child.y>=component.y
                    && child.x+child.w<=component.x+component.w && child.y+child.h<=component.y+component.h)
                    for(Ink ink:child.properties)if(merged.stream().noneMatch(v->v.role.equals(ink.role)))merged.add(ink);
                List<Ink> expanded=new ArrayList<>(merged);
                for(Ink ink:merged) for(String role:ThemeProperties.componentStateRoles(ink.role)) {
                    if(expanded.stream().anyMatch(value->value.role.equals(role))) continue;
                    int original=ThemeProperties.defaultColor(role,ink.original);
                    expanded.add(new Ink(role,original,ThemeProperties.resolve(draft,role,original)));
                }
                return new Element(component.x,component.y,component.w,component.h,List.copyOf(expanded));
            }
        }
        return null;
    }
}
