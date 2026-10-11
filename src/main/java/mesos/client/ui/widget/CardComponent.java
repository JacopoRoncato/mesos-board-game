package mesos.client.ui.widget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import mesos.client.ui.resource.CardArtManifest;
import mesos.client.ui.resource.Resources;
import mesos.client.ui.resource.Theme;

/**
 * One card slot. Renders either the real MESOS card art (face-down ⇒
 * era-specific tribal back; face-up ⇒ the page-N front PNG resolved
 * via a hash-of-name placeholder, until the real manifest lands).
 *
 * The slot enforces the printed 5:7 card aspect ratio so cards don't
 * look squashed regardless of the row layout.
 */
public class CardComponent extends JComponent {

    public enum Kind { EMPTY, BACK, CHARACTER, BUILDING, EVENT }

    private Kind kind = Kind.EMPTY;
    private String label = "";
    private String era = null;   // "I" / "II" / "III" — affects the card back art
    private boolean pickable = false;
    private boolean selected = false;

    public CardComponent() {
        setPreferredSize(new Dimension(Theme.scale(150), Theme.scale(210)));    // 5:7 portrait
        setMinimumSize(new Dimension(Theme.scale(110), Theme.scale(154)));
        setOpaque(false);
    }

    public void setKind(Kind kind) { this.kind = kind; repaint(); }
    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        // Infer era from synthetic names like "Hunter-I-3", "Sustenance-II-0".
        this.era = inferEra(this.label);
        repaint();
    }
    public void setEra(String era) { this.era = era; repaint(); }
    public Kind getKind() { return kind; }
    public void setPickable(boolean p) { if (pickable != p) { pickable = p; repaint(); } }
    public boolean isPickable() { return pickable; }
    public void setSelected(boolean s) { if (selected != s) { selected = s; repaint(); } }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Enforce 5:7 aspect ratio inside the available area: letterbox.
        int areaW = getWidth();
        int areaH = getHeight();
        double targetAspect = 5.0 / 7.0;
        int drawW = areaW;
        int drawH = (int) (areaW / targetAspect);
        if (drawH > areaH) {
            drawH = areaH;
            drawW = (int) (areaH * targetAspect);
        }
        int dx = (areaW - drawW) / 2;
        int dy = (areaH - drawH) / 2;

        if (kind == Kind.EMPTY) {
            // Translucent white fill so sky shows through
            g2.setColor(new Color(0xFF, 0xFF, 0xFF, 0x33));
            g2.fillRoundRect(dx, dy, drawW, drawH, 8, 8);
            // Dashed ink border
            g2.setColor(new Color(0x0F, 0x08, 0x07, 0x55));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                         0f, new float[]{6f, 4f}, 0f));
            g2.drawRoundRect(dx + 1, dy + 1, drawW - 3, drawH - 3, 8, 8);
            g2.dispose();
            return;
        }

        // Clip drawing to a rounded rect so the printed card corners blend with
        // the parchment playmat below instead of showing the PDF's white margin.
        int arc = Math.max(6, drawW / 18);
        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(new java.awt.geom.RoundRectangle2D.Float(dx, dy, drawW, drawH, arc, arc));

        BufferedImage art = imageFor(kind, label, era);
        if (art != null) {
            g2.drawImage(art, dx, dy, drawW, drawH, null);
        } else {
            paintFallback(g2, dx, dy, drawW, drawH);
        }
        g2.setClip(oldClip);

        // BGA-style frame: thin cream border + brown outline (type is shown by the art's corner icons).
        g2.setColor(Theme.CREAM);
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRoundRect(dx + 2, dy + 2, drawW - 5, drawH - 5, arc, arc);
        g2.setColor(Theme.INK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(dx, dy, drawW - 1, drawH - 1, arc, arc);

        // Action-phase pick highlight: gold border when pickable, brighter/thicker when selected.
        if (selected || pickable) {
            g2.setColor(selected ? Theme.CORAL : Theme.SUN);
            g2.setStroke(new BasicStroke(selected ? 6f : 4f));
            g2.drawRoundRect(dx + 1, dy + 1, drawW - 3, drawH - 3, arc, arc);
        }

        g2.dispose();
    }

    private BufferedImage imageFor(Kind k, String name, String era) {
        if (k == Kind.BACK) return Resources.cardBack(era);
        if (name == null || name.isEmpty()) return Resources.cardBack(era);
        int pageIndex = CardArtManifest.pageForName(name);
        BufferedImage img = Resources.cardFront(pageIndex);
        return img != null ? img : Resources.cardBack(era);
    }

    /** BGA-style card-type frame colour: characters green, buildings blue, events red. */
    private Color frameColor() {
        return switch (kind) {
            case CHARACTER -> Theme.CARD_GREEN;
            case BUILDING  -> Theme.CARD_BLUE;
            case EVENT     -> Theme.CARD_RED;
            default        -> Theme.INK;
        };
    }

    private static String inferEra(String name) {
        if (name == null) return null;
        if (name.contains("-I-") || name.endsWith("-I"))   return "I";
        if (name.contains("-II-") || name.endsWith("-II")) return "II";
        if (name.contains("-III") )                        return "III";
        return null;
    }

    private void paintFallback(Graphics2D g2, int dx, int dy, int w, int h) {
        Color fill;
        switch (kind) {
            case BACK:      fill = Theme.CARD_BACK;       break;
            case CHARACTER: fill = Theme.CARD_CHARACTER;  break;
            case BUILDING:  fill = Theme.CARD_BUILDING;   break;
            case EVENT:     fill = Theme.CARD_EVENT;      break;
            default:        fill = Theme.BG_PARCHMENT_DARK;
        }
        g2.setColor(fill);
        g2.fillRoundRect(dx, dy, w - 1, h - 1, 6, 6);
        if (!label.isEmpty()) {
            g2.setColor(Theme.INK);
            g2.setFont(Theme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            int x = dx + (w - fm.stringWidth(label)) / 2;
            int y = dy + h / 2 + fm.getAscent() / 2;
            g2.drawString(label, x, y);
        }
    }
}
