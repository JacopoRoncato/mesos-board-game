package mesos.client.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.LinearGradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GuiPhase;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.Theme;
import mesos.client.ui.widget.TotemBadge;

/**
 * Lobby screen — pick one of the 5 totem colours, send ColorMessage,
 * then wait for the others to pick theirs. When the lobby is full and
 * everyone has accepted, the "Inizia partita" button becomes visible.
 *
 * <p>The panel tolerates a {@code null} {@link PhaseController} (e.g.,
 * during unit tests) and shows a fallback lobby state in that case.
 */
public final class LobbyPanel extends JPanel {

    private final PhaseController phase;
    private final Consumer<String> sendColor;   // wires VirtualServer.sendColor
    private final Runnable sendStart;

    private final Map<TotemBadge.Colour, JPanel>      cards  = new HashMap<>();
    private final Map<TotemBadge.Colour, TotemBadge> badges = new HashMap<>();
    private TotemBadge.Colour selected;

    private final JButton confirm        = primaryBtn("Conferma");
    private final JButton start          = primaryBtn("Inizia partita");
    private final JLabel  status         = label(13, Theme.CREAM, Font.PLAIN);
    private final JLabel  recoveryBanner = label(13, Theme.INK, Font.BOLD);

    /**
     * Constructs a LobbyPanel.
     *
     * @param phase      phase controller driving button visibility and colour greyout
     * @param sendColor  called with the lower-case view-colour name when "Conferma" is pressed
     * @param sendStart  called when the player presses "Inizia partita"
     */
    public LobbyPanel(PhaseController phase, Consumer<String> sendColor, Runnable sendStart) {
        this.phase = phase;
        this.sendColor = sendColor;
        this.sendStart = sendStart;

        setLayout(new BorderLayout(Theme.scale(12), Theme.scale(12)));
        // Opaque: paintComponent fills the whole panel with the gradient, so the
        // panel must declare it covers its bounds. Otherwise Swing's partial
        // repaints leave the previously-shown CountPanel buttons lingering in the
        // gaps between the totem cards ("2"/"5" bleeding into the selector).
        setOpaque(true);
        setBorder(new EmptyBorder(Theme.scale(40), Theme.scale(40), Theme.scale(40), Theme.scale(40)));

        recoveryBanner.setOpaque(true);
        recoveryBanner.setBackground(Theme.FLAME);
        recoveryBanner.setHorizontalAlignment(SwingConstants.CENTER);
        recoveryBanner.setBorder(new EmptyBorder(Theme.scale(6), Theme.scale(16), Theme.scale(6), Theme.scale(16)));
        recoveryBanner.setVisible(false);

        JLabel title = label(28, Theme.CREAM, Font.BOLD);
        title.setText("Scegli il tuo totem");
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel grid = new JPanel(new GridLayout(1, 5, Theme.scale(16), Theme.scale(16)));
        grid.setOpaque(false);
        for (TotemBadge.Colour c : TotemBadge.Colour.values()) {
            JPanel card = colourCard(c);
            cards.put(c, card);
            grid.add(card);
        }

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.scale(16), Theme.scale(8)));
        south.setOpaque(false);
        south.add(confirm);
        south.add(start);

        confirm.addActionListener(e -> {
            if (selected != null && sendColor != null) {
                sendColor.accept(selected.name().toLowerCase(Locale.ROOT));
            }
        });
        start.addActionListener(e -> { if (sendStart != null) sendStart.run(); });

        JPanel north = new JPanel(new BorderLayout(0, Theme.scale(10)));
        north.setOpaque(false);
        north.add(recoveryBanner, BorderLayout.NORTH);
        north.add(title,          BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(grid,  BorderLayout.CENTER);
        JPanel bot = new JPanel(new BorderLayout(0, Theme.scale(6)));
        bot.setOpaque(false);
        bot.add(south, BorderLayout.NORTH);
        bot.add(status, BorderLayout.SOUTH);
        add(bot, BorderLayout.SOUTH);

        if (phase != null) phase.addListener(this::refresh);
        refresh();
    }

    private JPanel colourCard(TotemBadge.Colour c) {
        JPanel card = new JPanel(new BorderLayout(0, Theme.scale(4)));
        card.setOpaque(true);
        card.setBackground(Theme.CREAM);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 2),
                new EmptyBorder(Theme.scale(14), Theme.scale(8), Theme.scale(12), Theme.scale(8))));
        TotemBadge badge = new TotemBadge(c, 100);
        badge.setAlignmentX(CENTER_ALIGNMENT);
        badges.put(c, badge);
        card.add(badge, BorderLayout.CENTER);
        JLabel name = label(16, Theme.INK, Font.BOLD);
        name.setText(displayName(c));
        name.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(name, BorderLayout.SOUTH);
        // Register the listener on every child so clicks on the badge image
        // and the name label are not silently absorbed (Swing does not bubble
        // mouse events from child to parent automatically).
        java.awt.event.MouseAdapter clicker = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onColourClicked(c, card);
            }
        };
        card.addMouseListener(clicker);
        badge.addMouseListener(clicker);
        name.addMouseListener(clicker);
        return card;
    }

    private void onColourClicked(TotemBadge.Colour c, JPanel card) {
        if (phase != null && phase.getGuiPhase() != GuiPhase.LOBBY_PICKING_COLOR) return;
        if (phase != null && phase.takenColors().contains(c.name().toLowerCase(Locale.ROOT))) return;
        if (selected == c) return;
        // Reset previous selection border
        if (selected != null) {
            cards.get(selected).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.INK, 2),
                    new EmptyBorder(Theme.scale(14), Theme.scale(8), Theme.scale(12), Theme.scale(8))));
        }
        selected = c;
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.FLAME, 4),
                new EmptyBorder(Theme.scale(12), Theme.scale(6), Theme.scale(10), Theme.scale(6))));
    }

    private void refresh() {
        if (phase == null) {
            setVisible(true);
            confirm.setVisible(true);
            start.setVisible(false);
            status.setText("Stato: lobby (phase=null)");
            return;
        }
        GuiPhase p = phase.getGuiPhase();
        boolean lobby = p.isLobby();
        setVisible(lobby);
        confirm.setVisible(p == GuiPhase.LOBBY_PICKING_COLOR);
        start.setVisible(p == GuiPhase.READY_TO_START);
        status.setText(switch (p) {
            case LOBBY_PICKING_COLOR  -> "Scegli un colore, poi premi Conferma";
            case LOBBY_WAITING_OTHERS -> "In attesa degli altri giocatori...";
            case READY_TO_START       -> "Tutti pronti - premi Inizia partita";
            default                    -> "";
        });
        // Repaint cards to reflect which colours are already taken
        Set<String> taken = phase.takenColors();
        for (Map.Entry<TotemBadge.Colour, JPanel> e : cards.entrySet()) {
            JPanel card = e.getValue();
            boolean t = taken.contains(e.getKey().name().toLowerCase(Locale.ROOT));
            card.setEnabled(!t);
            card.setBackground(t ? Theme.CREAM_DIM : Theme.CREAM);
            badges.get(e.getKey()).setDimmed(t);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        float h = Math.max(1f, getHeight());
        g2.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0f, 0.55f, 1f},
                new Color[]{Theme.ORANGE, Theme.CORAL, Theme.BRICK}));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    /** Shows the recovery banner with current reconnection progress. EDT-safe. */
    public void setRecoveryMode(int current, int total) {
        recoveryBanner.setText("Partita salvata trovata — riconnessione in corso ("
                + current + "/" + total + " giocatori)");
        recoveryBanner.setVisible(true);
        revalidate();
        repaint();
    }

    private static String displayName(TotemBadge.Colour c) {
        return switch (c) {
            case RED    -> "Rosso";
            case YELLOW -> "Giallo";
            case CYAN   -> "Ciano";
            case PURPLE -> "Viola";
            case WHITE  -> "Bianco";
        };
    }

    private static JLabel label(int size, Color colour, int style) {
        JLabel l = new JLabel("");
        l.setForeground(colour);
        l.setFont(new Font("Serif", style, Theme.scale(size)));
        return l;
    }

    private static JButton primaryBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(Theme.FLAME);
        b.setForeground(Theme.INK);
        b.setFont(new Font("Serif", Font.BOLD, Theme.scale(14)));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 3),
                new EmptyBorder(Theme.scale(10), Theme.scale(24), Theme.scale(10), Theme.scale(24))));
        b.setFocusPainted(false);
        return b;
    }
}
