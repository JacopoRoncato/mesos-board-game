package mesos.client.ui.panel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.LinearGradientPaint;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import mesos.client.ui.resource.Theme;

/**
 * Lobby step shown to the first player: pick how many players (2-5) with big
 * buttons, on the warm MESOS gradient. Replaces the old JOptionPane dropdown so
 * the whole lobby is one cohesive flow. View-only.
 */
public final class CountPanel extends JPanel {

    /**
     * Constructs a CountPanel.
     *
     * @param onCount called with the chosen player count (2–5) when a button is pressed
     */
    public CountPanel(IntConsumer onCount) {
        setLayout(new GridBagLayout());
        setOpaque(true);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Quanti giocatori?", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, Theme.scale(30)));
        title.setForeground(Theme.CREAM);
        title.setAlignmentX(CENTER_ALIGNMENT);
        col.add(title);
        col.add(Box.createVerticalStrut(Theme.scale(28)));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.scale(18), 0));
        row.setOpaque(false);
        for (int n = 2; n <= 5; n++) {
            row.add(countButton(n, onCount));
        }
        row.setAlignmentX(CENTER_ALIGNMENT);
        col.add(row);

        add(col);
    }

    private static JButton countButton(int n, IntConsumer onCount) {
        JButton b = new JButton(String.valueOf(n));
        b.setFont(new Font("Serif", Font.BOLD, Theme.scale(26)));
        b.setPreferredSize(new Dimension(Theme.scale(72), Theme.scale(86)));
        b.setBackground(Theme.CREAM);
        b.setForeground(Theme.INK);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(Theme.INK, 3));
        b.addActionListener(e -> onCount.accept(n));
        return b;
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
}
