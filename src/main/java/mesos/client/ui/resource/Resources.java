package mesos.client.ui.resource;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import mesos.enums.CharacterType;

/**
 * Classpath PNG loader with a process-wide cache. All MESOS assets
 * sliced from the print PDFs (and the ESERCITAZIONE3/Materials2026
 * extras) live under {@code /img/...}. Returns {@code null} if a
 * resource is missing — callers fall back to procedural rendering.
 */
public final class Resources {

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private Resources() { }

    /** Loads {@code <path>} from classpath, or returns null if absent. */
    public static synchronized BufferedImage load(String path) {
        if (CACHE.containsKey(path)) return CACHE.get(path);
        BufferedImage img = null;
        try (InputStream in = Resources.class.getResourceAsStream(path)) {
            if (in != null) img = ImageIO.read(in);
        } catch (IOException ignored) {
            img = null;
        }
        CACHE.put(path, img);
        return img;
    }

    public static BufferedImage tile(String tileId) {
        return load("/img/tile/" + tileId + ".png");
    }

    public static BufferedImage turnOrder(int playerCount) {
        // Per-count cave art (2/3/4/5 spaces) re-sliced clean from fustelle front+back.
        int n = Math.max(2, Math.min(5, playerCount));
        return load("/img/tile/turn-order-" + n + "p.png");
    }

    public static BufferedImage totem(String colorName) {
        return load("/img/totem/" + colorName + ".png");
    }

    public static BufferedImage totemForColor(Color c) {
        String name = totemNameForColor(c);
        return name == null ? null : totem(name);
    }

    /** Top-down view of the totem standee — used on the turn order tile. */
    public static BufferedImage totemTopForColor(Color c) {
        String name = totemNameForColor(c);
        return name == null ? null : load("/img/totem/" + name + "top.png");
    }

    /** Era-specific card back (tent / hut / pueblo). Falls back to the generic back. */
    public static BufferedImage cardBack(String era) {
        if (era != null) {
            BufferedImage e = load("/img/card/back-" + era + ".png");
            if (e != null) return e;
        }
        return load("/img/card/back.png");
    }

    public static BufferedImage cardBack() { return cardBack(null); }

    public static BufferedImage cardFront(int pageIndex) {
        return load(String.format("/img/card/front-%03d.png", pageIndex));
    }

    public static String totemNameForColor(Color c) {
        if (c == null) return null;
        if (c.equals(Theme.P_RED))    return "red";
        if (c.equals(Theme.P_YELLOW)) return "yellow";
        if (c.equals(Theme.P_BLUE))   return "cyan";
        if (c.equals(Theme.P_PURPLE)) return "purple";
        if (c.equals(Theme.P_WHITE))  return "white";
        return null;
    }

    /** Dedicated character-type icon from {@code /img/icon/<type>.png}, or null if absent. */
    public static BufferedImage icon(CharacterType type) {
        if (type == null) return null;
        return load("/img/icon/" + type.name().toLowerCase(java.util.Locale.ROOT) + ".png");
    }

    /** Dedicated buildings icon from {@code /img/icon/building.png}, or null if absent. */
    public static BufferedImage buildingIcon() {
        return load("/img/icon/building.png");
    }

    // ── Character-type icon, cropped from a representative card ───────────────

    private static final Map<CharacterType, BufferedImage> TYPE_ICON_CACHE =
            new EnumMap<>(CharacterType.class);

    // Top-right type badge crop region, measured on the 632x968 card art and
    // verified on all 6 types. Below y=0.14 sits the card's food icon — excluded.
    private static final double ICON_X0 = 0.705, ICON_X1 = 0.985;
    private static final double ICON_Y0 = 0.015, ICON_Y1 = 0.14;

    /**
     * Returns the character-type badge (the coloured token in the card's
     * top-right corner) for {@code type}, cropped from a representative card
     * (Era I, copy 0) and cached. Returns {@code null} if the card art or the
     * manifest entry is missing — callers fall back to procedural rendering.
     */
    public static synchronized BufferedImage typeIcon(CharacterType type) {
        if (type == null) return null;
        if (TYPE_ICON_CACHE.containsKey(type)) return TYPE_ICON_CACHE.get(type);
        BufferedImage icon = null;
        int page = CardArtManifest.pageForCharacter(type.name(), "I", 0);
        if (page > 0) {
            BufferedImage card = cardFront(page);
            if (card != null) {
                int w = card.getWidth(), h = card.getHeight();
                int x0 = (int) (ICON_X0 * w), y0 = (int) (ICON_Y0 * h);
                int cw = Math.min((int) ((ICON_X1 - ICON_X0) * w), w - x0);
                int ch = Math.min((int) ((ICON_Y1 - ICON_Y0) * h), h - y0);
                if (cw > 0 && ch > 0) {
                    // Copy into a standalone image: getSubimage shares the card raster,
                    // so bilinear scaling could otherwise bleed pixels from outside the crop.
                    java.awt.image.BufferedImage sub = card.getSubimage(x0, y0, cw, ch);
                    icon = new java.awt.image.BufferedImage(cw, ch, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D ig = icon.createGraphics();
                    ig.drawImage(sub, 0, 0, null);
                    ig.dispose();
                }
            }
        }
        TYPE_ICON_CACHE.put(type, icon);
        return icon;
    }
}
