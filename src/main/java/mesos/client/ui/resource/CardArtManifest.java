package mesos.client.ui.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Maps a model card name (e.g. "Hunter-I-3", "Sustenance Artists",
 * "FinalEvent-1") to a 1-based page index inside
 * {@code Cards_total_front_NO PROMO.pdf} (the 122-card print sheet).
 *
 * <p>Strategy:
 * <ul>
 *   <li>Characters (Hunter/Shaman/Artist/Builder/Inventor/Gatherer):
 *       loaded from {@code character-manifest.properties} which maps each
 *       of the 84 character pages to an exact (TYPE, ERA) pair. A query for
 *       {@code (HUNTER, I)} cycles deterministically through the matching
 *       pages so {@code Hunter-I-0 .. Hunter-I-4} each land on a different
 *       Hunter Era-I print.</li>
 *   <li>Events (Hunting/Shamanic/Cave/Sustenance): the next 10 pages
 *       (85-94) — each event type maps to a band of pages.</li>
 *   <li>Buildings: 21 named cards mapped explicitly to pages 95-115,
 *       in the order produced by {@code BuildingDeckFactory}.</li>
 *   <li>Final Events: pages 116-117.</li>
 *   <li>Anything else: deterministic hash over the full 1..122 range.</li>
 * </ul>
 *
 * <p>Era-boundary note: the PDF lays out characters as:
 * <ul>
 *   <li>Era I  : pages  1-29  (HUNTER 1-5, INVENTOR 6-9, GATHERER 10-13,
 *                               SHAMAN 14-18, ARTIST 19-25, BUILDER 26-29)</li>
 *   <li>Era II : pages 30-57  (same type order)</li>
 *   <li>Era III: pages 58-84  (same type order)</li>
 * </ul>
 */
public final class CardArtManifest {

    private CardArtManifest() { }

    // ── Page bands ───────────────────────────────────────────────────────────
    private static final int EVENTS_START  = 85;    // pages 85..94 (10 events)
    private static final int EVENTS_BAND   = 10;
    private static final int BUILDINGS_START = 95;  // pages 95..115 (21 bldgs)
    private static final int FINAL_START   = 116;   // pages 116..117

    // ── Character manifest loaded at class-init from .properties file ────────
    // Key: "TYPE,ERA" e.g. "HUNTER,I"  →  sorted list of matching page numbers
    private static final Map<String, List<Integer>> CHAR_PAGES_BY_TYPE_ERA;

