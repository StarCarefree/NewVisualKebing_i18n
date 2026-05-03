package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.github.newvisualkeybing.platform.Services;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.ConflictContext;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 跨平台按键绑定扫描器。
 * 移植自 Holographic Keybinds {@code KeyBindingScanner}，将 Forge 专属 API
 * （{@code IKeyConflictContext}, {@code ModList}）抽离至 {@link Services#PLATFORM}，
 * 兼具：
 * <ul>
 *   <li>冲突上下文感知（同上下文内才视为真冲突）</li>
 *   <li>多模式 modId 解析（key.&lt;modId&gt;.* / key.categories.&lt;modId&gt; / 显示名匹配）</li>
 *   <li>GLFW 键名缓存</li>
 *   <li>统计信息缓存</li>
 *   <li>键盘 + 鼠标双轨扫描</li>
 * </ul>
 */
public class KeyBindingScanner {

    public enum KeyStatus {
        FREE,
        SELF,
        OTHER_SINGLE,
        BOUND,
        CONFLICT
    }

    public record KeyBindingInfo(
            String translationKey,
            String actionName,
            String categoryKey,
            String categoryName,
            String modId,
            String modName,
            boolean self,
            ConflictContext conflictContext
    ) {
        public String contextDescription() {
            return switch (conflictContext) {
                case UNIVERSAL -> Component.translatable("screen.newvisualkeybing.viewer.context.universal").getString();
                case IN_GAME   -> Component.translatable("screen.newvisualkeybing.viewer.context.in_game").getString();
                case GUI       -> Component.translatable("screen.newvisualkeybing.viewer.context.gui").getString();
                case UNKNOWN   -> Component.translatable("screen.newvisualkeybing.viewer.context.unknown").getString();
            };
        }
    }

    public record ScanStats(int total, int free, int self, int other, int bound, int conflict) {}

    private static final Set<String> VANILLA_CATEGORIES = Set.of(
            "gameplay", "inventory", "movement", "multiplayer", "ui", "misc", "narrator", "creative"
    );

    private final Map<Integer, List<KeyBindingInfo>> keyboardBindings = new LinkedHashMap<>();
    private final Map<Integer, List<KeyBindingInfo>> mouseBindings = new LinkedHashMap<>();
    private final Map<Integer, KeyStatus> keyboardStatuses = new HashMap<>();
    private final Map<Integer, KeyStatus> mouseStatuses = new HashMap<>();
    private final Map<Integer, String> keyLabelCache = new HashMap<>();
    private final Map<String, String> registeredMods = new LinkedHashMap<>();

    private long lastScanTime = -1L;
    private static final long RESCAN_INTERVAL_MS = 3000L;

    public void refreshIfNeeded() {
        if (System.currentTimeMillis() - lastScanTime > RESCAN_INTERVAL_MS) scan();
    }

    public void scan() {
        keyboardBindings.clear();
        mouseBindings.clear();
        keyboardStatuses.clear();
        mouseStatuses.clear();
        registeredMods.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) return;

        for (KeyMapping mapping : minecraft.options.keyMappings) {
            String actionKey = mapping.getName();
            String categoryKey = mapping.getCategory();
            String modId = resolveModId(actionKey, categoryKey);
            String modName = resolveModName(modId, categoryKey);
            registeredMods.putIfAbsent(modId, modName);

            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
            if (key == InputConstants.UNKNOWN) continue;

            ConflictContext ctx = Services.PLATFORM.getConflictContext(mapping);
            KeyBindingInfo info = new KeyBindingInfo(
                    actionKey,
                    Component.translatable(actionKey).getString(),
                    categoryKey,
                    Component.translatable(categoryKey).getString(),
                    modId,
                    modName,
                    Constants.MOD_ID.equals(modId),
                    ctx
            );

            if (key.getType() == InputConstants.Type.MOUSE) {
                mouseBindings.computeIfAbsent(key.getValue(), k -> new ArrayList<>()).add(info);
            } else if (key.getValue() > GLFW.GLFW_KEY_UNKNOWN) {
                keyboardBindings.computeIfAbsent(key.getValue(), k -> new ArrayList<>()).add(info);
            }
        }

        Comparator<KeyBindingInfo> comparator = Comparator
                .comparing(KeyBindingInfo::self).reversed()
                .thenComparing(KeyBindingInfo::modName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(KeyBindingInfo::actionName, String.CASE_INSENSITIVE_ORDER);
        keyboardBindings.values().forEach(list -> list.sort(comparator));
        mouseBindings.values().forEach(list -> list.sort(comparator));

        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : keyboardBindings.entrySet()) {
            keyboardStatuses.put(entry.getKey(), computeStatus(entry.getValue()));
        }
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : mouseBindings.entrySet()) {
            mouseStatuses.put(entry.getKey(), computeStatus(entry.getValue()));
        }

        lastScanTime = System.currentTimeMillis();
    }

    public KeyStatus getStatus(int glfwKey) {
        return keyboardStatuses.getOrDefault(glfwKey, KeyStatus.FREE);
    }

    public KeyStatus getMouseStatus(int mouseButton) {
        return mouseStatuses.getOrDefault(mouseButton, KeyStatus.FREE);
    }

    public List<KeyBindingInfo> getBindings(int glfwKey) {
        List<KeyBindingInfo> list = keyboardBindings.get(glfwKey);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public List<KeyBindingInfo> getMouseBindings(int mouseButton) {
        List<KeyBindingInfo> list = mouseBindings.get(mouseButton);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public Map<Integer, List<KeyBindingInfo>> getAllBindings() {
        return Collections.unmodifiableMap(keyboardBindings);
    }

    public Map<Integer, List<KeyBindingInfo>> getAllMouseBindings() {
        return Collections.unmodifiableMap(mouseBindings);
    }

    public Map<String, String> getAllRegisteredMods() {
        return registeredMods.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    public Set<Integer> filterByStatus(FilterTab tab) {
        if (tab == null || tab == FilterTab.ALL) return null;
        Set<Integer> matches = new LinkedHashSet<>();
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            if (matchesStatus(getStatus(key.glfwKey()), tab)) matches.add(key.glfwKey());
        }
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.MOUSE_KEYS) {
            if (matchesStatus(getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(key.glfwKey())), tab)) {
                matches.add(key.glfwKey());
            }
        }
        return matches;
    }

    public Set<Integer> filterByMod(String modId) {
        if (modId == null || modId.isBlank()) return null;
        Set<Integer> matches = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : keyboardBindings.entrySet()) {
            if (entry.getValue().stream().anyMatch(info -> modId.equals(info.modId()))) matches.add(entry.getKey());
        }
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : mouseBindings.entrySet()) {
            if (entry.getValue().stream().anyMatch(info -> modId.equals(info.modId()))) {
                matches.add(KeyboardLayoutData.mouseToVirtual(entry.getKey()));
            }
        }
        return matches;
    }

    public Set<Integer> filterKeys(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.toLowerCase(Locale.ROOT).trim();
        Set<Integer> matches = new LinkedHashSet<>();

        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            int glfw = key.glfwKey();
            if (getKeyLabel(glfw).toLowerCase(Locale.ROOT).contains(q) || key.label().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(glfw);
                continue;
            }
            for (KeyBindingInfo info : getBindings(glfw)) {
                if (matches(info, q)) { matches.add(glfw); break; }
            }
        }
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.MOUSE_KEYS) {
            int btn = KeyboardLayoutData.virtualToMouseBtn(key.glfwKey());
            if (getMouseButtonLabel(btn).toLowerCase(Locale.ROOT).contains(q) || key.label().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(key.glfwKey());
                continue;
            }
            for (KeyBindingInfo info : getMouseBindings(btn)) {
                if (matches(info, q)) { matches.add(key.glfwKey()); break; }
            }
        }
        return matches;
    }

    public ScanStats getStats() {
        int free = 0, self = 0, other = 0, bound = 0, conflict = 0;
        int total = KeyboardLayoutData.KEYS.size() + KeyboardLayoutData.MOUSE_KEYS.size();
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            switch (getStatus(key.glfwKey())) {
                case FREE -> free++;
                case SELF -> self++;
                case OTHER_SINGLE -> other++;
                case BOUND -> bound++;
                case CONFLICT -> conflict++;
            }
        }
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.MOUSE_KEYS) {
            switch (getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(key.glfwKey()))) {
                case FREE -> free++;
                case SELF -> self++;
                case OTHER_SINGLE -> other++;
                case BOUND -> bound++;
                case CONFLICT -> conflict++;
            }
        }
        return new ScanStats(total, free, self, other, bound + self + other, conflict);
    }

    public String getKeyLabel(int glfwKey) {
        return keyLabelCache.computeIfAbsent(glfwKey, key -> {
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name != null && !name.isBlank()) return name.toUpperCase(Locale.ROOT);
            return switch (key) {
                case GLFW.GLFW_KEY_SPACE -> "SPACE";
                case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
                case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
                case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
                case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
                case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
                case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
                case GLFW.GLFW_KEY_LEFT_SUPER -> "LWIN";
                case GLFW.GLFW_KEY_RIGHT_SUPER -> "RWIN";
                case GLFW.GLFW_KEY_ESCAPE -> "ESC";
                case GLFW.GLFW_KEY_ENTER -> "ENTER";
                case GLFW.GLFW_KEY_TAB -> "TAB";
                case GLFW.GLFW_KEY_BACKSPACE -> "BKSP";
                case GLFW.GLFW_KEY_DELETE -> "DEL";
                case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
                case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
                case GLFW.GLFW_KEY_PRINT_SCREEN -> "PRT";
                default -> "KEY_" + key;
            };
        });
    }

    public static String getMouseButtonLabel(int mouseButton) {
        return switch (mouseButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
            case 3 -> "M4";
            case 4 -> "M5";
            default -> "M" + (mouseButton + 1);
        };
    }

    private static boolean matches(KeyBindingInfo info, String query) {
        return info.actionName().toLowerCase(Locale.ROOT).contains(query)
                || info.translationKey().toLowerCase(Locale.ROOT).contains(query)
                || info.categoryName().toLowerCase(Locale.ROOT).contains(query)
                || info.categoryKey().toLowerCase(Locale.ROOT).contains(query)
                || info.modId().toLowerCase(Locale.ROOT).contains(query)
                || info.modName().toLowerCase(Locale.ROOT).contains(query);
    }

    /**
     * 计算冲突状态（移植自 Holographic Keybinds）。
     * 仅当两个绑定的冲突上下文互相 conflicts(...) 时才认为是真实冲突。
     */
    private static KeyStatus computeStatus(List<KeyBindingInfo> infos) {
        if (infos.isEmpty()) return KeyStatus.FREE;
        if (infos.size() == 1) return infos.get(0).self() ? KeyStatus.SELF : KeyStatus.OTHER_SINGLE;

        for (int i = 0; i < infos.size(); i++) {
            for (int j = i + 1; j < infos.size(); j++) {
                ConflictContext ci = infos.get(i).conflictContext();
                ConflictContext cj = infos.get(j).conflictContext();
                if (ci != null && cj != null && ci.conflicts(cj)) return KeyStatus.CONFLICT;
            }
        }
        boolean hasSelf = infos.stream().anyMatch(KeyBindingInfo::self);
        boolean hasOther = infos.stream().anyMatch(info -> !info.self());
        if (hasSelf && hasOther) return KeyStatus.CONFLICT;
        return hasSelf ? KeyStatus.SELF : KeyStatus.OTHER_SINGLE;
    }

    private static boolean matchesStatus(KeyStatus status, FilterTab tab) {
        return switch (tab) {
            case ALL -> true;
            case FREE -> status == KeyStatus.FREE;
            case SELF -> status == KeyStatus.SELF;
            case OTHER -> status == KeyStatus.OTHER_SINGLE || status == KeyStatus.BOUND;
            case CONFLICT -> status == KeyStatus.CONFLICT;
        };
    }

    /** 多模式 modId 解析（移植自 Holographic Keybinds，调用 Services.PLATFORM.isModLoaded 替代 ModList）。 */
    private static String resolveModId(String name, String category) {
        // Pattern 1: key.<modId>.<action>
        if (name != null && name.startsWith("key.")) {
            String[] parts = name.split("\\.", 3);
            if (parts.length >= 2) {
                String candidate = parts[1];
                if (!"minecraft".equals(candidate) && Services.PLATFORM.isModLoaded(candidate)) return candidate;
            }
        }
        // Pattern 2: key.categories.<modId>
        if (category != null && category.startsWith("key.categories.")) {
            String suffix = category.substring("key.categories.".length());
            String suffixLower = suffix.toLowerCase(Locale.ROOT);
            if (!VANILLA_CATEGORIES.contains(suffixLower)) {
                if (Services.PLATFORM.isModLoaded(suffix))      return suffix;
                if (Services.PLATFORM.isModLoaded(suffixLower)) return suffixLower;
            }
        }
        // Pattern 3: scan all dot-segments of name
        if (name != null) {
            for (String part : name.split("\\.")) {
                if (part.isEmpty()) continue;
                String lower = part.toLowerCase(Locale.ROOT);
                if ("key".equals(lower) || "minecraft".equals(lower)) continue;
                if (Services.PLATFORM.isModLoaded(lower)) return lower;
            }
        }
        // Pattern 4: scan all dot-segments of category
        if (category != null) {
            for (String part : category.split("\\.")) {
                if (part.isEmpty()) continue;
                String lower = part.toLowerCase(Locale.ROOT);
                if ("key".equals(lower) || "categories".equals(lower) || "minecraft".equals(lower)) continue;
                if (Services.PLATFORM.isModLoaded(lower)) return lower;
            }
        }
        return "minecraft";
    }

    private static String resolveModName(String modId, String categoryKey) {
        if ("minecraft".equals(modId)) return "Minecraft";
        String fromPlatform = Services.PLATFORM.getModName(modId);
        if (fromPlatform != null && !fromPlatform.isBlank()) return fromPlatform;
        if (categoryKey != null) {
            String translated = Component.translatable(categoryKey).getString();
            if (translated != null && !translated.equals(categoryKey) && !translated.isBlank()) return translated;
        }
        return modId;
    }
}

