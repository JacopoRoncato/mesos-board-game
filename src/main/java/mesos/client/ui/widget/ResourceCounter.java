package mesos.client.ui.widget;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import mesos.client.ui.resource.Theme;

/**
 * Resource counter: draws a token image (food or PP) with the numeric value
 * centered below it. Auto-detects the bounding box of non-transparent pixels
 * so the visible content is always centered regardless of asymmetric padding
 * in the source PNG.
 */
public final class ResourceCounter extends JComponent {

    private final String imagePath;
    private BufferedImage image; // always TYPE_INT_ARGB after load
    private int[] contentBounds; // [minX, minY, maxX, maxY] in image pixels
    private int value;

    public ResourceCounter(String imagePath, int value) {
        this.imagePath = imagePath;
        this.value = value;
        setOpaque(false);
        setPreferredSize(new Dimension(Theme.scale(56), Theme.scale(76)));
    }

    public void setValue(int v) {
        if (v == value) return;
        value = v;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Fill with nearest opaque ancestor background so transparent PNG
        // pixels show the correct colour (Swing double-buffering may not
        // propagate the parent background reliably for non-opaque components).
        Color bg = nearestOpaqueBackground();
        if (bg != null) {
            g.setColor(bg);
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        if (image == null) loadImage();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        Font numFont = new Font("Serif", Font.BOLD, Theme.scale(16));
        g2.setFont(numFont);
        FontMetrics fm = g2.getFontMetrics();
        int numH = fm.getAscent() + fm.getDescent();
        int gap = 4;
        int imgSize = h - numH - gap;
        if (imgSize < 8) imgSize = 8;

        if (image != null && contentBounds != null) {
            int cMinX = contentBounds[0], cMinY = contentBounds[1];
            int cMaxX = contentBounds[2], cMaxY = contentBounds[3];
            int contentW = cMaxX - cMinX + 1;
            int contentH = cMaxY - cMinY + 1;

            // FIT (max): preserve the token's natural shape and aspect — the
            // prestige piece stays octagonal and the food token stays a disc, and
            // the whole token is shown. No circular slot/outline behind it, so a
            // non-circular token never leaves a dark ring showing at its edges.
            double scale = (double) imgSize / Math.max(contentW, contentH);
            int scaledW = (int) (image.getWidth()  * scale);
            int scaledH = (int) (image.getHeight() * scale);

            double contentCX = (cMinX + cMaxX) / 2.0 * scale;
            double contentCY = (cMinY + cMaxY) / 2.0 * scale;

            int drawX = (int) (w / 2.0 - contentCX);
            int drawY = (int) (imgSize / 2.0 - contentCY);

            // Explicit SRC_OVER so alpha channel of the ARGB image is honoured.
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            g2.drawImage(image, drawX, drawY, scaledW, scaledH, null);
        } else {
            // Fallback when the token PNG is missing/unreadable: a neutral
            // placeholder disc in the icon box, so the counter never collapses
            // to a bare number (which would silently hide a broken asset path).
            int d = Math.max(8, Math.min(w, imgSize));
            int ox = (w - d) / 2;
            int oy = (imgSize - d) / 2;
            g2.setColor(Theme.BG_STONE);
            g2.fillOval(ox, oy, d, d);
            g2.setColor(Theme.INK);
            g2.drawOval(ox, oy, d, d);
        }

        String text = String.valueOf(value);
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = imgSize + gap + fm.getAscent();
        g2.setColor(Theme.INK);
        g2.drawString(text, tx, ty);

        g2.dispose();
    }

    private Color nearestOpaqueBackground() {
        Container c = getParent();
        while (c != null && !c.isOpaque()) c = c.getParent();
        return (c != null) ? c.getBackground() : Theme.CREAM;
    }

    private void loadImage() {
        try (var in = ResourceCounter.class.getResourceAsStream(imagePath)) {
            if (in == null) return;
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) return;
            // Force ARGB so the alpha channel is always present regardless of
            // how the PNG was encoded (some decoders return TYPE_INT_RGB).
            image = toArgb(raw);
            computeContentBounds();
        } catch (IOException ignored) {}
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    // Scans all pixels; finds the bounding box of those with alpha > 10.
    private void computeContentBounds() {
        int iw = image.getWidth();
        int ih = image.getHeight();
        int minX = iw, minY = ih, maxX = 0, maxY = 0;
        for (int y = 0; y < ih; y++) {
            for (int x = 0; x < iw; x++) {
                if (((image.getRGB(x, y) >> 24) & 0xFF) > 10) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        contentBounds = (maxX >= minX)
                ? new int[]{minX, minY, maxX, maxY}
                : new int[]{0, 0, iw - 1, ih - 1};
    }

}
