package mesos.client.ui.widget;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import mesos.client.ui.resource.Theme;

/**
 * The "buildings" token. Unlike the character types, building cards carry no
 * croppable corner badge, so this token is drawn procedurally: a stylised hut
 * (triangular roof + body) on an earthy disc, plus the shared count badge.
 * Faded (alpha 0.5) when the count is 0.
 */
public final class BuildingBadge extends JComponent {

    private static final Color EARTH = new Color(0x8A, 0x5A, 0x2B);

    private int count;

    public BuildingBadge(int count) {
        this.count = count;
        int s = Theme.scale(26);
        setPreferredSize(new Dimension(s, s));
        setOpaque(false);
        setToolTipText("Edifici: " + count);
    }

    public void setCount(int n) {
        if (n == count) return;
        count = n;
        setToolTipText("Edifici: " + count);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (count == 0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        }
        int w = getWidth(), h = getHeight();
        int pad = Math.max(1, w / 16);
        int aw = w - 2 * pad, ah = h - 2 * pad;

        java.awt.image.BufferedImage icon = mesos.client.ui.resource.Resources.buildingIcon();
        if (icon != null && icon.getWidth() > 0 && icon.getHeight() > 0) {
            double ia = (double) icon.getWidth() / icon.getHeight();
            int dw, dh;
            if (ia > (double) aw / ah) { dw = aw; dh = (int) (aw / ia); }
            else                        { dh = ah; dw = (int) (ah * ia); }
            g2.drawImage(icon, pad + (aw - dw) / 2, pad + (ah - dh) / 2, dw, dh, null);
        } else {
            // Fallback (no PNG): procedural hut on a circular earth disc.
            int d = Math.min(aw, ah);
            int ox = (w - d) / 2;
            int oy = (h - d) / 2;
            g2.setColor(EARTH);
            g2.fillOval(ox, oy, d, d);
            g2.setColor(Theme.CREAM);
            int cx = w / 2;
            int bodyW = d * 44 / 100, bodyH = d * 30 / 100;
            int bodyX = cx - bodyW / 2;
            int bodyY = oy + d * 52 / 100;
            g2.fillRect(bodyX, bodyY, bodyW, bodyH);
            int roofHalf = bodyW * 70 / 100;
            int[] xs = { cx - roofHalf, cx, cx + roofHalf };
            int[] ys = { bodyY, oy + d * 28 / 100, bodyY };
            g2.fillPolygon(xs, ys, 3);
        }

        TypeTokenBadge.drawCountBadge(g2, w, h, count);
        g2.dispose();
    }
}
