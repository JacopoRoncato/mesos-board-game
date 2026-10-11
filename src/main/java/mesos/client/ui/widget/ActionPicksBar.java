package mesos.client.ui.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GuiPhase;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.model.PicksRemaining;
import mesos.client.ui.resource.Theme;

/**
 * Strip shown above the board while {@link GuiPhase#ACTION_MINE} is
 * active. Reads the picks-remaining counter from the PhaseController
 * and displays the residual count plus a hint.
 */
public final class ActionPicksBar extends JPanel {

    private final PhaseController phase;
    private final JLabel counter = label(22, Theme.FLAME, Font.BOLD);
    private final JLabel label   = label(12, Theme.CREAM, Font.PLAIN);
    private final JLabel hint    = label(12, Theme.CREAM, Font.ITALIC);

    /**
     * Constructs an ActionPicksBar.
     *
     * @param phase the phase controller supplying the remaining-picks counter
     */
    public ActionPicksBar(PhaseController phase) {
        this.phase = phase;
        setLayout(new BorderLayout(Theme.scale(16), 0));
        setOpaque(true);
        setBackground(Theme.INK);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.FLAME, 2),
                new EmptyBorder(Theme.scale(10), Theme.scale(14), Theme.scale(10), Theme.scale(14))));

        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(label); left.add(counter);

        add(left, BorderLayout.WEST);
        add(hint, BorderLayout.EAST);

        if (phase != null) phase.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        if (phase == null) {
            setVisible(false);
            return;
        }
        PicksRemaining pr = phase.getRemainingPicks();
        int total = phase.totalPickBudget();
        int done = total - pr.total();
        counter.setText(done + " / " + total + " totali");
        hint.setText(pr.isDone()
                ? "Pronto a chiudere il turno"
                : "Mancano " + pr.total() + " picks — clicca le carte evidenziate");
        label.setText("PICKS RIMASTI");
        setVisible(phase.getGuiPhase() == GuiPhase.ACTION_MINE);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private static JLabel label(int size, Color c, int style) {
        JLabel l = new JLabel("");
        l.setForeground(c);
        l.setFont(new Font("Serif", style, Theme.scale(size)));
        return l;
    }
}
