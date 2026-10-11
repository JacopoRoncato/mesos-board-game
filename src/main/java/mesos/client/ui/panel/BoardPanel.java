package mesos.client.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.LinearGradientPaint;
import java.awt.image.BufferedImage;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import mesos.client.ui.model.GameSnapshot;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.PlayerColors;
import mesos.client.ui.resource.Theme;
import mesos.client.ui.widget.CardComponent;
import mesos.client.ui.widget.OfferTrackPanel;
import mesos.client.ui.widget.RowPanel;
import mesos.client.ui.widget.TurnOrderTilePanel;

/**
 * The visual board. Paints a wooden-table look with a parchment "playmat"
 * inside, then arranges the three game zones:
 *
 *   [Top row of cards .....................................]
 *   [Turn Order tile] [A][B][C][D][E][F][G]
 *   [Bottom row of cards ..................................]
 *
 * Modular by player count: hides tessera A when fewer than 5 players,
 * and reconfigures the turn-order tile.
 */
public class BoardPanel extends JPanel {

    private final GameSnapshot snapshot;
    private final RowPanel topRow;
    private final RowPanel bottomRow;
    private final OfferTrackPanel offerTrack;
    private final TurnOrderTilePanel turnOrder;
    private final PhaseController phase;
    private final JLabel eraBadge;
    private int lastPlayerCount = -1;

    /**
     * Constructs the board panel.
     *
     * @param snapshot     live game snapshot subscribed for updates
     * @param phase        phase controller driving pickable/selected state on cards
     * @param placeHandler called when the player clicks a free offer tile
     */
    public BoardPanel(GameSnapshot snapshot, PhaseController phase,
                      OfferTrackPanel.PlaceHandler placeHandler) {
        this.snapshot = snapshot;
        this.phase = phase;
        setBackground(Theme.BG_STONE);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(Theme.scale(18), Theme.scale(18), Theme.scale(18), Theme.scale(18)));

        topRow     = new RowPanel("Fila superiore", 6);
        bottomRow  = new RowPanel("Fila inferiore", 3);
        offerTrack = new OfferTrackPanel(phase, placeHandler);
        turnOrder  = new TurnOrderTilePanel(2);

        JPanel top = new JPanel(new BorderLayout(Theme.scale(8), 0));
        top.setOpaque(false);
        top.add(topRow, BorderLayout.CENTER);

        eraBadge = new JLabel("Era ?", SwingConstants.CENTER);
        eraBadge.setOpaque(true);
        eraBadge.setBackground(Theme.BG_DARK_STONE);
        eraBadge.setForeground(Theme.BG_PARCHMENT);
        eraBadge.setFont(Theme.HEADING.deriveFont((float) Theme.scale(18)));
        eraBadge.setPreferredSize(new Dimension(Theme.scale(90), Theme.scale(72)));
        eraBadge.setBorder(BorderFactory.createLineBorder(Theme.TILE_BORDER, 2));
        top.add(eraBadge, BorderLayout.EAST);

        // BorderLayout (not GridBag): WEST keeps the turn-order tile at its
        // preferred width; the offer track in CENTER takes the rest and shrinks
        // its tiles to fit. With GridBag the offer track's large preferred width
        // (landscape tiles) squeezed the weightx=0 turn-order column to ~1px,
        // making the turn-order tile invisible.
        JPanel mid = new JPanel(new BorderLayout(Theme.scale(10), 0));
        mid.setOpaque(false);
        mid.add(turnOrder, BorderLayout.WEST);
        mid.add(offerTrack, BorderLayout.CENTER);

        GridBagConstraints oc = new GridBagConstraints();
        oc.gridx = 0;
        oc.fill = GridBagConstraints.BOTH;
        oc.weightx = 1.0;

        oc.gridy = 0; oc.weighty = 1.0;
        add(top, oc);

        oc.gridy = 1; oc.weighty = 1.5;   // offer track = prominent central band
        add(mid, oc);

        oc.gridy = 2; oc.weighty = 1.0;
        add(bottomRow, oc);

