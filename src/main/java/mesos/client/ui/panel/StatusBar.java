package mesos.client.ui.panel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GameSnapshot;
import mesos.client.ui.model.GuiPhase;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.Theme;

/**
 * Top strip showing the current GUI phase, round/era/player list, and
 * whose turn it is. Painted with a NIGHT_MID→NIGHT_DEEP gradient and a
 * 3 px FLAME bottom border.
 *
 * <p>State derives entirely from {@link GameSnapshot} (round, era, player
 * list) and {@link PhaseController} (GuiPhase, isMyTurn). Both are
 * optional at construction time: passing {@code null} for
 * {@code PhaseController} yields neutral placeholder text until Task 21
 * wires the real controller.
 */
public final class StatusBar extends JPanel {

    private final GameSnapshot snap;
    private final PhaseController phase;
    private final JLabel chip = chipLabel();
    private final JLabel info = whiteLabel(Font.PLAIN, 13);
    private final JLabel turn = flameLabel();

    /**
     * Creates a StatusBar.
     *
     * @param snap  live game snapshot (must not be null)
     * @param phase phase controller; may be {@code null} (scaffold mode — Task 21)
     */
    public StatusBar(GameSnapshot snap, PhaseController phase) {
        this.snap  = snap;
        this.phase = phase;
        setLayout(new BorderLayout(Theme.scale(12), 0));
        setBorder(new EmptyBorder(Theme.scale(10), Theme.scale(18), Theme.scale(10), Theme.scale(18)));
        setOpaque(false);
        add(chip, BorderLayout.WEST);
        add(info, BorderLayout.CENTER);
        add(turn, BorderLayout.EAST);
        snap.addListener(this::refresh);
        if (phase != null) phase.addListener(this::refresh);
        refresh();
    }

    // ── legacy single-arg constructor kept for binary compat if needed ────────

    /** @deprecated Use {@link #StatusBar(GameSnapshot, PhaseController)} instead. */
    @Deprecated
    public StatusBar(GameSnapshot snap) {
        this(snap, null);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private void refresh() {
        if (phase != null) {
            chip.setText(" " + phase.getGuiPhase().name() + " ");
            turn.setText(phase.getGuiPhase().isMyTurn() ? "★ Tocca a te" : "— attendi —");
        } else {
            chip.setText(" — ");
            turn.setText("");
        }
        info.setText("Round " + snap.getRound()
                   + " · Era " + snap.getEra()
                   + " · " + String.join(", ", snap.getPlayerNicks()));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, Theme.NIGHT_MID, 0, getHeight(), Theme.NIGHT_DEEP));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Theme.FLAME);
        g2.fillRect(0, getHeight() - 3, getWidth(), 3);
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, Theme.scale(48));
    }

    // ── factory helpers ───────────────────────────────────────────────────────

    private static JLabel chipLabel() {
        JLabel l = new JLabel(" — ", SwingConstants.CENTER);
        l.setOpaque(true);
        l.setBackground(Theme.FLAME);
        l.setForeground(Theme.INK);
        l.setFont(new Font("Serif", Font.BOLD, Theme.scale(12)));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 2),
                new EmptyBorder(Theme.scale(4), Theme.scale(12), Theme.scale(4), Theme.scale(12))));
        return l;
    }

    private static JLabel flameLabel() {
        JLabel l = new JLabel("");
        l.setForeground(Theme.FLAME);
        l.setFont(new Font("Serif", Font.BOLD, Theme.scale(13)));
        return l;
    }

    private static JLabel whiteLabel(int style, int size) {
        JLabel l = new JLabel("");
        l.setForeground(Theme.CREAM);
        l.setFont(new Font("Serif", style, Theme.scale(size)));
        return l;
    }
}
