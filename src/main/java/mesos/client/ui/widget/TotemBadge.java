package mesos.client.ui.widget;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import mesos.client.ui.resource.Theme;

/**
 * Renders one of the 5 MESOS totem PNGs (red / yellow / cyan / purple /
 * white) at a fixed pixel size. Used in the lobby colour picker, on the
 * offer track as the occupant marker, and inside PlayerPanel banners.
 *
 * <p>Images are loaded from {@code /img/totem/<colour>.png} on the
 * classpath. Caches one BufferedImage per colour so repeated badges
 * share memory.
 */
public final class TotemBadge extends JComponent {

    public enum Colour { RED, YELLOW, CYAN, PURPLE, WHITE }

    private static final Map<Colour, BufferedImage> CACHE = new HashMap<>();

    private final Colour colour;
    private final int targetSize;
    private boolean dimmed = false;

    public void setDimmed(boolean d) {
        if (d == dimmed) return;
        dimmed = d;
        repaint();
    }

    /**
     * Constructs a TotemBadge.
     *
     * @param colour     the totem colour to render
     * @param targetSize preferred width in pixels; height is scaled at 1.4× for a portrait standee
     */
    public TotemBadge(Colour colour, int targetSize) {
        this.colour = colour;
        this.targetSize = targetSize;
        setPreferredSize(new Dimension(Theme.scale(targetSize), (int) (Theme.scale(targetSize) * 1.4)));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage img = loadCached(colour);
        if (img == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int w = getWidth();
        int h = getHeight();
        double scale = Math.min(w / (double) img.getWidth(), h / (double) img.getHeight());
        int drawW = (int) (img.getWidth() * scale);
        int drawH = (int) (img.getHeight() * scale);
        int x = (w - drawW) / 2;
        int y = (h - drawH) / 2;
        g2.drawImage(img, x, y, drawW, drawH, null);
        if (dimmed) {
            int margin = Math.max(6, drawW / 8);
            int strokeW = Math.max(4, drawW / 10);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x + margin, y + margin, x + drawW - margin, y + drawH - margin);
            g2.drawLine(x + drawW - margin, y + margin, x + margin, y + drawH - margin);
        }
        g2.dispose();
    }

    private static synchronized BufferedImage loadCached(Colour c) {
        return CACHE.computeIfAbsent(c, key -> {
            String path = "/img/totem/" + key.name().toLowerCase(Locale.ROOT) + ".png";
            try (var in = TotemBadge.class.getResourceAsStream(path)) {
                return (in != null) ? ImageIO.read(in) : null;
            } catch (IOException e) { return null; }
        });
    }
}