        topRow.setOnSlotClick(i -> onSlotClick(true, i));
        bottomRow.setOnSlotClick(i -> onSlotClick(false, i));
        snapshot.addListener(this::refresh);
        phase.addListener(this::refresh);
        refresh();
    }

    private void onSlotClick(boolean top, int index) {
        if (phase != null && phase.rowPickable(top)) {
            phase.togglePick(top, index);   // fires listener → refresh → visuals update
        }
    }

    private void applyPickState(RowPanel row, boolean top) {
        boolean pickable = phase != null && phase.rowPickable(top);
        for (int i = 0; ; i++) {
            CardComponent c = row.slot(i);
            if (c == null) break;
            CardComponent.Kind k = c.getKind();
            c.setPickable(pickable && k != CardComponent.Kind.EMPTY
                                   && k != CardComponent.Kind.BACK
                                   && k != CardComponent.Kind.EVENT);
            c.setSelected(phase != null && phase.isPicked(top, i));
        }
    }

    private void refresh() {
        eraBadge.setText("Era " + snapshot.getEra());

        // Modular: when player count changes, reconfigure offer track + TO tile.
        int n = snapshot.getPlayers().size();
        if (n > 0 && n != lastPlayerCount) {
            lastPlayerCount = n;
            offerTrack.configureForPlayerCount(n);
            turnOrder.configure(n);
        }
        // Show only the tiles the server actually dealt (e.g. B,C,D for 2 players).
        offerTrack.setVisibleTiles(snapshot.getTiles().keySet());

        offerTrack.clearAllOccupants();
        turnOrder.clearOccupants();
        java.util.List<GameSnapshot.PlayerState> playerList = new java.util.ArrayList<>(snapshot.getPlayers());
        for (int i = 0; i < playerList.size(); i++) {
            GameSnapshot.PlayerState p = playerList.get(i);
            String tile = p.selectedTile;
            if (tile != null && !tile.isEmpty() && !"-".equals(tile)) {
                offerTrack.setOccupant(tile, PlayerColors.colorFromServerColor(p.totemColor));
            } else {
                // Player is on the turn order tile: their list index = their slot index.
                turnOrder.setOccupant(i, PlayerColors.colorFromServerColor(p.totemColor));
            }
        }

        renderRow(topRow, snapshot.getTopRow(), snapshot.getEra());
        renderRow(bottomRow, snapshot.getBottomRow(), snapshot.getEra());
        applyPickState(topRow, true);
        applyPickState(bottomRow, false);
    }

    private static void renderRow(RowPanel row, java.util.List<GameSnapshot.CardInfo> contents,
                                  String fallbackEra) {
        if (contents.isEmpty()) return;
        row.setSlotCount(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            CardComponent slot = row.slot(i);
            if (slot == null) continue;
            GameSnapshot.CardInfo info = contents.get(i);
            slot.setKind(info.kind);
            slot.setLabel(info.name);
            // Face-down: use the current era to pick the right back art.
            if (info.kind == CardComponent.Kind.BACK) {
                slot.setEra(fallbackEra);
            }
        }
        row.revalidate();
        row.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = getWidth(), h = Math.max(1, getHeight());
        // Warm fiery cave-painting table (BGA atmosphere) instead of the old teal sky.
        g2.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0f, 0.5f, 1f},
                new Color[]{Theme.ORANGE, Theme.CORAL, Theme.BRICK}));
        g2.fillRect(0, 0, w, h);
        BufferedImage cave = caveArt();
        if (cave != null) {
            // Scale the cave-painting to COVER the whole board (anchored at the
            // bottom) as a faint warm texture. A bottom *band* left a hard top
            // edge that read as the board being "cut in half" once the offer
            // band stopped covering it — a full-bleed low-alpha wash has no edge.
            double scale = Math.max((double) w / cave.getWidth(), (double) h / cave.getHeight());
            int dw = (int) Math.ceil(cave.getWidth() * scale);
            int dh = (int) Math.ceil(cave.getHeight() * scale);
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g2.drawImage(cave, (w - dw) / 2, h - dh, dw, dh, null);
            g2.setComposite(old);
        }
        g2.dispose();
    }

    private static BufferedImage caveArt;
    private static boolean caveTried;
    private static BufferedImage caveArt() {
        if (!caveTried) {
            caveTried = true;
            try { caveArt = javax.imageio.ImageIO.read(BoardPanel.class.getResourceAsStream("/img/playmat/sfondo.png")); }
            catch (Exception e) { caveArt = null; }
        }
        return caveArt;
    }
}
