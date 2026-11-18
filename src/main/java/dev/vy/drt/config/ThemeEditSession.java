package dev.vy.drt.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolated draft. Constructing and editing it never changes configuration or the active palette. */
public final class ThemeEditSession {
    private final JsonObject original;
    private final Path source;
    private final String originalText;
    private final Map<String, Integer> changes = new LinkedHashMap<>();
    private final java.util.Set<String> removals = new java.util.HashSet<>();

    public ThemeEditSession(Path source) throws IOException {
        this.source = source;
        originalText = source == null ? null : Files.readString(source);
        original = originalText == null ? new JsonObject() : JsonParser.parseString(originalText.lines()
            .filter(line -> !line.stripLeading().startsWith("#"))
            .collect(java.util.stream.Collectors.joining("\n"))).getAsJsonObject();
    }

    public boolean readOnly() { return source == null; }
    public void set(String role, String color) {
        requireRole(role);
        int parsed = DrtTheme.parseColor(color);
        changes.put(role, parsed);
        removals.remove(role);
    }
    public void restoreDefault(String role) {
        requireRole(role);
        changes.remove(role);
        removals.add(role);
    }
    public void reset() { changes.clear(); removals.clear(); }
    public Map<String, Integer> palette() { return DrtTheme.parse(document().toString()); }
    public JsonObject document() {
        JsonObject result = original.deepCopy();
        removals.forEach(result::remove);
        changes.forEach((role, color) -> result.addProperty(role, String.format("#%08X", color)));
        return result;
    }
    private static void requireRole(String role) {
        if (!ThemeProperties.SUPPORTED.contains(role)) throw new IllegalArgumentException("This colour is not themeable");
    }

    public Path save(Path directory, String name, boolean createCopy) throws IOException {
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,63}") || name.equalsIgnoreCase("Default"))
            throw new IllegalArgumentException("Choose a name using letters, numbers, spaces, underscores or hyphens");
        if (!createCopy && readOnly()) throw new IllegalStateException("Create a copy of Default first");
        Files.createDirectories(directory);
        Path target = createCopy ? directory.resolve(name + ".json") : source;
        if (createCopy && ThemeDiscovery.discover(directory).stream().anyMatch(n -> n.equalsIgnoreCase(name)))
            throw new IOException("A theme with that name already exists");
        if (!createCopy && !Files.readString(source).equals(originalText))
            throw new IOException("This theme changed on disk. Reopen it before saving");
        JsonObject result = document();
        Map<String, Integer> validated = DrtTheme.parse(result.toString());
        for (String role : changes.keySet()) if (!validated.containsKey(role))
            throw new IllegalArgumentException("Profit and loss must remain visible and distinguishable");
        Path temporary = Files.createTempFile(target.getParent(), ".drt-theme-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
            if (createCopy) Files.move(temporary, target);
            else Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return target;
    }
}
