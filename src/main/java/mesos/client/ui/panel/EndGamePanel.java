package mesos.client.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GameSnapshot;
import mesos.client.ui.resource.Theme;
import mesos.db.ScoreResult;

/**
 * Final-screen ranking panel shown after GAME_OVER. Reads the players
 * from GameSnapshot and renders a 2x2 grid of rank tiles ordered by
 * prestige descending. Backdrop is a dramatic gradient
 * NIGHT_DEEP -> BRICK with the box-cover dancers strip pasted at the
 * bottom, evoking the box art.
 */
// Final screen with ranking/leaderboard.
public final class EndGamePanel extends JPanel {

    private final GameSnapshot snap;
    private final Runnable onExit;

    private BufferedImage logo, sfondo;
    private final JPanel rankingGrid = new JPanel(new GridLayout(0, 2, Theme.scale(22), Theme.scale(22)));
    private final JPanel dbSection   = new JPanel();

    public EndGamePanel(GameSnapshot snap, Runnable onExit) {
        this.snap = snap;
        this.onExit = onExit;

        setOpaque(true);
        setBackground(Theme.NIGHT_DEEP);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(Theme.scale(28), Theme.scale(28), Theme.scale(28), Theme.scale(28)));

        rankingGrid.setOpaque(false);
        dbSection.setOpaque(false);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(rankingGrid, BorderLayout.NORTH);
        center.add(dbSection,   BorderLayout.CENTER);

        add(subtitleStrip(), BorderLayout.NORTH);
        add(center,         BorderLayout.CENTER);
        add(buttonRow(),    BorderLayout.SOUTH);

