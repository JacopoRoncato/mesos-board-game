package mesos.client.ui.widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import mesos.client.ui.resource.Resources;
import mesos.client.ui.resource.Theme;

/**
 * The Turn Order tile — the "Cave" illustration sliced from
 * fustelle_front_p1. The cave PDF already shows the 5 spaces with their
 * food-bonus / penalty icons painted in. We overlay totem standees on
 * the right space when a player occupies it.
 *
 * Each slot map entry is {cx, cy}: the normalized centre of the white
 * rectangle on that specific turn-order-Np.png image (0..1 from the
 * top-left corner of the drawn image). Measured per-image so each
 * player-count variant has its own precise coordinates.
 */
public class TurnOrderTilePanel extends JComponent {

    public static class Space {
        public final int foodBonus;
        public final boolean lastSpace;
        public Color occupantColor;
        public Space(int foodBonus, boolean lastSpace) {
            this.foodBonus = foodBonus;
            this.lastSpace = lastSpace;
        }
    }

    // Vertical range {yTop, yBot} of the white slot column, as fractions of drawn
    // image height. Slots are distributed by linear interpolation so only two
    // values need tuning when images change. cx is fixed at 0.49 for all variants.
    // 2P/3P/5P: original calibrated values. 4P: shifted down by ~0.06 (user feedback).
    private static final double CX      = 0.49;
    private static final double[] RANGE_2P = {0.30, 0.49};
    private static final double[] RANGE_3P = {0.26, 0.61};
    private static final double[] RANGE_4P = {0.22, 0.73};
    private static final double[] RANGE_5P = {0.16, 0.84};

    private final List<Space> spaces = new ArrayList<>();
    private int playerCount = 2;
    private BufferedImage cave;
    /** w/h of the cave image; refreshed in {@link #configure} so the tile keeps its aspect. */
    private double caveAspect = 0.65;

    /**
     * Constructs a TurnOrderTilePanel configured for the given player count.
     *
     * @param playerCount number of players (2–5); determines the cave image and space count
     */
    public TurnOrderTilePanel(int playerCount) {
        configure(playerCount);
    }

    /**
     * Match the turn-order tile to the offer-track tiles: both fill the central
     * band's height. {@code OfferTrackPanel} sizes its tiles to the available
     * height; here we request a width = (bandHeight − inset) × image aspect so,
     * placed in {@code BorderLayout.WEST} with the band's full height, the
     * letterboxed cave fills that height too — same visual size as an offer tile.
     */
    @Override
    public Dimension getPreferredSize() {
        java.awt.Container parent = getParent();
        int bandH = (parent != null && parent.getHeight() > 0)
                ? parent.getHeight() : Theme.scale(300);
        int targetImgH = Math.max(1, bandH - Theme.scale(18));  // OfferTrackPanel top+bottom inset
        int w = (int) Math.round(targetImgH * caveAspect);
        return new Dimension(w, bandH);
    }

    /**
     * Reconfigures the tile for a different player count, reloading the cave image and
     * rebuilding the space list with the correct food-bonus values.
     *
     * @param newPlayerCount number of players (2–5)
     */
    public void configure(int newPlayerCount) {
        this.playerCount = newPlayerCount;
        this.cave = Resources.turnOrder(newPlayerCount);
        if (this.cave != null && this.cave.getHeight() > 0) {
            this.caveAspect = (double) this.cave.getWidth() / this.cave.getHeight();
        }
        spaces.clear();
        int[] bonuses;
        switch (newPlayerCount) {
            case 3:  bonuses = new int[]{2, 0, 0};       break;
            case 4:  bonuses = new int[]{2, 1, 0, 0};    break;
            case 5:  bonuses = new int[]{3, 1, 0, 0, 0}; break;
            case 2:
            default: bonuses = new int[]{1, 0};
        }
        for (int i = 0; i < bonuses.length; i++) {
            spaces.add(new Space(bonuses[i], i == bonuses.length - 1));
        }
        revalidate();
        repaint();
    }

