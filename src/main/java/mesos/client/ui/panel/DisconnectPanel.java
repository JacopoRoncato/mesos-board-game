package mesos.client.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.resource.Theme;

/**
 * Full-screen replacement shown when the GUI enters a non-playable state:
 * another player disconnected and the game is terminated, the local
 * connection was lost, or the server is unreachable. Mode chosen at
 * construction via {@link Mode}.
 */
public final class DisconnectPanel extends JPanel {

    public enum Mode {
        OTHER_DISCONNECTED,   // TRIBU INCOMPLETA — orange ribbon
        LOST_CONNECTION,      // FUOCO SPENTO — coral ribbon
        SERVER_UNREACHABLE    // VILLAGGIO DESERTO — navy ribbon
    }

    /**
     * Constructs a DisconnectPanel.
     *
     * @param mode      controls ribbon colour and button labels
     * @param detail    human-readable reason shown in the body
     * @param primary   called when the primary action button is pressed
     * @param secondary called when the secondary action button is pressed
     */
    public DisconnectPanel(Mode mode, String detail,
                           Runnable primary, Runnable secondary) {
        setOpaque(true);
        setLayout(new BorderLayout(0, 0));
        setBackground(Theme.CREAM);

        JPanel ribbon = ribbon(mode);
        add(ribbon, BorderLayout.NORTH);

        JLabel body = new JLabel("<html><div style='padding:32px;font-size:14px;color:#0F0807'>"
                                 + detail + "</div></html>");
        add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.scale(12), Theme.scale(24)));
        actions.setOpaque(false);
        actions.add(secondaryBtn(secondaryLabel(mode), secondary));
        if (mode == Mode.LOST_CONNECTION)
            actions.add(primaryBtn(primaryLabel(mode), primary));
        add(actions, BorderLayout.SOUTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, Theme.SKY_TOP, 0, getHeight(), Theme.SKY_MID));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    private JPanel ribbon(Mode mode) {
        JPanel p = new JPanel(new BorderLayout(Theme.scale(12), 0));
        p.setOpaque(true);
        p.setBackground(switch (mode) {
            case OTHER_DISCONNECTED -> Theme.ORANGE;
            case LOST_CONNECTION    -> Theme.CORAL;
            case SERVER_UNREACHABLE -> Theme.NIGHT_MID;
        });
        p.setBorder(new EmptyBorder(Theme.scale(18), Theme.scale(22), Theme.scale(18), Theme.scale(22)));
        JLabel title = new JLabel(titleFor(mode));
        title.setFont(new Font("Serif", Font.BOLD, Theme.scale(22)));
        title.setForeground(Theme.CREAM);
        p.add(title, BorderLayout.CENTER);
        return p;
    }

    private static String titleFor(Mode m) {
        return switch (m) {
            case OTHER_DISCONNECTED -> "TRIBU INCOMPLETA";
            case LOST_CONNECTION    -> "FUOCO SPENTO";
            case SERVER_UNREACHABLE -> "VILLAGGIO DESERTO";
        };
    }
    private static String primaryLabel(Mode m) {
        return switch (m) {
            case OTHER_DISCONNECTED -> "Classifica";
            case LOST_CONNECTION    -> "Riconnetti";
            case SERVER_UNREACHABLE -> "Ritenta";
        };
    }
    private static String secondaryLabel(Mode m) {
        return switch (m) {
            case OTHER_DISCONNECTED -> "Esci";
            case LOST_CONNECTION    -> "Esci";
            case SERVER_UNREACHABLE -> "Esci";
        };
    }

    private static JButton primaryBtn(String s, Runnable r) {
        JButton b = new JButton(s);
        b.setBackground(Theme.INK); b.setForeground(Theme.CREAM);
        b.setFont(new Font("Serif", Font.BOLD, Theme.scale(13)));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 2),
                new EmptyBorder(Theme.scale(10), Theme.scale(18), Theme.scale(10), Theme.scale(18))));
        b.setFocusPainted(false);
        b.addActionListener(e -> { if (r != null) r.run(); });
        return b;
    }
    private static JButton secondaryBtn(String s, Runnable r) {
        JButton b = primaryBtn(s, r);
        b.setBackground(Theme.CREAM); b.setForeground(Theme.INK);
        return b;
    }
}