        if (snap != null) snap.addListener(this::refresh);
        refresh();
    }

    private JPanel subtitleStrip() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(Theme.scale(150), 0, Theme.scale(8), 0));
        JLabel sub = label(13, Theme.FLAME, Font.BOLD);
        sub.setText("PARTITA CONCLUSA");
        sub.setHorizontalAlignment(JLabel.CENTER);
        p.add(sub, BorderLayout.CENTER);
        return p;
    }

    private JPanel buttonRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.scale(16), 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(Theme.scale(20), 0, 0, 0));
        JButton exit = secondaryBtn("Esci");
        exit.addActionListener(e -> { if (onExit != null) onExit.run(); });
        p.add(exit);
        return p;
    }

    private void refresh() {
        rankingGrid.removeAll();
        if (snap != null) {
            List<GameSnapshot.PlayerState> sorted = snap.getPlayers().stream()
                    .sorted(Comparator.comparingInt((GameSnapshot.PlayerState p) -> -p.prestige))
                    .toList();
            int rank = 1;
            for (GameSnapshot.PlayerState p : sorted) {
                rankingGrid.add(rankTile(rank++, p));
            }
        }
        revalidate(); repaint();
    }

    /** Shows the historical DB leaderboard below the per-game ranking grid. */
    public void setDbRanking(List<ScoreResult> ranking) {
        // Must run on the EDT — GuiClient always calls this from a button action
        // (already on EDT), so no invokeLater wrapper is needed. Using invokeLater
        // would cause the card switch (CARD_END) to paint BEFORE the rows are added,
        // leaving a transparent gap for one frame that never repaints on some systems.
        dbSection.removeAll();
        dbSection.setLayout(new BorderLayout(0, 8));

        JLabel title = label(13, Theme.FLAME, Font.BOLD);
        title.setText("CLASSIFICA STORICA (" + ranking.get(0).numPlayers() + " giocatori)");
        title.setHorizontalAlignment(JLabel.CENTER);

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(true);
        rows.setBackground(Theme.NIGHT_DEEP);
        int pos = 1;
        for (ScoreResult r : ranking) rows.add(dbRow(pos++, r));

        JScrollPane scroll = new JScrollPane(rows,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);

        dbSection.add(title,  BorderLayout.NORTH);
        dbSection.add(scroll, BorderLayout.CENTER);
        dbSection.revalidate();
        dbSection.repaint();
    }

    private JPanel dbRow(int pos, ScoreResult r) {
        boolean isFirst = pos == 1;
        Color bg   = isFirst ? Theme.FLAME : new Color(248, 241, 228);
        Color text = Theme.INK;
        JPanel row = new JPanel(new BorderLayout(Theme.scale(8), 0));
        row.setOpaque(true);
        row.setBackground(bg);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 2),
                new EmptyBorder(Theme.scale(6), Theme.scale(12), Theme.scale(6), Theme.scale(12))));
        JLabel posLbl = label(15, text, Font.BOLD);
        posLbl.setText(roman(pos));
        posLbl.setPreferredSize(new Dimension(Theme.scale(40), Theme.scale(24)));
        JLabel nick = label(15, text, Font.PLAIN);
        nick.setText(r.nickname());
        JLabel score = label(15, text, Font.BOLD);
        score.setText(r.totalScore() + " PP  ×" + r.gamesPlayed());
        score.setPreferredSize(new Dimension(Theme.scale(140), Theme.scale(24)));
        score.setHorizontalAlignment(JLabel.RIGHT);
        row.add(posLbl, BorderLayout.WEST);
        row.add(nick,   BorderLayout.CENTER);
        row.add(score,  BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.scale(44)));
        return row;
    }

    private JPanel rankTile(int rank, GameSnapshot.PlayerState p) {
        JPanel tile = new JPanel(new BorderLayout(0, 0));
        tile.setOpaque(true);
        tile.setBackground(Theme.CREAM);
        tile.setBorder(BorderFactory.createLineBorder(Theme.INK, 4));

        JLabel medal = label(40, Theme.FLAME, Font.BOLD);
        medal.setText(roman(rank));
        medal.setHorizontalAlignment(JLabel.CENTER);
        medal.setOpaque(true);
        medal.setBackground(Theme.INK);
        medal.setPreferredSize(new Dimension(Theme.scale(80), 0));

        JLabel name = label(20, Theme.INK, Font.BOLD);
        name.setText(" " + p.id.toUpperCase());

        JLabel pp = label(36, Theme.INK, Font.BOLD);
        pp.setText(String.valueOf(p.prestige));
        pp.setHorizontalAlignment(JLabel.CENTER);
        JLabel ppLbl = label(10, Theme.INK, Font.BOLD);
        ppLbl.setText("PRESTIGIO");
        ppLbl.setHorizontalAlignment(JLabel.CENTER);

        JPanel ppBox = new JPanel(new BorderLayout());
        ppBox.setOpaque(true); ppBox.setBackground(Theme.FLAME);
        ppBox.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.INK));
        ppBox.setPreferredSize(new Dimension(Theme.scale(120), 0));
        ppBox.add(pp, BorderLayout.CENTER);
        ppBox.add(ppLbl, BorderLayout.SOUTH);

        tile.add(medal, BorderLayout.WEST);
        tile.add(name, BorderLayout.CENTER);
        tile.add(ppBox, BorderLayout.EAST);
        tile.setPreferredSize(new Dimension(0, Theme.scale(90)));
        return tile;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setColor(Theme.NIGHT_DEEP);
        g2.fillRect(0, 0, getWidth(), getHeight());
        BufferedImage bg = loadSfondo();
        if (bg != null) {
            double imgRatio   = (double) bg.getWidth() / bg.getHeight();
            double panelRatio = (double) getWidth()    / getHeight();
            int drawW, drawH, drawX, drawY;
            if (imgRatio > panelRatio) {
                drawH = getHeight();
                drawW = (int) (drawH * imgRatio);
                drawX = (getWidth() - drawW) / 2;
                drawY = 0;
            } else {
                drawW = getWidth();
                drawH = (int) (drawW / imgRatio);
                drawX = 0;
                drawY = (getHeight() - drawH) / 2;
            }
            g2.drawImage(bg, drawX, drawY, drawW, drawH, null);
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        BufferedImage lg = loadLogo();
        if (lg != null) {
            int inset = Theme.scale(12);
            int maxW = Math.min(getWidth() - 2 * inset, Theme.scale(480));
            int lgH  = (int)(maxW * ((double)lg.getHeight() / lg.getWidth()));
            int lgX  = (getWidth() - maxW) / 2;
            int lgY  = inset;
            g2.drawImage(lg, lgX, lgY, maxW, lgH, null);
        }
        g2.dispose();
    }

    private static String roman(int n) {
        return String.valueOf(n);
    }

    private synchronized BufferedImage loadLogo() {
        if (logo == null) logo = loadResource("/img/box/logo-mesos.png");
        return logo;
    }
    private synchronized BufferedImage loadSfondo() {
        if (sfondo == null) sfondo = loadResource("/img/box/sfondo.png");
        return sfondo;
    }
    private static BufferedImage loadResource(String path) {
        try (var in = EndGamePanel.class.getResourceAsStream(path)) {
            return (in != null) ? ImageIO.read(in) : null;
        } catch (IOException e) { return null; }
    }

    private static JLabel label(int size, Color c, int style) {
        JLabel l = new JLabel("");
        l.setForeground(c);
        l.setFont(new Font("Serif", style, Theme.scale(size)));
        return l;
    }
    private static JButton primaryBtn(String s) {
        JButton b = new JButton(s);
        b.setBackground(Theme.FLAME); b.setForeground(Theme.INK);
        b.setFont(new Font("Serif", Font.BOLD, Theme.scale(14)));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 3),
                new EmptyBorder(Theme.scale(10), Theme.scale(22), Theme.scale(10), Theme.scale(22))));
        b.setFocusPainted(false);
        return b;
    }
    private static JButton secondaryBtn(String s) {
        JButton b = primaryBtn(s); b.setBackground(Theme.CREAM); return b;
    }
}
