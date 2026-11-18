package dev.vy.drt.config;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** SkyList-style file discovery and cached palette, with exact per-call Default fallbacks. */
public final class DrtTheme {
    private static volatile Map<String, Integer> colors = Map.of();
    private static String loaded;
    private static String diagnostic = "";
    private DrtTheme() {}

    public static int color(String role, int original) {
        if (ThemePreview.active()) return ThemePreview.resolve(role, original);
        return ThemeProperties.resolve(colors, role, original);
    }

    public static synchronized void ensureLoaded() {
        String selected = DrtConfigManager.getConfig().selectedTheme;
        if (!Objects.equals(loaded, selected)) reload();
    }

    public static synchronized void reload() {
        load(DrtConfigManager.themesDirectory(), DrtConfigManager.getConfig().selectedTheme);
    }

    public static synchronized void load(Path directory, String selection) {
        loaded = selection;
        colors = Map.of();
        diagnostic = "";
        if (loaded == null || loaded.equalsIgnoreCase("Default")) return;
        try {
            try (var entries = Files.list(directory)) {
                Path file = entries.sorted()
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(loaded + ".json")
                        || p.getFileName().toString().equalsIgnoreCase(loaded + ".theme")
                        || Files.isDirectory(p) && p.getFileName().toString().equalsIgnoreCase(loaded))
                    .findFirst().orElseThrow();
                if (Files.isDirectory(file)) file = file.resolve("theme.json");
                colors = parse(Files.readString(file));
            }
        } catch (Exception e) {
            diagnostic = "Theme could not be loaded. Default colours are active.";
        }
    }

    public static String diagnostic() { return diagnostic; }

    public static Map<String, Integer> parse(String source) {
        String cleaned = source.lines().filter(line -> !line.stripLeading().startsWith("#"))
            .collect(java.util.stream.Collectors.joining("\n"));
        var root = JsonParser.parseString(cleaned).getAsJsonObject();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : root.entrySet()) {
            try {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) continue;
                result.put(entry.getKey(), parseColor(entry.getValue().getAsString()));
            } catch (RuntimeException ignored) { /* Invalid properties retain their original colours. */ }
        }
        Integer positive = result.get("profit.positive"), negative = result.get("profit.negative");
        if ((positive != null || negative != null) && (distance(positive == null ? 0xFF8BCF9A : positive,
                negative == null ? 0xFFFF7A7A : negative) < 80
                || positive != null && (positive >>> 24) < 128 || negative != null && (negative >>> 24) < 128)) {
            result.remove("profit.positive");
            result.remove("profit.negative");
        }
        return Map.copyOf(result);
    }

    private static int distance(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255))
            + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }

    public static int parseColor(String value) {
        String text = value.trim();
        if (text.matches("#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            String raw = text.replace("#", "");
            return (int) Long.parseLong(raw, 16) | (raw.length() == 6 ? 0xFF000000 : 0);
        }
        if (!text.toLowerCase(java.util.Locale.ROOT).startsWith("rgba(") || !text.endsWith(")"))
            throw new IllegalArgumentException("Invalid colour");
        String[] channels = text.substring(5, text.length() - 1).split(",");
        if (channels.length != 4) throw new IllegalArgumentException("Invalid rgba");
        int rgb = 0;
        for (int i = 0; i < 3; i++) rgb = rgb << 8 | Math.clamp(Integer.parseInt(channels[i].trim()), 0, 255);
        double alpha = Double.parseDouble(channels[3].trim());
        if (!Double.isFinite(alpha)) throw new IllegalArgumentException("Invalid alpha");
        int a = (int) Math.clamp(alpha <= 1 ? alpha * 255 : alpha, 0, 255);
        return a << 24 | rgb;
    }
}
