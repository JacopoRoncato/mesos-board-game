package mesos.client.ui.widget;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import mesos.client.ui.resource.Theme;

/**
 * Full-window semi-transparent overlay that shows a single card enlarged.
 * Installed as the root pane's glass pane; dismissed by the × button,
 * by clicking the dark backdrop, or by pressing Escape.
 */
public final class CardZoomOverlay extends JPanel {

    private static final Color BACKDROP = new Color(0, 0, 0, 170);

    /**
     * Shows the overlay over the window that contains {@code source}.
     */
    public static void show(Component source, CardComponent.Kind kind, String label) {
        JRootPane root = SwingUtilities.getRootPane(source);
        if (root == null) return;
        CardZoomOverlay overlay = new CardZoomOverlay(root, kind, label);
        root.setGlassPane(overlay);
        overlay.setVisible(true);
        overlay.requestFocusInWindow();
    }

    private CardZoomOverlay(JRootPane root, CardComponent.Kind kind, String label) {
        setOpaque(false);
        setLayout(null);

        int cardW = Theme.scale(240);
        int cardH = Theme.scale(336);

        CardComponent card = new CardComponent();
        card.setKind(kind);
        card.setLabel(label);
        card.setBounds(0, 0, cardW, cardH);
        add(card);

        JButton close = closeButton();
        close.addActionListener(e -> dismiss());
        add(close);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { reposition(card, close, cardW, cardH); }
            @Override public void componentShown(ComponentEvent e)   { reposition(card, close, cardW, cardH); }
        });

        // Click on backdrop dismisses
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!card.getBounds().contains(e.getPoint())) dismiss();
            }
        });

        // Escape dismisses
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dismiss");
        getActionMap().put("dismiss", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dismiss(); }
        });
    }

    private void reposition(CardComponent card, JButton close, int cardW, int cardH) {
        int cx = (getWidth()  - cardW) / 2;
        int cy = (getHeight() - cardH) / 2;
        card.setBounds(cx, cy, cardW, cardH);
        Dimension btnPref = close.getPreferredSize();
        int bw = btnPref.width, bh = btnPref.height;
        close.setBounds(cx + cardW - bw / 2, cy - bh / 2, bw, bh);
    }

    private void dismiss() {
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(BACKDROP);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private static JButton closeButton() {
        JButton b = new JButton("×");
        b.setFont(new Font("Serif", Font.BOLD, Theme.scale(16)));
        b.setForeground(Theme.CREAM);
        b.setBackground(Theme.INK);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(
                Theme.scale(4), Theme.scale(9), Theme.scale(4), Theme.scale(9)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
