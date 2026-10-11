package mesos.client.ui.resource;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import mesos.client.ui.widget.TotemBadge;

/**
 * Canonical mapping from server-side player id ("P1".."P5") to the totem
 * colour used across every UI panel. Single source of truth so adding a
 * new totem colour only touches one file.
 */
public final class PlayerColors {

    private static final Map<String, Color> BY_ID;
    static {
        BY_ID = new LinkedHashMap<>();
        BY_ID.put("P1", Theme.P_RED);
        BY_ID.put("P2", Theme.P_BLUE);
        BY_ID.put("P3", Theme.P_WHITE);
        BY_ID.put("P4", Theme.P_YELLOW);
        BY_ID.put("P5", Theme.P_PURPLE);
    }

    private static final Color[] PALETTE = {
        Theme.P_RED, Theme.P_BLUE, Theme.P_WHITE, Theme.P_YELLOW, Theme.P_PURPLE
    };

    private static final Color FALLBACK = Color.GRAY;

    private PlayerColors() { }

    /**
     * Returns the colour for the Nth player (0-based).
     * Used when player IDs are nicknames rather than "P1".."P5".
     */
    public static Color forPosition(int i) {
        return (i >= 0 && i < PALETTE.length) ? PALETTE[i] : FALLBACK;
    }

    /** Colour for a legacy "P1".."P5" player id; {@code GRAY} for unknown ids. */
    public static Color forId(String playerId) {
        if (playerId == null) return FALLBACK;
        return BY_ID.getOrDefault(playerId, FALLBACK);
    }

    /**
     * Converts the server-side TotemColor enum name (e.g. "RED", "BLUE") to
     * the GUI {@link TotemBadge.Colour} used by PlayerPanel and TotemBadge.
     * Note: the server uses "BLUE" for the colour the GUI displays as CYAN.
     */
    public static TotemBadge.Colour fromServerColor(String serverColor) {
        if (serverColor == null) return TotemBadge.Colour.RED;
        return switch (serverColor.toUpperCase(java.util.Locale.ROOT)) {
            case "RED"    -> TotemBadge.Colour.RED;
            case "BLUE"   -> TotemBadge.Colour.CYAN;   // server BLUE ↔ GUI CYAN
            case "WHITE"  -> TotemBadge.Colour.WHITE;
            case "YELLOW" -> TotemBadge.Colour.YELLOW;
            case "PURPLE" -> TotemBadge.Colour.PURPLE;
            default       -> TotemBadge.Colour.RED;
        };
    }

    /**
     * Converts the server-side TotemColor enum name to an AWT {@link Color}
     * suitable for {@link mesos.client.ui.widget.OfferTrackPanel#setOccupant(String, Color)}.
     */
    public static Color colorFromServerColor(String serverColor) {
        if (serverColor == null) return FALLBACK;
        return switch (serverColor.toUpperCase(java.util.Locale.ROOT)) {
            case "RED"    -> Theme.P_RED;
            case "BLUE"   -> Theme.P_CYAN;   // server BLUE ↔ GUI CYAN
            case "WHITE"  -> Theme.P_WHITE;
            case "YELLOW" -> Theme.P_YELLOW;
            case "PURPLE" -> Theme.P_PURPLE;
            default       -> FALLBACK;
        };
    }
}
