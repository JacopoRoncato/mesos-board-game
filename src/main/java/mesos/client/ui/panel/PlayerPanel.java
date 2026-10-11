package mesos.client.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GameSnapshot;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.Theme;
import mesos.client.ui.widget.ResourceCounter;
import mesos.client.ui.widget.TotemBadge;
import mesos.client.ui.widget.TribeCountsBar;

/**
 * Compact sidebar player panel: totem badge + nickname + food/PP + chosen tile,
 * plus an always-visible tribe counts bar (the "iconcine") and an expandable
 * view of the player's actual cards. Reads state from GameSnapshot and
 * PhaseController.
 *
 * <p><b>Modularity:</b> the always-visible counts ({@link TribeCountsBar}) are
 * CORE. The card expansion ({@link TribeCardsPanel}) is a removable module gated
 * by {@link #TRIBE_EXPAND_ENABLED}: set it {@code false} to keep only the
 * iconcine. The expansion is fully self-contained — the one-expanded-at-a-time
 * coordination lives here (collapsing sibling panels in the parent column), so
 * deleting the guarded blocks + {@code TribeCardsPanel} needs NO change to
 * {@code GuiClient}.
 */
public final class PlayerPanel extends JPanel {

    /** Master switch for the (removable) card-expansion module. */
    private static final boolean TRIBE_EXPAND_ENABLED = true;

    private final String nickname;
    private final TotemBadge.Colour colour;
    private final GameSnapshot snap;
    private final PhaseController phase;

    private final JLabel name = serif(13, Font.BOLD, Theme.INK);
    private final JLabel tile = serif(11, Font.ITALIC, Theme.INK_SOFT);
    private final ResourceCounter food = new ResourceCounter("/img/token/food.png", 0);
    private final ResourceCounter pp   = new ResourceCounter("/img/token/pp.png",   0);
    private final TribeCountsBar countsBar = new TribeCountsBar();

    // ── Expansion module (only wired when TRIBE_EXPAND_ENABLED) ──────────────
    private final JLabel chevron = serif(12, Font.BOLD, Theme.INK_SOFT);
    private final JPanel cardsHolder = new JPanel(new BorderLayout());
    private boolean expanded = false;

    /**
     * Constructs a PlayerPanel for the given player.
     *
     * @param nickname the player's nickname (used to look up state in the snapshot)
     * @param colour   the totem colour assigned to this player in the lobby
     * @param snap     live game snapshot subscribed for updates
     * @param phase    phase controller used to show the active-turn marker
     */
    public PlayerPanel(String nickname, TotemBadge.Colour colour,
                       GameSnapshot snap, PhaseController phase) {
        this.nickname = nickname;
        this.colour = colour;
        this.snap = snap;
        this.phase = phase;

        setLayout(new BorderLayout(Theme.scale(8), Theme.scale(4)));
        setOpaque(true);
        setBackground(Theme.CREAM);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 8, 2, 2, playerSwatch()),
                new EmptyBorder(Theme.scale(6), Theme.scale(8), Theme.scale(6), Theme.scale(8))));

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.scale(8), 0));
        head.setOpaque(false);
        head.add(new TotemBadge(colour, 28));
        head.add(name);
        if (TRIBE_EXPAND_ENABLED) {
            chevron.setText("▸");   // ▸ collapsed
            chevron.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter click = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { requestToggle(); }
            };
            chevron.addMouseListener(click);
            name.addMouseListener(click);
            head.add(chevron);
        }

        // Only the two resource counters go on this FlowLayout row. The tile
        // label used to live here too, which pushed the row past the sidebar
        // width and made it wrap; the row's fixed single-row height then clipped
        // the counters top/bottom. Keeping just food+pp here, with the tile on
        // its own line below, stops the wrap so the discs always render fully.
        JPanel hexes = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.scale(8), 0));
        hexes.setOpaque(false);
        hexes.add(food); hexes.add(pp);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        hexes.setAlignmentX(LEFT_ALIGNMENT);
        tile.setAlignmentX(LEFT_ALIGNMENT);
        countsBar.setAlignmentX(LEFT_ALIGNMENT);
        cardsHolder.setAlignmentX(LEFT_ALIGNMENT);
        cardsHolder.setOpaque(false);
        cardsHolder.setVisible(false);
        center.add(hexes);
        center.add(tile);
        center.add(Box.createVerticalStrut(Theme.scale(4)));
        center.add(countsBar);
        center.add(cardsHolder);

        add(head, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        if (snap != null) snap.addListener(this::refresh);
        if (phase != null) phase.addListener(this::refresh);
        refresh();
    }

    /** BoxLayout column: full width, but only as tall as needed (so accordion works). */
    @Override
    public Dimension getMaximumSize() {
        Dimension p = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, p.height);
    }

    private void requestToggle() {
        if (!TRIBE_EXPAND_ENABLED) return;
        boolean willExpand = !expanded;
        if (willExpand) collapseSiblings();   // one expanded at a time
        setExpanded(willExpand);
        Container parent = getParent();
        if (parent != null) { parent.revalidate(); parent.repaint(); }
    }

    /** One-expanded-at-a-time: collapse any other expanded PlayerPanel in the same column. */
    private void collapseSiblings() {
        Container parent = getParent();
        if (parent == null) return;
        for (Component c : parent.getComponents()) {
            if (c != this && c instanceof PlayerPanel pp && pp.isExpanded()) pp.setExpanded(false);
        }
    }

    public boolean isExpanded() { return expanded; }

    /** Expands or collapses the card view. Called by the coordinator (GuiClient). */
    public void setExpanded(boolean exp) {
        if (!TRIBE_EXPAND_ENABLED) return;
        expanded = exp;
        chevron.setText(exp ? "▾" : "▸");   // ▾ / ▸
        rebuildCards();
        cardsHolder.setVisible(exp);
        revalidate();
        repaint();
    }

    private void rebuildCards() {
        cardsHolder.removeAll();
        if (expanded) {
            GameSnapshot.PlayerState me = (snap != null) ? snap.getPlayer(nickname) : null;
            cardsHolder.add(new TribeCardsPanel(me), BorderLayout.CENTER);
        }
    }

    private void refresh() {
        GameSnapshot.PlayerState me = (snap != null) ? snap.getPlayer(nickname) : null;
        if (me == null) return;
        boolean acting = phase != null && nickname.equals(phase.getActivePlayerNickname());
        name.setText(nickname + (acting ? "  *" : ""));
        food.setValue(me.food);
        pp.setValue(me.prestige);
        String t = (me.selectedTile == null || "-".equals(me.selectedTile))
                 ? "- nessuna tessera -"
                 : "tessera " + me.selectedTile;
        tile.setText(t);
        countsBar.update(me);
        if (expanded) { rebuildCards(); cardsHolder.revalidate(); }
        repaint();
    }

    private Color playerSwatch() {
        return switch (colour) {
            case RED    -> Theme.P_RED;
            case YELLOW -> Theme.P_YELLOW;
            case CYAN   -> Theme.P_CYAN;
            case PURPLE -> Theme.P_PURPLE;
            case WHITE  -> Theme.P_WHITE;
        };
    }

    private static JLabel serif(int size, int style, Color c) {
        JLabel l = new JLabel("");
        l.setForeground(c);
        l.setFont(new Font("Serif", style, Theme.scale(size)));
        return l;
    }
}