    static {
        Map<String, List<Integer>> map = new HashMap<>();
        Properties props = new Properties();
        String resource = "/img/card/character-manifest.properties";
        try (InputStream in = CardArtManifest.class.getResourceAsStream(resource)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // No-op: fall through to empty map; pageForName will use hash fallback.
        }

        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("page.")) continue;
            int pageNum;
            try {
                pageNum = Integer.parseInt(key.substring("page.".length()));
            } catch (NumberFormatException e) {
                continue;
            }
            String value = props.getProperty(key).trim();  // e.g. "HUNTER,I"
            map.computeIfAbsent(value, k -> new ArrayList<>()).add(pageNum);
        }

        // Sort each list so cycling is deterministic and ascending by page index.
        for (List<Integer> pages : map.values()) {
            Collections.sort(pages);
        }

        CHAR_PAGES_BY_TYPE_ERA = Collections.unmodifiableMap(map);
    }

    // ── Exact mapping for the 21 named buildings ─────────────────────────────
    private static final Map<String, Integer> BUILDING_PAGES = Map.ofEntries(
            // Era I (6 buildings, pages 95-100)
            Map.entry("Sustenance Artists",   95),
            Map.entry("Sustenance Inventors", 96),
            Map.entry("Sustenance Gatherers", 97),
            Map.entry("Turn Order Food",      98),
            Map.entry("Pair Food",            99),
            Map.entry("Set Food",            100),
            // Era II (7 buildings, pages 101-107)
            Map.entry("No Shaman Penalty",   101),
            Map.entry("Extra Shaman Icons",  102),
            Map.entry("Hunt Bonus",          103),
            Map.entry("Cave Art Food",       104),
            Map.entry("Artist Endgame",      105),
            Map.entry("Complete Set PP",     106),
            Map.entry("Double Builders",     107),
            // Era III (8 buildings, pages 108-115)
            Map.entry("Double Shaman Reward",   108),
            Map.entry("Extra Top Pick",         109),
            Map.entry("Flat Endgame",           110),
            Map.entry("Hunt Bonus III",         111),
            Map.entry("No Shaman Penalty III",  112),
            Map.entry("Sustenance Artists III", 113),
            Map.entry("Artist Endgame III",     114),
            Map.entry("Complete Set PP III",    115));

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the 1-based PDF page index for the given model card name.
     *
     * <p>Character names must follow the pattern {@code TYPE-ERA-copyIndex},
     * e.g. {@code "Hunter-I-0"}, {@code "Shaman-III-2"}.
     */
    public static int pageForName(String name) {
        if (name == null || name.isEmpty()) return 1;

        // Final events → pages 116-117.
        if (name.startsWith("FinalEvent-")) {
            int n = parseTrailingInt(name, 1);
            return FINAL_START + ((n - 1) % 2);
        }

        // Building (named) → exact mapping.
        Integer building = BUILDING_PAGES.get(name);
        if (building != null) return building;

        // Events: Hunting-I, Shamanic-II, Cave-III, Sustenance-I etc.
        if (name.startsWith("Hunting-")    || name.startsWith("Shamanic-")
         || name.startsWith("Cave-")       || name.startsWith("Sustenance-")) {
            return EVENTS_START + (Math.floorMod(hash(name), EVENTS_BAND));
        }

        // Characters: use exact manifest lookup.
        String type = inferType(name);
        String era  = inferEra(name);
        if (type != null && era != null) {
            int page = pageForCharacter(type, era, parseTrailingInt(name, 0));
            if (page > 0) return page;
        }

        // Fallback: era-banded hash (manifest may be missing in tests).
        if (era != null) {
            int start = switch (era) {
                case "I"   -> 1;
                case "II"  -> 30;
                case "III" -> 58;
                default    -> 1;
            };
            int bandSize = switch (era) {
                case "I"   -> 29;
                case "II"  -> 28;
                case "III" -> 27;
                default    -> 28;
            };
            return start + (Math.floorMod(hash(name), bandSize));
        }

        // Last-resort: full-range hash.
        return 1 + Math.floorMod(hash(name), 122);
    }

    /**
     * Returns the PDF page for a character identified by type key, era key,
     * and a zero-based copy index (cycles through all matching pages).
     *
     * @param typeKey  e.g. "HUNTER", "SHAMAN"  (upper-case)
     * @param eraKey   "I", "II", or "III"
     * @param copyIndex zero-based copy index; cycles modulo the page count
     * @return 1-based page number, or -1 if the manifest has no entry
     */
    public static int pageForCharacter(String typeKey, String eraKey, int copyIndex) {
        String key = typeKey.toUpperCase() + "," + eraKey.toUpperCase();
        List<Integer> pages = CHAR_PAGES_BY_TYPE_ERA.get(key);
        if (pages == null || pages.isEmpty()) return -1;
        return pages.get(Math.floorMod(copyIndex, pages.size()));
    }

    /**
     * Returns all PDF pages classified as the given (type, era) pair.
     * Returns an empty list if the manifest has no matching entry.
     */
    public static List<Integer> pagesForTypeEra(String typeKey, String eraKey) {
        String key = typeKey.toUpperCase() + "," + eraKey.toUpperCase();
        List<Integer> pages = CHAR_PAGES_BY_TYPE_ERA.get(key);
        return pages != null ? Collections.unmodifiableList(pages) : Collections.emptyList();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private static int hash(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h;
    }

    /** Extracts the era token from a card name like "Hunter-I-0" or "Builder-III". */
    private static String inferEra(String name) {
        if (name == null) return null;
        if (name.contains("-III") || name.endsWith("-III")) return "III";
        if (name.contains("-II-") || name.endsWith("-II"))  return "II";
        if (name.contains("-I-")  || name.endsWith("-I"))   return "I";
        return null;
    }

    /**
     * Extracts the character type token from a name like "Hunter-I-0".
     * Returns the upper-case type string matching the manifest keys,
     * or null if the name doesn't match a known type prefix.
     */
    private static String inferType(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        if (lower.startsWith("hunter"))   return "HUNTER";
        if (lower.startsWith("shaman"))   return "SHAMAN";
        if (lower.startsWith("artist"))   return "ARTIST";
        if (lower.startsWith("builder"))  return "BUILDER";
        if (lower.startsWith("inventor")) return "INVENTOR";
        if (lower.startsWith("gatherer")) return "GATHERER";
        return null;
    }

    private static int parseTrailingInt(String s, int fallback) {
        int dash = s.lastIndexOf('-');
        if (dash < 0 || dash == s.length() - 1) return fallback;
        try { return Integer.parseInt(s.substring(dash + 1)); }
        catch (NumberFormatException e) { return fallback; }
    }
}
