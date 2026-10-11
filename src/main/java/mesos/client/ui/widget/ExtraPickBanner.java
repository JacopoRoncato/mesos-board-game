package mesos.client.ui.widget;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GuiPhase;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.Theme;

/**
 * Banner shown only during {@link GuiPhase#ACTION_EXTRA_PICK_MINE}:
 * yellow background, prominent "BONUS" title, and a SKIP button next
 * to the description.
 */
public final class ExtraPickBanner extends JPanel {

    private final PhaseController phase;
    private final JButton skip = new JButton("Salta");

    /**
     * Constructs an ExtraPickBanner.
     *
     * @param phase  the phase controller; the banner is only visible during ACTION_EXTRA_PICK_MINE
     * @param onSkip called when the player presses "Salta"
     */
    public ExtraPickBanner(PhaseController phase, Runnable onSkip) {
        this.phase = phase;
        setOpaque(true);
        setBackground(Theme.FLAME);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 3),
                new EmptyBorder(Theme.scale(14), Theme.scale(18), Theme.scale(14), Theme.scale(18))));
        setLayout(new BorderLayout(Theme.scale(14), 0));

        JLabel txt = new JLabel(
                "<html><b style='font-size:14px'>BONUS - 1 carta extra dalla riga superiore</b>"
              + "<br><span style='font-size:11px'>Il tuo edificio B020 (Extra Top Pick) ti concede un pick aggiuntivo. "
              + "Clicca una carta top o salta.</span></html>");
        txt.setForeground(Theme.INK);
        txt.setFont(new Font("Serif", Font.PLAIN, Theme.scale(13)));

        skip.setBackground(Theme.INK);
        skip.setForeground(Theme.CREAM);
        skip.setFont(new Font("Serif", Font.BOLD, Theme.scale(13)));
        skip.setBorder(new EmptyBorder(Theme.scale(8), Theme.scale(16), Theme.scale(8), Theme.scale(16)));
        skip.setFocusPainted(false);
        skip.addActionListener(e -> { if (onSkip != null) onSkip.run(); });

        add(txt, BorderLayout.CENTER);
        add(skip, BorderLayout.EAST);

        if (phase != null) phase.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        setVisible(phase != null && phase.getGuiPhase() == GuiPhase.ACTION_EXTRA_PICK_MINE);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
