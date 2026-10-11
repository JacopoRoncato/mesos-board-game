package mesos.client.ui.widget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.Resources;
import mesos.client.ui.resource.Theme;

/**
 * One offer tile, painted with the original MESOS landscape art
 * (sliced from fustelle_front_p1). Hover ⇒ subtle parchment glow.
 * Click ⇒ fires the supplied handler with the tile id.
 *
 * When a totem occupies the tile we draw the totem standee PNG
 * centered at the bottom of the tile.
 */
public class OfferTileComponent extends JComponent {

    @FunctionalInterface
    public interface ClickHandler {
        void onClick(String tileId);
    }

    private final String tileId;
    private final PhaseController phase;
    private final BufferedImage tileImg;
    private boolean hovered = false;
    private Color occupantColor;

    /**
     * Constructs an OfferTileComponent.
     *
     * @param tileId     the tile identifier (e.g. "B", "F")
     * @param phase      the phase controller; drives the FLAME glow on PLACEMENT_MINE
     * @param effectText the tooltip description of this tile's card-pick effect
     * @param handler    called with the tile id when the player clicks
     */
    public OfferTileComponent(String tileId, PhaseController phase,
                              String effectText, ClickHandler handler) {
        this.tileId = tileId;
        this.phase  = phase;
        this.tileImg = Resources.tile(tileId);
        // Tooltip carries the regola.txt §8 effect — replaces the inline label
        // because the art already shows the icons.
        setToolTipText("Tessera " + tileId + " — " + effectText);
        // Aspect ratio of the sliced tile art is roughly 2 wide : 3 tall.
        setPreferredSize(new Dimension(Theme.scale(120), Theme.scale(180)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) { handler.onClick(tileId); }
        });

        // Repaint whenever the phase changes so the FLAME glow appears/disappears
        // in sync with PLACEMENT_MINE transitions.
        if (phase != null) phase.addListener(this::repaint);
    }

    /** Sets the totem colour to overlay on this tile and repaints. */
    public void setOccupantColor(Color c) { this.occupantColor = c; repaint(); }

    /** Removes the totem overlay from this tile and repaints. */
    public void clearOccupant()           { this.occupantColor = null; repaint(); }

    /** The tile art's native width:height, so the layout and letterbox use the
     *  real printed proportions and never stretch the landscape. */
    double artAspect() {
        return (tileImg != null && tileImg.getHeight() > 0)
                ? (double) tileImg.getWidth() / tileImg.getHeight() : 2.0 / 3.0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth();
        int h = getHeight();

        // Letterbox the art to its OWN printed aspect within the cell so it is
        // never stretched, however the offer band is sized. The border, hover
        // glow and totem are all drawn relative to this art rect.
        double aspect = artAspect();
        int drawW = w;
        int drawH = (int) Math.round(w / aspect);
        if (drawH > h) { drawH = h; drawW = (int) Math.round(h * aspect); }
        int dx = (w - drawW) / 2;
        int dy = (h - drawH) / 2;

        // Clip drawing to a rounded rect so the printed tile's white DIECUT
        // margin doesn't show in the corners against the parchment playmat.
        int arc = Math.max(8, drawW / 12);
        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(new java.awt.geom.RoundRectangle2D.Float(dx, dy, drawW, drawH, arc, arc));

        if (tileImg != null) {
            g2.drawImage(tileImg, dx, dy, drawW, drawH, null);
        } else {
            // Fallback: solid parchment with a big letter
            g2.setColor(Theme.TILE_BG);
            g2.fillRoundRect(dx, dy, drawW - 1, drawH - 1, 12, 12);
            g2.setColor(Theme.INK);
            g2.setFont(Theme.LETTER);
            g2.drawString(tileId, dx + drawW / 2 - 14, dy + drawH / 2);
        }
        g2.setClip(oldClip);

        // Hover highlight: a translucent parchment overlay
        if (hovered) {
            g2.setColor(new Color(0xFF, 0xF4, 0xC8, 70));
            g2.fillRoundRect(dx, dy, drawW - 1, drawH - 1, 10, 10);
        }

        // Border: FLAME glow when the local player can place here; muted ink otherwise.
        boolean clickable = phase != null && phase.isTileClickable(tileId);
        g2.setColor(clickable ? Theme.FLAME : new Color(0x2E, 0x1F, 0x14, 200));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(dx + 1, dy + 1, drawW - 3, drawH - 3, 10, 10);

        // Totem occupant: a small standee marker tucked into the bottom-right
        // corner so it never covers the tile's card slot or the art the player
        // is choosing from.
        if (occupantColor != null) {
            BufferedImage totem = Resources.totemForColor(occupantColor);
            int th = (int) (drawH * 0.34);
            int tw = (int) (th * 0.52);
            int tx = dx + drawW - tw - Math.max(4, drawW / 14);
            int ty = dy + drawH - th - Math.max(4, drawH / 22);
            if (totem != null) {
                g2.drawImage(totem, tx, ty, tw, th, null);
            } else {
                g2.setColor(occupantColor);
                g2.fillOval(tx, ty + th / 3, tw, tw);
                g2.setColor(Theme.INK);
                g2.drawOval(tx, ty + th / 3, tw, tw);
            }
        }

        g2.dispose();
    }
}
