package mesos.client.ui.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import mesos.client.ui.resource.Theme;

/**
 * Horizontal strip of card slots above or below the offer track. Each
 * slot is a {@link CardComponent}; currently all default to {@code BACK}
 * (face down) because the server's STATE reply doesn't include row
 * contents yet. Setter methods are exposed so a future row sync can
 * populate kinds + labels per slot.
 */
public class RowPanel extends JPanel {

    private final List<CardComponent> slots = new ArrayList<>();
    private final JPanel slotRow;

    /**
     * Constructs a RowPanel.
     *
     * @param title     header label shown above the row
     * @param slotCount initial number of card slots
     */
    public RowPanel(String title, int slotCount) {
        setLayout(new BorderLayout(0, Theme.scale(2)));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.scale(2), Theme.scale(4), Theme.scale(2), Theme.scale(4)));

        JLabel header = new JLabel(title, SwingConstants.LEFT);
        header.setFont(Theme.HEADING);
        header.setForeground(Theme.INK);
        add(header, BorderLayout.NORTH);

        slotRow = new JPanel(new CardRowLayout(Theme.scale(6)));
        slotRow.setOpaque(false);
        for (int i = 0; i < slotCount; i++) {
            CardComponent c = new CardComponent();
            c.setKind(CardComponent.Kind.BACK);
            attachClick(c, i);
            slots.add(c);
            slotRow.add(c);
        }
        add(slotRow, BorderLayout.CENTER);
    }

    /**
     * Adjusts the number of card slots, rebuilding the slot list if the count changed.
     *
     * @param count the new slot count
     */
    public void setSlotCount(int count) {
        if (count == slots.size()) return;
        slotRow.removeAll();
        slots.clear();
        for (int i = 0; i < count; i++) {
            CardComponent c = new CardComponent();
            c.setKind(CardComponent.Kind.BACK);
            attachClick(c, i);
            slots.add(c);
            slotRow.add(c);
        }
        slotRow.revalidate();
        slotRow.repaint();
    }

    private java.util.function.IntConsumer onSlotClick;

    /** Routes a click on any card slot to {@code cb} with the slot index. */
    public void setOnSlotClick(java.util.function.IntConsumer cb) { this.onSlotClick = cb; }

    private void attachClick(CardComponent c, int idx) {
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (onSlotClick != null && c.isPickable()) onSlotClick.accept(idx);
            }
        });
    }

    /**
     * Returns the {@link CardComponent} at the given slot index, or {@code null} if
     * the index is out of range.
     *
     * @param index zero-based slot index
     * @return the card component, or {@code null}
     */
    public CardComponent slot(int index) {
        return (index < 0 || index >= slots.size()) ? null : slots.get(index);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // BGA-style: cards sit on a soft parchment row panel over the warm table.
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0xF5, 0xEC, 0xD6, 205));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.setColor(new Color(0x3A, 0x24, 0x18, 90));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g2.dispose();
    }

    /**
     * Responsive layout for the card slots: makes every card as large as
     * possible while keeping the printed 5:7 aspect ratio, so the row fills
     * the available height (capped by width) and the cards grow/shrink with
     * the window instead of sitting at a fixed size in the top-left corner.
     * The cards are centred as a group.
     */
    private static final class CardRowLayout implements LayoutManager {
        private static final double ASPECT_W_OVER_H = 5.0 / 7.0;
        private final int gap;

        CardRowLayout(int gap) { this.gap = Math.max(0, gap); }

        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}

        @Override public Dimension preferredLayoutSize(Container parent) {
            int n = parent.getComponentCount();
            Insets in = parent.getInsets();
            int h = Theme.scale(210);
            int w = (int) Math.round(h * ASPECT_W_OVER_H);
            int total = n == 0 ? 0 : n * w + (n - 1) * gap;
            return new Dimension(total + in.left + in.right, h + in.top + in.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            return new Dimension(in.left + in.right + Theme.scale(40),
                                 in.top + in.bottom + Theme.scale(56));
        }

        @Override public void layoutContainer(Container parent) {
            int n = parent.getComponentCount();
            if (n == 0) return;
            Insets in = parent.getInsets();
            int availW = Math.max(1, parent.getWidth()  - in.left - in.right);
            int availH = Math.max(1, parent.getHeight() - in.top  - in.bottom);
            // Largest card height so n cards fit BOTH the width and the height.
            int hByWidth = (int) (((availW - (n - 1) * gap) / (double) n) / ASPECT_W_OVER_H);
            int cardH = Math.max(1, Math.min(availH, hByWidth));
            cardH = Math.min(cardH, Theme.scale(250));   // ceiling: don't let cards become oversized
            int cardW = (int) Math.round(cardH * ASPECT_W_OVER_H);
            int totalW = n * cardW + (n - 1) * gap;
            int x = in.left + Math.max(0, (availW - totalW) / 2);
            int y = in.top  + Math.max(0, (availH - cardH) / 2);
            for (int i = 0; i < n; i++) {
                parent.getComponent(i).setBounds(x, y, cardW, cardH);
                x += cardW + gap;
            }
        }
    }
}
