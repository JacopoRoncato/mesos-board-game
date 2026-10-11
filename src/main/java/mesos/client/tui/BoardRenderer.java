package mesos.client.tui;

import java.util.List;
import mesos.network.server.SnapshotMessage;
import mesos.network.server.SnapshotMessage.PlayerSnapshot;
import mesos.network.server.SnapshotMessage.SlotSnapshot;
import mesos.network.server.SnapshotMessage.TileSnapshot;

/**
 * Renders a SnapshotMessage as ASCII art to stdout.
 */
public class BoardRenderer {

    private static final int SLOT_W = 20;
    private static final String SEP = "=".repeat(64);

    /**
     * Renders the board without player context (no "your turn" banner).
     *
     * @param snap the snapshot to render
     */
    public static void render(SnapshotMessage snap) {
        render(snap, null, false);
    }

    /**
     * Renders the board with player context, treating the player as not yet executed.
     *
     * @param snap the snapshot to render
     * @param nick the local player's nickname (used for the "your turn" banner)
     */
    public static void render(SnapshotMessage snap, String nick) {
        render(snap, nick, false);
    }

    /**
     * Renders the board with full context. When {@code done} is {@code true} the
     * ACTION_RESOLUTION "your turn" banner is suppressed (the player already executed).
     *
     * @param snap the snapshot to render
     * @param nick the local player's nickname
     * @param done {@code true} if the player has already executed this round
     */
    public static void render(SnapshotMessage snap, String nick, boolean done) {
        System.out.println();
        System.out.println(SEP);
        System.out.printf("  Fase: %-18s  Round: %2d   Era: %s%n",
                snap.getPhase(), snap.getRound(), snap.getEra());
        System.out.println(SEP);

        if (nick != null) {
            String myTile = snap.getPlayers().stream()
                    .filter(p -> p.getId().equals(nick))
                    .map(PlayerSnapshot::getSelectedTile)
                    .findFirst().orElse(null);

            if ("PLACEMENT".equals(snap.getPhase()) && "-".equals(myTile)) {
                // My actual turn only if I am the first player still without a tile (sequential order).
                boolean myActualTurn = snap.getPlayers().stream()
                        .filter(p -> "-".equals(p.getSelectedTile()))
                        .findFirst().map(p -> p.getId().equals(nick)).orElse(false);
                if (myActualTurn) {
                    String free = snap.getOfferTrack().stream()
                            .filter(t -> "free".equals(t.getOccupant()))
                            .map(TileSnapshot::getTileId)
                            .collect(java.util.stream.Collectors.joining("  "));
                    System.out.println("  *** IL TUO TURNO — piazza il totem: place <tile>   tessere libere: " + free + " ***");
                    System.out.println();
                }
            } else if ("ACTION_RESOLUTION".equals(snap.getPhase()) && myTile != null && !"-".equals(myTile) && !done) {
                System.out.println("  *** IL TUO TURNO — esegui: exec " + tilePickDetail(myTile)
                        + "   tessera: " + myTile + " ***");
                System.out.println();
            }
        }

        System.out.println("  RIGA TOP:");
        renderRow(snap.getTopRow());

        if ("PLACEMENT".equals(snap.getPhase()) && nick != null) {
            PlayerSnapshot expected = snap.getPlayers().stream()
                    .filter(p -> "-".equals(p.getSelectedTile()))
                    .findFirst().orElse(null);
            if (expected != null) {
                if (expected.getId().equals(nick)) {
                    System.out.println("  >>> È IL TUO TURNO di piazzamento — scegli una tessera libera");
                } else {
                    System.out.println("  Ordine turno: aspetta — sta piazzando " + expected.getId());
                }
            }
        }

        System.out.println();
        System.out.println("  OFFER TRACK:   [tessera  pesca : occupante]");
        renderOfferTrack(snap.getOfferTrack());

        System.out.println();
        System.out.println("  RIGA BOTTOM:");
        renderRow(snap.getBottomRow());

        System.out.println();
        System.out.println("  GIOCATORI:");
        for (PlayerSnapshot p : snap.getPlayers()) {
            String arrow = nick != null && nick.equals(p.getId()) ? "→ " : "  ";
            System.out.printf("  %s%-12s  cibo=%-3d  pp=%-3d  tessera=%s%n",
                    arrow, p.getId(), p.getFood(), p.getPrestigePoints(), p.getSelectedTile());
        }
        System.out.println(SEP);
    }

    private static void renderRow(List<SlotSnapshot> slots) {
        if (slots.isEmpty()) { System.out.println("  (vuota)"); return; }
        StringBuilder sb = new StringBuilder("  ");
        for (SlotSnapshot slot : slots) {
            sb.append("[").append(pad(formatSlot(slot), SLOT_W)).append("]");
        }
        System.out.println(sb);
    }

    private static void renderOfferTrack(List<TileSnapshot> tiles) {
        StringBuilder sb = new StringBuilder("  ");
        for (TileSnapshot tile : tiles) {
            String occ = "free".equals(tile.getOccupant()) ? "----" : tile.getOccupant();
            String picks = tilePickInfo(tile.getTileId());
            sb.append("[").append(tile.getTileId()).append(" ").append(pad(picks, 4))
              .append(":").append(pad(occ, 6)).append("]");
        }
        System.out.println(sb);
    }

    private static String tilePickInfo(String tileId) {
        return switch (tileId) {
            case "A" -> "-";
            case "B" -> "1↓";
            case "C" -> "1↑";
            case "D" -> "2↓";
            case "E" -> "1↑1↓";
            case "F" -> "2↑";
            case "G" -> "2↑1↓";
            default  -> "?";
        };
    }

    private static String tilePickDetail(String tileId) {
        return switch (tileId) {
            case "A" -> "(solo ordine di turno — nessuna carta)";
            case "B" -> "BOTTOM:m  (1 carta da BOTTOM)";
            case "C" -> "TOP:n  (1 carta da TOP)";
            case "D" -> "BOTTOM:m  (2 carte da BOTTOM)";
            case "E" -> "TOP:n BOTTOM:m  (1 da TOP + 1 da BOTTOM)";
            case "F" -> "TOP:n  (2 carte da TOP)";
            case "G" -> "TOP:n BOTTOM:m  (2 da TOP + 1 da BOTTOM)";
            default  -> "[picks]";
        };
    }
    private static String formatSlot(SlotSnapshot slot) {
        if ("EMPTY".equals(slot.getKind())) return "EMPTY";
        String prefix = slot.getKind().substring(0, Math.min(3, slot.getKind().length()));
        String name   = slot.getName() == null ? "" : slot.getName();
        if (name.length() > 14) name = name.substring(0, 14);
        return prefix + " " + name;
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

}
