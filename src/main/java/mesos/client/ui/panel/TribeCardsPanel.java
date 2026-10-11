package mesos.client.ui.panel;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GameSnapshot;
import mesos.client.ui.resource.Theme;
import mesos.client.ui.widget.CardComponent;
import mesos.client.ui.widget.CardZoomOverlay;

/**
 * REMOVABLE expand module: renders a player's acquired tribe cards as small
 * thumbnails (characters then buildings) via {@link CardComponent}. Built on
 * demand by {@link PlayerPanel} when expanded.
 *
 * <p>Deleting this class + the guarded block in {@code PlayerPanel} leaves the
 * always-visible counts bar fully working.
 */
public final class TribeCardsPanel extends JPanel {

    private static final int COLS = 4;

    public TribeCardsPanel(GameSnapshot.PlayerState player) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(Theme.scale(4), 0, Theme.scale(2), 0));

        List<GameSnapshot.TribeCard> chars = new ArrayList<>();
        List<GameSnapshot.TribeCard> builds = new ArrayList<>();
        if (player != null && player.tribe != null) {
            for (GameSnapshot.TribeCard c : player.tribe) {
                if (c.kind == CardComponent.Kind.BUILDING) builds.add(c);
                else chars.add(c);
            }
        }

        if (chars.isEmpty() && builds.isEmpty()) {
            add(label("Nessuna carta acquisita"));
            return;
        }
        if (!chars.isEmpty()) add(section("Personaggi", chars));
        if (!builds.isEmpty()) {
            if (!chars.isEmpty()) add(Box.createVerticalStrut(Theme.scale(4)));
            add(section("Edifici", builds));
        }
    }

    private JComponent section(String title, List<GameSnapshot.TribeCard> cards) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(LEFT_ALIGNMENT);
        box.add(label(title));

        JPanel grid = new JPanel(new GridLayout(0, COLS, Theme.scale(3), Theme.scale(3)));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        for (GameSnapshot.TribeCard c : cards) {
            CardComponent cc = new CardComponent();
            cc.setKind(c.kind);
            cc.setLabel(c.name);
            cc.setPreferredSize(new Dimension(Theme.scale(44), Theme.scale(62)));
            cc.setMinimumSize(new Dimension(Theme.scale(28), Theme.scale(39)));
            cc.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cc.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    CardZoomOverlay.show(cc, c.kind, c.name);
                }
            });
            grid.add(cc);
        }
        box.add(grid);
        return box;
    }

    private JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Theme.INK_SOFT);
        l.setFont(new Font("Serif", Font.ITALIC, Theme.scale(11)));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }
}