    /** Removes all totem overlays from every space. */
    public void clearOccupants() {
        for (Space s : spaces) s.occupantColor = null;
        repaint();
    }

    /**
     * Sets the totem colour on the given turn-order space.
     *
     * @param spaceIndex zero-based space index
     * @param color      the totem's AWT colour
     */
    public void setOccupant(int spaceIndex, Color color) {
        if (spaceIndex < 0 || spaceIndex >= spaces.size()) return;
        spaces.get(spaceIndex).occupantColor = color;
        repaint();
    }

    /** Returns the current player count this panel is configured for. */
    public int getPlayerCount() { return playerCount; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth();
        int h = getHeight();

        if (cave != null) {
            // Letterbox the cave image (it's roughly portrait) inside the panel.
            double imgAspect = (double) cave.getWidth() / cave.getHeight();
            double boxAspect = (double) w / h;
            int drawW, drawH;
            if (imgAspect > boxAspect) {
                drawW = w;
                drawH = (int) (w / imgAspect);
            } else {
                drawH = h;
                drawW = (int) (h * imgAspect);
            }
            int dx = (w - drawW) / 2;
            int dy = (h - drawH) / 2;
            int arc = Math.max(10, drawW / 12);
            // Cream frame behind the cave art so the turn-order tile reads as a
            // distinct tile against the warm board background (matching the offer
            // track) instead of blending into it.
            int fpad = Theme.scale(7);
            g2.setColor(Theme.CREAM);
            g2.fillRoundRect(dx - fpad, dy - fpad, drawW + 2 * fpad, drawH + 2 * fpad, arc, arc);
            g2.setColor(Theme.INK);
            g2.setStroke(new java.awt.BasicStroke(2.5f));
            g2.drawRoundRect(dx - fpad, dy - fpad, drawW + 2 * fpad - 1, drawH + 2 * fpad - 1, arc, arc);
            // Clip the cave to a rounded rect so the print margin doesn't show.
            java.awt.Shape oldClip = g2.getClip();
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(dx, dy, drawW, drawH, arc, arc));
            g2.drawImage(cave, dx, dy, drawW, drawH, null);
            g2.setClip(oldClip);

            // Overlay top-view totem images centred on each slot's white rectangle.
            // SLOT_MAP_Np gives {cx, cy} as fractions of the drawn image for each
            // player-count variant. The top-view PNGs are landscape (~4:3).
            double[][] slotMap = slotMapFor(spaces.size());
            int totemW = Math.max(12, (int) (drawW * 0.28));
            int totemH = Math.max(6,  (int) (totemW * 0.67));
            for (int i = 0; i < spaces.size() && i < slotMap.length; i++) {
                Space s = spaces.get(i);
                if (s.occupantColor == null) continue;
                BufferedImage totem = Resources.totemTopForColor(s.occupantColor);
                if (totem == null) continue;
                int slotCx = dx + (int) (drawW * slotMap[i][0]);
                int slotCy = dy + (int) (drawH * slotMap[i][1]);
                g2.drawImage(totem,
                        slotCx - totemW / 2, slotCy - totemH / 2,
                        totemW, totemH, null);
            }
        } else {
            // Fallback: simple stone-coloured strip.
            g2.setColor(Theme.BG_STONE);
            g2.fillRoundRect(2, 2, w - 4, h - 4, 12, 12);
        }

        g2.dispose();
    }

    private static double[][] slotMapFor(int slotCount) {
        double[] range = switch (slotCount) {
            case 2  -> RANGE_2P;
            case 3  -> RANGE_3P;
            case 5  -> RANGE_5P;
            default -> RANGE_4P;
        };
        double yTop = range[0], yBot = range[1];
        double[][] map = new double[slotCount][2];
        for (int i = 0; i < slotCount; i++) {
            map[i][0] = CX;
            map[i][1] = slotCount == 1 ? (yTop + yBot) * 0.5
                                       : yTop + i * (yBot - yTop) / (slotCount - 1);
        }
        return map;
    }
}
