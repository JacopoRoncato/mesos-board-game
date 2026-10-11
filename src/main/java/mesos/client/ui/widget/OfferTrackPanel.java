package mesos.client.ui.widget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.Theme;

/**
 * The Offer Track — up to 7 painted tiles, A..G left to right.
 *
 * <p>Modular by player count (regole.txt §3.1): tessera A is only
 * dealt to 5-player games (the "3 Food" bonus tile). At lower counts
 * we hide it. The Panel still keeps the slot internally so a future
 * config change can re-show it without rebuilding.
 *
 * <p>Click ⇒ {@link PlaceHandler#onPlace(String)} with the tile id;
 * the caller is responsible for sending {@code PLACE <nickname> <tileId>}.
 */
public class OfferTrackPanel extends JPanel {

    @FunctionalInterface
    public interface PlaceHandler {
        void onPlace(String tileId);
    }

    private static final Map<String, String> TILE_TEXT = new LinkedHashMap<>();
    static {
        TILE_TEXT.put("A", "3 Cibo (solo 5 giocatori)");
        TILE_TEXT.put("B", "1 carta dalla fila inferiore");
        TILE_TEXT.put("C", "1 carta dalla fila superiore");
        TILE_TEXT.put("D", "2 carte dalla fila inferiore");
        TILE_TEXT.put("E", "1 fila superiore + 1 inferiore");
        TILE_TEXT.put("F", "2 carte dalla fila superiore");
        TILE_TEXT.put("G", "2 superiore + 1 inferiore");
    }

    /** Printed tile height that sizes the band; tiles keep a 2:3 (w:h) aspect. */
    private static final int BAND_H = Theme.scale(300);

    private final Map<String, OfferTileComponent> tiles = new LinkedHashMap<>();
    private int currentPlayerCount = 5;
    private java.util.Set<String> visibleIds = null;

    /**
     * Constructs an OfferTrackPanel with up to 7 tiles (A–G).
     *
     * @param phase   the phase controller; drives FLAME glow on PLACEMENT_MINE
     * @param handler called with the tile id when the player clicks a free tile
     */
    public OfferTrackPanel(PhaseController phase, PlaceHandler handler) {
        setLayout(null);                            // custom doLayout() below
        setOpaque(false);
        setBorder(new EmptyBorder(Theme.scale(9), Theme.scale(11), Theme.scale(9), Theme.scale(11)));   // room for the cream frame

        for (Map.Entry<String, String> e : TILE_TEXT.entrySet()) {
            OfferTileComponent tile = new OfferTileComponent(
                    e.getKey(), phase, e.getValue(), handler::onPlace);
            tiles.put(e.getKey(), tile);
        }
        rebuildVisible();
    }

    /**
     * Lay the visible tiles out edge-to-edge, keeping each one's printed 2:3
     * (w:h) aspect, and centre the group in the band. This makes the tiles read
     * as one continuous landscape (BGA look) at any player count — height-driven,
     * shrunk to fit the width only when there are many tiles.
     */
    private static double aspectOf(Component c) {
        return (c instanceof OfferTileComponent) ? ((OfferTileComponent) c).artAspect() : 2.0 / 3.0;
    }

    /** Total width of the tile group at a given tile height (per-tile aspect). */
    private int groupWidth(int tileH) {
        int total = 0;
        for (Component c : getComponents()) total += (int) Math.round(tileH * aspectOf(c));
        return total;
    }

    /** Geometry of the centred, edge-to-edge tile group: {x, y, totalW, tileH}. */
    private int[] groupGeometry() {
        Insets pad = getInsets();
        int availW = Math.max(0, getWidth()  - pad.left - pad.right);
        int availH = Math.max(0, getHeight() - pad.top  - pad.bottom);
        int tileH = availH;
        int total = groupWidth(tileH);
        if (total > availW && total > 0) {          // too wide: scale the band down
            tileH = (int) Math.round(tileH * (double) availW / total);
            total = groupWidth(tileH);
        }
        int x = pad.left + (availW - total) / 2;
        int y = pad.top  + (availH - tileH) / 2;
        return new int[]{ x, y, total, tileH };
    }

    @Override
    public void doLayout() {
        int[] geo = groupGeometry();
        int x = geo[0], tileH = geo[3];
        for (Component c : getComponents()) {
            int w = (int) Math.round(tileH * aspectOf(c));
            c.setBounds(x, geo[1], w, tileH);
            x += w;
        }
    }

    /** Width tracks the visible tiles' real proportions so the cream frame wraps
     *  the landscape snugly and the board can centre it (BGA look). */
    @Override
    public Dimension getPreferredSize() {
        Insets pad = getInsets();
        return new Dimension(groupWidth(BAND_H) + pad.left + pad.right, BAND_H + pad.top + pad.bottom);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int n = getComponentCount();
        if (n == 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int[] geo = groupGeometry();
        int gw = geo[2];
        // BGA-style cream playmat wrapping just the centred tile group; the board's
        // cave background shows on either side in few-tile games (no empty mat).
        int fx = geo[0] - Theme.scale(11), fy = geo[1] - Theme.scale(9), fw = gw + Theme.scale(22), fh = geo[3] + Theme.scale(18);
        g2.setColor(Theme.CREAM);
        g2.fillRoundRect(fx, fy, fw, fh, Theme.scale(20), Theme.scale(20));
        g2.setColor(Theme.INK);
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRoundRect(fx + 1, fy + 1, fw - 3, fh - 3, Theme.scale(20), Theme.scale(20));
        g2.dispose();
    }

    /** Show/hide tessera A based on player count; call from snapshot updates. */
    public void configureForPlayerCount(int playerCount) {
        if (playerCount == currentPlayerCount) return;
        currentPlayerCount = playerCount;
        rebuildVisible();
    }

    private void rebuildVisible() {
        removeAll();
        for (Map.Entry<String, OfferTileComponent> e : tiles.entrySet()) {
            if (visibleIds != null) {
                if (visibleIds.contains(e.getKey())) add(e.getValue());
            } else if (!("A".equals(e.getKey()) && currentPlayerCount < 5)) {
                add(e.getValue());
            }
        }
        revalidate();
        repaint();
    }

    /** Show exactly the tiles the server dealt for this game (from the snapshot's offer track). */
    public void setVisibleTiles(java.util.Set<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        if (ids.equals(visibleIds)) return;
        this.visibleIds = new java.util.HashSet<>(ids);
        rebuildVisible();
    }

    /**
     * Marks a tile as occupied by drawing the given totem colour over it.
     *
     * @param tileId the tile identifier (e.g. "B")
     * @param color  the totem's AWT colour
     */
    public void setOccupant(String tileId, Color color) {
        OfferTileComponent t = tiles.get(tileId);
        if (t != null) t.setOccupantColor(color);
    }

    /** Removes all totem overlays from every tile. */
    public void clearAllOccupants() {
        for (OfferTileComponent t : tiles.values()) {
            t.clearOccupant();
        }
    }
}
