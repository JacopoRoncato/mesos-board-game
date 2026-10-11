package mesos.client.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import mesos.client.ui.model.GuiPhase;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.resource.Theme;

/**
 * Bottom strip that tells the player what to do right now. Reads the
 * current state from {@link PhaseController} and re-renders title +
 * hint + main button + meta chip on every phase change.
 */
public final class ActionPanel extends JPanel {

    private final PhaseController phase;
    private final Consumer<String> verbSender;          // verb-style commands ("START", etc.)
    private final Runnable onSendStart;
    private final Runnable onSkipExtra;
    private final Runnable onQuit;

    private final JLabel title = serif(19, Theme.INK, Font.BOLD);
    private final JLabel hint  = serif(12, Theme.INK_SOFT, Font.PLAIN);
    private final JButton primary = primaryBtn("");
    private final JButton skip = secondaryBtn("Salta");
    private final JButton quit = quitBtn("Esci");
    private final JLabel meta = monoChip("");

    /**
     * Constructs an ActionPanel wired to the given phase controller and action callbacks.
     *
     * @param phase       the phase controller driving label/button visibility
     * @param verbSender  sends a verb-style command string (e.g. "EXECUTE alice TOP:0")
     * @param onSendStart called when the player presses "Inizia partita"
     * @param onSkipExtra called when the player presses "Salta" during ACTION_EXTRA_PICK_MINE
     * @param onQuit      called when the player presses "Esci"
     */
    public ActionPanel(PhaseController phase,
                       Consumer<String> verbSender,
                       Runnable onSendStart,
                       Runnable onSkipExtra,
                       Runnable onQuit) {
        this.phase = phase;
        this.verbSender = verbSender;
        this.onSendStart = onSendStart;
        this.onSkipExtra = onSkipExtra;
        this.onQuit = onQuit;

        setLayout(new BorderLayout(Theme.scale(20), 0));
        setBorder(new EmptyBorder(Theme.scale(16), Theme.scale(22), Theme.scale(16), Theme.scale(22)));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        text.add(title); text.add(hint);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.scale(8), 0));
        right.setOpaque(false);
        right.add(quit);
        right.add(skip);
        right.add(primary);
        right.add(meta);

        add(text, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        primary.addActionListener(e -> onPrimary());
        skip.addActionListener(e -> { if (onSkipExtra != null) onSkipExtra.run(); });
        quit.addActionListener(e -> { if (onQuit != null) onQuit.run(); });

        if (phase != null) phase.addListener(this::refresh);
        refresh();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Color top, bottom;
        GuiPhase p = (phase != null) ? phase.getGuiPhase() : null;
        if (p == GuiPhase.ACTION_MINE || p == GuiPhase.ACTION_EXTRA_PICK_MINE) {
            top = Theme.CORAL; bottom = Theme.BRICK;
        } else if (p == GuiPhase.GAME_OVER) {
            top = Theme.FLAME; bottom = Theme.FLAME_DEEP;
        } else {
            top = Theme.ORANGE; bottom = Theme.CORAL;
        }
        g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Theme.INK);
        g2.fillRect(0, 0, getWidth(), 4);                // top border 4 px
        g2.dispose();
    }

    private void onPrimary() {
        if (phase == null) return;
        switch (phase.getGuiPhase()) {
            case READY_TO_START -> { if (onSendStart != null) onSendStart.run(); }
            case ACTION_MINE -> {
                // Guard: prevent sending EXECUTE with fewer picks than the tile requires.
                if (!phase.picksComplete()) return;
                String args = phase.execArgs();
                verbSender.accept("EXECUTE " + phase.getMyNickname() + (args.isBlank() ? "" : " " + args));
            }
            default              -> { /* nothing — main action is via tiles/cards */ }
        }
    }

    private void refresh() {
        GuiPhase p = (phase != null) ? phase.getGuiPhase() : null;
        if (p == null) {
            title.setText("");
            hint.setText("");
            primary.setVisible(false);
            skip.setVisible(false);
            meta.setText("");
            repaint();
            return;
        }
        switch (p) {
            case LOBBY_PICKING_COLOR -> {
                title.setText("SCEGLI IL TUO TOTEM");
                hint.setText("Clicca uno dei 5 colori sopra.");
                primary.setVisible(false); skip.setVisible(false);
            }
            case LOBBY_WAITING_OTHERS -> {
                title.setText("ASPETTANDO GLI ALTRI GIOCATORI");
                hint.setText("Hai scelto il colore. Aspetta che tutti i giocatori scelgano il loro.");
                primary.setVisible(false); skip.setVisible(false);
            }
            case READY_TO_START -> {
                title.setText("PRONTI PER INIZIARE");
                hint.setText("Lobby al completo. Clicca per avviare la partita.");
                primary.setVisible(true); primary.setText("Inizia partita");
                skip.setVisible(false);
            }
            case PLACEMENT_MINE -> {
                title.setText("PIAZZA IL TUO TOTEM!");
                hint.setText("Clicca una tessera libera dell'Offer Track. I numeri ▲/▼ indicano i picks futuri.");
                primary.setVisible(false); skip.setVisible(false);
            }
            case PLACEMENT_WAITING -> {
                title.setText("ALTRI GIOCATORI STANNO PIAZZANDO");
                hint.setText("Hai gia' piazzato il tuo totem. Aspetta che gli altri scelgano la loro tessera.");
                primary.setVisible(false); skip.setVisible(false);
            }
            case ACTION_MINE -> {
                title.setText("SCEGLI LE TUE CARTE");
                hint.setText("Clicca le carte col bordo dorato, poi premi Esegui. Picks restanti: top "
                           + phase.getRemainingPicks().top() + " - bottom "
                           + phase.getRemainingPicks().bottom() + ".");
                primary.setVisible(true); primary.setText("Esegui");
                // Disabled until all required picks are made; re-enabled by PhaseController listener.
                primary.setEnabled(phase.picksComplete());
                skip.setVisible(false);
            }
            case ACTION_EXTRA_PICK_MINE -> {
                title.setText("BONUS - 1 CARTA EXTRA DAL TOP");
                hint.setText("L'edificio B020 ti da' un pick aggiuntivo. Clicca una carta top, oppure salta.");
                primary.setVisible(false); skip.setVisible(true);
            }
            case ACTION_DONE_WAITING -> {
                title.setText("ALTRI GIOCATORI STANNO ESEGUENDO");
                hint.setText("Hai completato i picks. Aspetta che gli altri finiscano.");
                primary.setVisible(false); skip.setVisible(false);
            }
            case FINAL_SCORING -> {
                title.setText("PUNTEGGI FINALI");
                hint.setText("Il server sta calcolando il bonus di fine partita.");
                primary.setVisible(false); skip.setVisible(false);
            }
            case GAME_OVER -> {
                title.setText("PARTITA CONCLUSA");
                hint.setText("Vedi la classifica nella schermata principale.");
                primary.setVisible(false); skip.setVisible(false);
            }
        }
        meta.setText(" PhaseController = " + p.name() + " ");
        repaint();
    }

    private static JLabel serif(int size, Color colour, int style) {
        JLabel l = new JLabel("");
        l.setForeground(colour);
        l.setFont(new Font("Serif", style, Theme.scale(size)));
        return l;
    }

    private static JButton primaryBtn(String label) {
        JButton b = new JButton(label);
        b.setBackground(Theme.FLAME);
        b.setForeground(Theme.INK);
        b.setFont(new Font("Serif", Font.BOLD, Theme.scale(14)));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 3),
                new EmptyBorder(Theme.scale(8), Theme.scale(22), Theme.scale(8), Theme.scale(22))));
        b.setFocusPainted(false);
        return b;
    }

    private static JButton secondaryBtn(String label) {
        JButton b = primaryBtn(label);
        b.setBackground(Theme.INK);
        b.setForeground(Theme.CREAM);
        return b;
    }

    private static JButton quitBtn(String label) {
        JButton b = primaryBtn(label);
        b.setBackground(new Color(0x7A, 0x1F, 0x1F));
        b.setForeground(Theme.CREAM);
        return b;
    }

    private static JLabel monoChip(String s) {
        JLabel l = new JLabel(s);
        l.setOpaque(true);
        l.setBackground(Theme.CREAM);
        l.setForeground(Theme.INK);
        l.setFont(new Font("Monospaced", Font.PLAIN, Theme.scale(11)));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 2),
                new EmptyBorder(Theme.scale(4), Theme.scale(10), Theme.scale(4), Theme.scale(10))));
        return l;
    }
}
