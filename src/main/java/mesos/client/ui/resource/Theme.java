package mesos.client.ui.resource;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * Visual palette for the client UI. Canonical colours extracted from the
 * official MESOS materials: box cover (Box_ITA_PREV.pdf) and playmat
 * (fustelle_front-p1.png). Player colours match pedine_specs.pdf Pantone refs.
 */
public final class Theme {

    private Theme() {}

    // ── Backgrounds — sky/cloud playmat ──────────────────────────────────────
    public static final Color SKY_TOP        = new Color(0x5F, 0xCD, 0xD8);
    public static final Color SKY_MID        = new Color(0x2D, 0xB5, 0xC7);
    public static final Color SKY_DEEP       = new Color(0x1B, 0x7D, 0x8C);

    // ── Warm — sun, earth, brick (box cover interior) ────────────────────────
    public static final Color SUN            = new Color(0xF5, 0xA9, 0x3D);
    public static final Color ORANGE         = new Color(0xE5, 0x64, 0x2A);
    public static final Color CORAL          = new Color(0xC2, 0x4F, 0x2E);
    public static final Color BRICK          = new Color(0x7A, 0x24, 0x18);

    // ── Cream / parchment ────────────────────────────────────────────────────
    public static final Color CREAM          = new Color(0xF5, 0xF0, 0xE5);
    public static final Color CREAM_DIM      = new Color(0xE5, 0xDD, 0xC8);

    // ── Forest greens ────────────────────────────────────────────────────────
    public static final Color FOREST         = new Color(0x1D, 0x4A, 0x3C);
    public static final Color FOREST_DEEP    = new Color(0x0A, 0x28, 0x20);

    // ── Stone / earth (mid-luminance ground tone) ────────────────────────────
    public static final Color STONE          = new Color(0x8B, 0x7A, 0x5F);

    // ── Night (box cover exterior, status bar) ───────────────────────────────
    public static final Color NIGHT_DEEP     = new Color(0x0F, 0x19, 0x33);
    public static final Color NIGHT_MID      = new Color(0x1B, 0x2A, 0x4E);
    public static final Color NIGHT_BLUE     = new Color(0x2A, 0x5B, 0xBA);

    // ── Flame (accent) ───────────────────────────────────────────────────────
    public static final Color FLAME          = new Color(0xF5, 0xC9, 0x28);
    public static final Color FLAME_DEEP     = new Color(0xC9, 0x9E, 0x11);
    public static final Color FLAME_GLOW     = new Color(0xF5, 0x8E, 0x2A);

    // ── Ink outline ──────────────────────────────────────────────────────────
    public static final Color INK            = new Color(0x0F, 0x08, 0x07);
    public static final Color INK_SOFT       = new Color(0x2A, 0x1A, 0x14);

    // ── Player colours — Pantone PDF pedine_specs ────────────────────────────
    public static final Color P_RED          = new Color(0xF1, 0x5D, 0x3E);  // PANTONE 1788 (sampled from pedine_specs)
    public static final Color P_YELLOW       = new Color(0xFE, 0xCE, 0x29);  // PANTONE 7406 (sampled)
    public static final Color P_CYAN         = new Color(0x13, 0xA2, 0xC1);  // PANTONE 3135 (sampled)
    public static final Color P_PURPLE       = new Color(0x41, 0x0B, 0x2C);  // PANTONE 7449 (sampled)
    public static final Color P_WHITE        = new Color(0xEE, 0xEF, 0xEE);  // spec "White" swatch

    // ── Card-type frames (BGA scheme: characters green, buildings blue, events red) ──
    public static final Color CARD_GREEN     = new Color(0x3F, 0x8F, 0x4E);
    public static final Color CARD_BLUE      = new Color(0x2F, 0x6F, 0xB0);
    public static final Color CARD_RED       = new Color(0xC2, 0x3A, 0x2E);

    // ── Status semantic ──────────────────────────────────────────────────────
    public static final Color SUCCESS        = new Color(0x5F, 0xB8, 0x7E);
    public static final Color DANGER         = CORAL;

    // ── Legacy aliases (kept so existing panels compile) ─────────────────────
    public static final Color BG_PARCHMENT      = CREAM;
    public static final Color BG_PARCHMENT_DARK = CREAM_DIM;
    public static final Color BG_STONE          = STONE;
    public static final Color BG_DARK_STONE     = NIGHT_MID;
    public static final Color TILE_BG           = CREAM;
    public static final Color TILE_BG_HOVER     = CREAM_DIM;
    public static final Color TILE_BORDER       = INK;
    public static final Color CARD_BACK         = BRICK;
    public static final Color CARD_CHARACTER    = CREAM;
    public static final Color CARD_BUILDING     = FLAME;
    public static final Color CARD_EVENT        = CORAL;
    public static final Color INK_LIGHT         = INK_SOFT;
    public static final Color P_BLUE            = P_CYAN;   // alias for compat

    // ── Display scaling ──────────────────────────────────────────────────────
    // The fixed pixel sizes throughout the GUI were authored for this baseline
    // window. SCALE keeps fonts and component dimensions proportional to the
    // actual screen, so the UI fits both small high-DPI panels (e.g. Surface)
    // and large desktop monitors. Computed once at class-load from the primary
    // screen's usable bounds (already reported by Swing in DPI-aware units).
    private static final double BASE_W = 1400.0;
    private static final double BASE_H = 880.0;

    /** Global UI scale factor (clamped to a sane range), from the screen size. */
    public static final double SCALE = computeScale();

    private static double computeScale() {
        try {
            Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds();
            double s = Math.min(b.width / BASE_W, b.height / BASE_H);
            return Math.max(0.65, Math.min(1.6, s));
        } catch (Throwable t) {
            return 1.0; // headless or unusual environment → no scaling
        }
    }

    /** Scales a baseline pixel value by {@link #SCALE} (rounded, never below 1). */
    public static int scale(int px) {
        return Math.max(1, (int) Math.round(px * SCALE));
    }

    /** Builds a font whose size is the baseline size scaled by {@link #SCALE}. */
    public static Font font(String family, int style, int size) {
        return new Font(family, style, scale(size));
    }

    // Fonts (sizes scaled by SCALE)
    public static final Font TITLE   = font("Serif", Font.BOLD, 22);
    public static final Font HEADING = font("Serif", Font.BOLD, 14);
    public static final Font BODY    = font("SansSerif", Font.PLAIN, 12);
    public static final Font SMALL   = font("SansSerif", Font.PLAIN, 10);
    public static final Font LETTER  = font("Serif", Font.BOLD, 28);
}
