package mesos.client.ui.widget;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import mesos.client.ui.resource.Resources;
import mesos.client.ui.resource.Theme;
import mesos.enums.CharacterType;

/**
 * A BoardGameArena-style type token: the character-type badge cropped from the
 * card art ({@link Resources#typeIcon}), scaled to fit, with a small count
 * badge in the bottom-right corner. The whole token is faded (alpha 0.5) when
 * the count is 0, to de-emphasise types the player hasn't collected.
 */
public final class TypeTokenBadge extends JComponent {

    private final CharacterType type;
    private int count;

    public TypeTokenBadge(CharacterType type, int count) {
        this.type = type;
        this.count = count;
        int s = Theme.scale(26);
        setPreferredSize(new Dimension(s, s));
        setOpaque(false);
        setToolTipText(label() + ": " + count);
    }

    public void setCount(int n) {
        if (n == count) return;
        count = n;
        setToolTipText(label() + ": " + count);
        repaint();
    }

    private String label() {
        return switch (type) {
            case HUNTER   -> "Cacciatore";
            case SHAMAN   -> "Sciamano";
            case ARTIST   -> "Artista";
            case BUILDER  -> "Architetto";
            case INVENTOR -> "Inventore";
            case GATHERER -> "Raccoglitore";
        };
    }

    /** Single-letter type initial, used as the missing-art fallback glyph. */
    private String letter() {
        return switch (type) {
            case HUNTER   -> "H";
            case SHAMAN   -> "S";
            case ARTIST   -> "A";
            case BUILDER  -> "B";
            case INVENTOR -> "I";
            case GATHERER -> "G";
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (count == 0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        }
        int w = getWidth(), h = getHeight();
        int pad = Math.max(1, w / 16);
        int aw = w - 2 * pad, ah = h - 2 * pad;

        BufferedImage icon = Resources.icon(type);            // dedicated PNG /img/icon/<type>.png
        if (icon == null) icon = Resources.typeIcon(type);    // fallback: corner crop from card art
        if (icon != null && icon.getWidth() > 0 && icon.getHeight() > 0) {
            double ia = (double) icon.getWidth() / icon.getHeight();
            int dw, dh;
            if (ia > (double) aw / ah) { dw = aw; dh = (int) (aw / ia); }
            else                        { dh = ah; dw = (int) (ah * ia); }
            g2.drawImage(icon, pad + (aw - dw) / 2, pad + (ah - dh) / 2, dw, dh, null);
        } else {
            // Fallback when the card art is missing: a neutral disc with the
            // type's initial, so the six types stay distinguishable instead of
            // collapsing to identical blank discs.
            g2.setColor(Theme.BG_STONE);
            g2.fillOval(pad, pad, aw, ah);
            g2.setColor(Theme.INK);
            g2.setFont(new Font("Serif", Font.BOLD, Math.max(9, ah * 6 / 10)));
            FontMetrics lfm = g2.getFontMetrics();
            String s = letter();
            g2.drawString(s, pad + (aw - lfm.stringWidth(s)) / 2,
                          pad + (ah + lfm.getAscent() - lfm.getDescent()) / 2);
        }

        drawCountBadge(g2, w, h, count);
        g2.dispose();
    }

    /** Shared count badge (dark disc + cream number) in the top-right corner. */
    static void drawCountBadge(Graphics2D g2, int w, int h, int count) {
        // Inset from the top-right corner and cap the diameter to the component
        // height, so the disc is never clipped flush against the top edge.
        int margin = Math.max(2, h / 9);
        int d = Math.max(Theme.scale(12), (h * 9) / 20);
        if (d > h - margin) d = Math.max(8, h - margin);
        int bx = w - d - margin, by = margin;
        g2.setColor(Theme.INK);
        g2.fillOval(bx, by, d - 1, d - 1);
        g2.setColor(Theme.CREAM);
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, d * 6 / 10)));
        FontMetrics fm = g2.getFontMetrics();
        String s = Integer.toString(count);
        int tx = bx + (d - fm.stringWidth(s)) / 2;
        int ty = by + (d + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(s, tx, ty);
    }
}
