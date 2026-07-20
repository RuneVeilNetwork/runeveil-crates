package com.runeveil.crates.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.runeveil.crates.RuneveilCrates;
import com.runeveil.crates.RuneveilCratesMod;
import com.runeveil.crates.storage.CrateLocationStorage;
import com.runeveil.crates.storage.PlayerPityStorage;
import com.runeveil.crates.storage.PendingKeyStorage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;

import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class CrateConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final MinecraftServer server;
    private final Path configDir;
    private final Path keysDir;
    private final Path cratesDir;
    private final Path settingsFile;
    private final Path locationsFile;
    private final Path pityFile;
    private final Path pendingKeysFile;

    private SettingsConfig settings = new SettingsConfig();
    private final Map<String, KeyConfig> keys = new LinkedHashMap<>();
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();
    private CrateLocationStorage locations = new CrateLocationStorage();
    private PlayerPityStorage pityStorage = new PlayerPityStorage();
    private PendingKeyStorage pendingKeys = new PendingKeyStorage();
    private List<String> validationErrors = List.of();
    private boolean pityDirty;

    public CrateConfigManager(MinecraftServer server) {
        this.server = server;
        this.configDir = server.getServerDirectory().toPath().resolve("config").resolve(RuneveilCrates.MOD_ID);
        this.keysDir = configDir.resolve("keys");
        this.cratesDir = configDir.resolve("crates");
        this.settingsFile = configDir.resolve("settings.json");
        this.locationsFile = configDir.resolve("locations.json");
        this.pityFile = configDir.resolve("player-pity.json");
        this.pendingKeysFile = configDir.resolve("pending-keys.json");
    }

    public void load() {
        try {
            Files.createDirectories(keysDir);
            Files.createDirectories(cratesDir);
            copyDefaultsIfMissing("settings.json", settingsFile);
            copyDefaultsIfMissing("locations.json", locationsFile);
            copyDefaultsIfMissing("keys/vote.json", keysDir.resolve("vote.json"));
            copyDefaultsIfMissing("keys/donor.json", keysDir.resolve("donor.json"));
            copyDefaultsIfMissing("keys/event.json", keysDir.resolve("event.json"));
            copyDefaultsIfMissing("keys/discord_nitro.json", keysDir.resolve("discord_nitro.json"));
            copyDefaultsIfMissing("crates/vote.json", cratesDir.resolve("vote.json"));
            copyDefaultsIfMissing("crates/donor.json", cratesDir.resolve("donor.json"));
            copyDefaultsIfMissing("crates/event.json", cratesDir.resolve("event.json"));
            copyDefaultsIfMissing("crates/discord_nitro.json", cratesDir.resolve("discord_nitro.json"));
            copyDefaultsIfMissing("claim-compat.txt", configDir.resolve("claim-compat.txt"));
            copyDefaultsIfMissing("readme.txt", configDir.resolve("readme.txt"));

            migrateLegacyConfig(settingsFile);
            migrateLegacyConfig(locationsFile);
            migrateLegacyDirectory(keysDir);
            migrateLegacyDirectory(cratesDir);

            SettingsConfig candidateSettings = readJsonStrict(settingsFile, SettingsConfig.class);
            CrateLocationStorage candidateLocations = readJsonStrict(locationsFile, CrateLocationStorage.class);
            PlayerPityStorage candidatePity = Files.exists(pityFile) ? readJsonStrict(pityFile, PlayerPityStorage.class) : new PlayerPityStorage();
            PendingKeyStorage candidatePending = Files.exists(pendingKeysFile) ? readJsonStrict(pendingKeysFile, PendingKeyStorage.class) : new PendingKeyStorage();
            Map<String, KeyConfig> candidateKeys = new LinkedHashMap<>();
            Map<String, CrateDefinition> candidateCrates = new LinkedHashMap<>();
            loadDirectoryStrict(keysDir, KeyConfig.class, candidateKeys);
            loadDirectoryStrict(cratesDir, CrateDefinition.class, candidateCrates);
            List<String> errors = ConfigValidator.validate(candidateSettings, candidateKeys, candidateCrates);
            validationErrors = List.copyOf(errors);
            if (!errors.isEmpty()) {
                RuneveilCratesMod.LOGGER.error("Rejected crate configuration reload with {} error(s): {}", errors.size(), String.join("; ", errors));
                return;
            }
            settings = candidateSettings;
            locations = candidateLocations;
            pityStorage = candidatePity;
            pendingKeys = candidatePending;
            keys.clear(); keys.putAll(candidateKeys);
            crates.clear(); crates.putAll(candidateCrates);
        } catch (Exception e) {
            validationErrors = List.of(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            RuneveilCratesMod.LOGGER.error("Failed to load crate configuration", e);
        }
    }

    public void reload() {
        load();
    }

    public void saveCrate(CrateDefinition crate) {
        if (crate == null || crate.id == null || crate.id.isBlank()) {
            return;
        }
        writeJson(cratesDir.resolve(normalize(crate.id) + ".json"), crate);
        crates.put(normalize(crate.id), crate);
    }

    public Path getCratesDir() {
        return cratesDir;
    }

    public void saveLocations() {
        writeJson(locationsFile, locations);
    }

    public PlayerPityStorage getPityStorage() {
        return pityStorage;
    }

    public void savePityStorage() {
        pityDirty = false;
        writeJson(pityFile, pityStorage);
    }

    public void markPityDirty() { pityDirty = true; }
    public void flushDirtyData() { if (pityDirty) savePityStorage(); savePendingKeys(); }
    public PendingKeyStorage getPendingKeys() { return pendingKeys; }
    public void savePendingKeys() { writeJson(pendingKeysFile, pendingKeys); }
    public List<String> getValidationErrors() { return validationErrors; }
    public List<String> validateCurrent() { return ConfigValidator.validate(settings, keys, crates); }

    public SettingsConfig getSettings() {
        return settings;
    }

    public boolean saveRollAnimationSettings() {
        try {
            String content = Files.readString(settingsFile, StandardCharsets.UTF_8);
            int sectionStart = content.indexOf("\"rollAnimation\"");
            int sectionEnd = sectionStart < 0 ? -1 : content.indexOf('}', sectionStart);
            if (sectionStart < 0 || sectionEnd < 0) {
                RuneveilCratesMod.LOGGER.error("Could not locate rollAnimation in {}", settingsFile);
                return false;
            }

            String section = content.substring(sectionStart, sectionEnd + 1);
            SettingsConfig.RollAnimation roll = settings.rollAnimation;
            section = replaceSetting(section, "enabled", Boolean.toString(roll.enabled));
            section = replaceSetting(section, "ticksPerStep", Integer.toString(roll.ticksPerStep));
            section = replaceSetting(section, "minimumSteps", Integer.toString(roll.minimumSteps));
            section = replaceSetting(section, "finalHoldTicks", Integer.toString(roll.finalHoldTicks));
            Files.writeString(settingsFile,
                    content.substring(0, sectionStart) + section + content.substring(sectionEnd + 1),
                    StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            RuneveilCratesMod.LOGGER.error("Failed to save roll animation settings", e);
            return false;
        }
    }

    private static String replaceSetting(String section, String key, String value) {
        return section.replaceFirst(
                "(\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*)(true|false|-?\\d+)",
                "$1" + value
        );
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ServerLevel getServerLevel(ResourceKey<Level> dimension) {
        return server.getLevel(dimension);
    }

    public Map<String, KeyConfig> getKeys() {
        return Collections.unmodifiableMap(keys);
    }

    public Map<String, CrateDefinition> getCrates() {
        return Collections.unmodifiableMap(crates);
    }

    public boolean deleteCrate(String id) {
        String key = normalize(id);
        CrateDefinition removed = crates.remove(key);
        if (removed == null) return false;
        try { Files.deleteIfExists(cratesDir.resolve(key + ".json")); return true; }
        catch (IOException e) { crates.put(key, removed); RuneveilCratesMod.LOGGER.error("Failed to delete crate {}", id, e); return false; }
    }

    public CrateDefinition duplicateCrate(String sourceId, String newId) {
        CrateDefinition source = getCrate(sourceId);
        String key = normalize(newId);
        if (source == null || key.isBlank() || crates.containsKey(key)) return null;
        CrateDefinition copy = GSON.fromJson(GSON.toJson(source), CrateDefinition.class);
        copy.id = key;
        copy.displayName = source.displayName + " Copy";
        saveCrate(copy);
        return copy;
    }

    public boolean renameCrate(String oldId, String newId) {
        String oldKey = normalize(oldId), newKey = normalize(newId);
        CrateDefinition crate = crates.get(oldKey);
        if (crate == null || newKey.isBlank() || crates.containsKey(newKey)) return false;
        try {
            Files.deleteIfExists(cratesDir.resolve(oldKey + ".json"));
            crates.remove(oldKey);
            crate.id = newKey;
            saveCrate(crate);
            locations.locations.replaceAll((location, value) -> normalize(value).equals(oldKey) ? newKey : value);
            saveLocations();
            return true;
        } catch (Exception e) { RuneveilCratesMod.LOGGER.error("Failed to rename crate {}", oldId, e); return false; }
    }

    public Path exportCrate(String id) {
        CrateDefinition crate = getCrate(id);
        if (crate == null) return null;
        Path target = configDir.resolve("exports").resolve(normalize(id) + ".json");
        writeJson(target, crate);
        return target;
    }

    public CrateDefinition importCrate(String fileName) {
        Path imports = configDir.resolve("imports").toAbsolutePath().normalize();
        Path source = imports.resolve(fileName).normalize();
        if (!source.startsWith(imports) || !source.getFileName().toString().endsWith(".json")) return null;
        try {
            CrateDefinition crate = readJsonStrict(source, CrateDefinition.class);
            Map<String, CrateDefinition> candidate = new LinkedHashMap<>(crates);
            candidate.put(normalize(crate.id), crate);
            if (!ConfigValidator.validate(settings, keys, candidate).isEmpty()) return null;
            saveCrate(crate);
            return crate;
        } catch (Exception e) { RuneveilCratesMod.LOGGER.error("Failed to import crate from {}", source, e); return null; }
    }

    public CrateLocationStorage getLocations() {
        return locations;
    }

    public KeyConfig getKey(String id) {
        return keys.get(normalize(id));
    }

    public CrateDefinition getCrate(String id) {
        return crates.get(normalize(id));
    }

    public boolean isKnownCrateType(String id) {
        return crates.containsKey(normalize(id));
    }

    public static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    private void copyDefaultsIfMissing(String resourcePath, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        ResourceManager resourceManager = ServerLifecycleHooks.getCurrentServer().getResourceManager();
        var location = ResourceLocation.fromNamespaceAndPath(RuneveilCrates.MOD_ID, "defaults/" + resourcePath);
        Resource resource = resourceManager.getResource(location).orElse(null);
        if (resource == null) {
            RuneveilCratesMod.LOGGER.warn("Missing bundled default config: {}", resourcePath);
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream in = resource.open()) {
            Files.copy(in, target);
        }
    }

    private <T> void loadDirectory(Path dir, Class<T> type, Map<String, T> target) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                T value = readJson(path, type, null);
                if (value == null) {
                    return;
                }
                String id = path.getFileName().toString().replace(".json", "");
                if (value instanceof KeyConfig keyConfig && keyConfig.id != null && !keyConfig.id.isBlank()) {
                    id = keyConfig.id;
                }
                if (value instanceof CrateDefinition crateDefinition && crateDefinition.id != null && !crateDefinition.id.isBlank()) {
                    id = crateDefinition.id;
                }
                target.put(normalize(id), value);
            });
        }
    }

    private static <T> void loadDirectoryStrict(Path dir, Class<T> type, Map<String, T> target) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                T value = readJsonStrict(path, type);
                String id = path.getFileName().toString().replace(".json", "");
                if (value instanceof KeyConfig key && key.id != null && !key.id.isBlank()) id = key.id;
                if (value instanceof CrateDefinition crate && crate.id != null && !crate.id.isBlank()) id = crate.id;
                String normalized = normalize(id);
                if (target.containsKey(normalized)) throw new IOException("Duplicate configured id: " + normalized);
                target.put(normalized, value);
            }
        }
    }

    private static void migrateLegacyDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> path.toString().endsWith(".json")).forEach(CrateConfigManager::migrateLegacyConfig);
        }
    }

    private static void migrateLegacyConfig(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            if (!raw.contains("\"_") && !raw.contains("\"resourcePack\"") && !raw.contains("\"customModelData\"")) {
                return;
            }
            JsonReader reader = new JsonReader(new java.io.StringReader(raw));
            reader.setLenient(true);
            JsonElement root = JsonParser.parseReader(reader);
            removeRetiredTextureFields(root);
            StringBuilder output = new StringBuilder();
            writeCommentedJson(root, 0, output);
            output.append(System.lineSeparator());
            Path backup = path.resolveSibling(path.getFileName() + ".pre-1.2.21.bak");
            Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
            RuneveilCratesMod.LOGGER.info("Migrated legacy config comments in {} (backup: {})", path, backup);
        } catch (Exception e) {
            RuneveilCratesMod.LOGGER.warn("Could not migrate legacy config {}", path, e);
        }
    }

    private static void removeRetiredTextureFields(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            object.remove("resourcePack");
            object.remove("customModelData");
            object.entrySet().forEach(entry -> removeRetiredTextureFields(entry.getValue()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(CrateConfigManager::removeRetiredTextureFields);
        }
    }

    private static void writeCommentedJson(JsonElement element, int indent, StringBuilder output) {
        String padding = " ".repeat(indent);
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            List<Map.Entry<String, JsonElement>> data = new ArrayList<>();
            object.entrySet().stream().filter(entry -> !entry.getKey().startsWith("_")).forEach(data::add);
            output.append('{').append(System.lineSeparator());
            int dataIndex = 0;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    appendComments(entry.getValue(), indent + 2, output);
                    continue;
                }
                output.append(" ".repeat(indent + 2)).append(GSON.toJson(entry.getKey())).append(": ");
                writeCommentedJson(entry.getValue(), indent + 2, output);
                if (++dataIndex < data.size()) {
                    output.append(',');
                }
                output.append(System.lineSeparator());
            }
            output.append(padding).append('}');
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) {
                output.append("[]");
                return;
            }
            output.append('[').append(System.lineSeparator());
            for (int i = 0; i < array.size(); i++) {
                output.append(" ".repeat(indent + 2));
                writeCommentedJson(array.get(i), indent + 2, output);
                if (i + 1 < array.size()) {
                    output.append(',');
                }
                output.append(System.lineSeparator());
            }
            output.append(padding).append(']');
        } else {
            output.append(GSON.toJson(element));
        }
    }

    private static void appendComments(JsonElement element, int indent, StringBuilder output) {
        String prefix = " ".repeat(indent) + "# ";
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                appendComments(child, indent, output);
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                output.append(prefix).append(entry.getKey()).append(": ")
                        .append(entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : GSON.toJson(entry.getValue()))
                        .append(System.lineSeparator());
            }
        } else if (element.isJsonPrimitive()) {
            for (String line : element.getAsString().split("\\R", -1)) {
                output.append(prefix).append(line).append(System.lineSeparator());
            }
        }
    }

    private static <T> T readJson(Path path, Class<T> type, T fallback) {
        if (!Files.exists(path)) {
            return fallback;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            T value = GSON.fromJson(jsonReader, type);
            return value == null ? fallback : value;
        } catch (JsonSyntaxException e) {
            RuneveilCratesMod.LOGGER.error("Invalid JSON in {} — fix syntax errors (check quotes, commas, and trailing characters): {}",
                    path, e.getMessage());
            return fallback;
        } catch (IOException e) {
            RuneveilCratesMod.LOGGER.error("Failed to read {}", path, e);
            return fallback;
        }
    }

    private static <T> T readJsonStrict(Path path, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            T value = GSON.fromJson(jsonReader, type);
            if (value == null) throw new IOException("Empty configuration: " + path);
            return value;
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid JSON in " + path + ": " + e.getMessage(), e);
        }
    }

    private static void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            RuneveilCratesMod.LOGGER.error("Failed to write {}", path, e);
        }
    }
}
