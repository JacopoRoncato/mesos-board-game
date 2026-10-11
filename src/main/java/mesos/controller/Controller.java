package mesos.controller;

import java.beans.PropertyChangeListener;
import mesos.effects.ExtraTopRowPickEffect;
import mesos.enums.GamePhase;
import mesos.enums.OfferTileId;
import mesos.enums.VisibleRow;
import mesos.game.Game;
import mesos.offer.CardPick;
import mesos.offer.OfferChoice;
import mesos.offer.OfferTile;
import mesos.player.Player;

/**
 * Server-side Controller. Receives textual commands from the (Virtual) View,
 * parses them, and dispatches to the Model. Mirrors Cugola's "Server 2'"
 * variante" (Towards Remote View slides 43-46).
 *
 * Command format:  VERB ARG1 ARG2 ...   one command per line.
 * Reply  format:   OK ... | ERR REASON  one line per reply.
 *
 * Model to View updates do NOT flow through here; they flow through the
 * listener registered via {@link #setListener}.
 */
public class Controller {
    private final Game game;

    /**
     * Creates a controller bound to the given game.
     *
     * @param game the game model this controller manages
     */
    public Controller(Game game) {
        this.game = game;
    }

    /**
     * Forwards the listener down to the Model so state changes propagate
     * to the Virtual View. The VV passes its ModelListener here; the Model fires the
     * PropertyChangeEvents.
     *
     * @param listener the listener to register
     */
    public void setListener(PropertyChangeListener listener) {
        game.setListener(listener);
    }

    /**
     * Parses and dispatches a raw textual command.
     * Returns {@code "OK ..."} on success or {@code "ERR ..."} on failure.
     * Command verbs are case-insensitive; player IDs are case-sensitive.
     *
     * @param raw the raw command string (e.g. {@code "PLACE alice B"})
     * @return the response string
     */
    public String processCmd(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ERR EMPTY";
        }
        String[] parts = raw.trim().split("\\s+");
        String verb = parts[0].toUpperCase();
        try {
            return switch (verb) {
                case "START"       -> cmdStart();
                case "PLACE"       -> cmdPlace(parts);
                case "EXECUTE"     -> cmdExecute(parts);
                case "EXTRA_PICK"  -> cmdExtraPick(parts);
                case "SKIP_EXTRA"  -> cmdSkipExtra(parts);
                default            -> "ERR UNKNOWN_CMD " + verb;
            };
        } catch (RuntimeException e) {
            return "ERR " + verb + " " + e.getMessage();
        }
    }

    // ---- commands ----

    private String cmdStart() {
        game.startGame();
        return "OK START phase=" + game.getPhase();
    }

    /** PLACE &lt;playerId&gt; &lt;tileId&gt; */
    private String cmdPlace(String[] parts) {
        if (game.getPhase() != GamePhase.PLACEMENT)
            return "ERR PLACE wrong_phase=" + game.getPhase();
        if (parts.length < 3) return "ERR PLACE NEEDS playerId tileId";
        Player player = findPlayer(parts[1]);
        if (player == null) return "ERR PLAYER_NOT_FOUND " + parts[1];
        Player expected = game.getCurrentPlacementPlayer();
        if (expected == null || !expected.getId().equals(player.getId()))
            return "ERR PLACE not_your_turn";
        OfferTile tile = findTile(parts[2]);
        if (tile == null) return "ERR TILE_NOT_FOUND " + parts[2];

        if (!game.placeTotem(player, tile)) {
            return "ERR PLACE_FAILED " + parts[2];
        }
        return "OK PLACE " + player.getId() + " -> " + tile.getId();
    }

    /** EXECUTE &lt;playerId&gt; [row:slot ...]   e.g. EXECUTE P1 TOP:0 BOTTOM:2 */
    private String cmdExecute(String[] parts) {
        if (game.getPhase() != GamePhase.ACTION_RESOLUTION)
            return "ERR EXECUTE wrong_phase=" + game.getPhase();
        if (parts.length < 2) return "ERR EXECUTE NEEDS playerId [picks]";
        Player player = findPlayer(parts[1]);
        if (player == null) return "ERR PLAYER_NOT_FOUND " + parts[1];
        Player expected = game.getCurrentExecutionPlayer();
        if (expected == null || !expected.getId().equals(player.getId()))
            return "ERR EXECUTE not_your_turn";
        OfferTile tile = player.getSelectedOfferTile();
        if (tile == null) return "ERR NO_TILE_SELECTED " + player.getId();

        OfferChoice choice = tile.createChoice();
        for (int i = 2; i < parts.length; i++) {
            CardPick pick = parsePick(parts[i]);
            if (pick == null) return "ERR BAD_PICK " + parts[i];
            choice.addPick(pick);
        }
        tile.executeChoice(choice, player, game.getBoard());
        if (player.getTribe().findExtraTopRowPickEffect() == null) {
            game.markPlayerDone(player);
        } else {
            game.setExtraPickPending(player);
        }
        return "OK EXECUTE " + player.getId() + " picks=" + (parts.length - 2);
    }

    /** EXTRA_PICK &lt;playerId&gt; &lt;row:slot&gt; */
    private String cmdExtraPick(String[] parts) {
        if (game.getPhase() != GamePhase.ACTION_RESOLUTION)
            return "ERR EXTRA_PICK wrong_phase=" + game.getPhase();
        if (parts.length < 3) return "ERR EXTRA_PICK NEEDS playerId row:slot";
        Player player = findPlayer(parts[1]);
        if (player == null) return "ERR PLAYER_NOT_FOUND " + parts[1];
        ExtraTopRowPickEffect effect = player.getTribe().findExtraTopRowPickEffect();
        if (effect == null) return "ERR NO_EXTRA_PICK_EFFECT";
        CardPick pick = parsePick(parts[2]);
        if (pick == null) return "ERR BAD_PICK " + parts[2];
        effect.executeTopRowPick(player, game.getBoard(), pick);
        game.markPlayerDone(player);
        return "OK EXTRA_PICK " + player.getId();
    }

    /** SKIP_EXTRA &lt;playerId&gt; */
    private String cmdSkipExtra(String[] parts) {
        if (game.getPhase() != GamePhase.ACTION_RESOLUTION)
            return "ERR SKIP_EXTRA wrong_phase=" + game.getPhase();
        if (parts.length < 2) return "ERR SKIP_EXTRA NEEDS playerId";
        Player player = findPlayer(parts[1]);
        if (player == null) return "ERR PLAYER_NOT_FOUND " + parts[1];
        game.markPlayerDone(player);
        return "OK SKIP_EXTRA " + player.getId();
    }

    // ---- helpers ----

    private Player findPlayer(String id) {
        for (Player p : game.getPlayers()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    private OfferTile findTile(String idStr) {
        OfferTileId target;
        try {
            target = OfferTileId.valueOf(idStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (OfferTile t : game.getBoard().getOfferTrack().getTiles()) {
            if (t.getId() == target) return t;
        }
        return null;
    }

    /** Parses "TOP:3" or "BOTTOM:1" into a CardPick; returns null on bad format. */
    private CardPick parsePick(String s) {
        String[] parts = s.split(":");
        if (parts.length != 2) return null;
        try {
            VisibleRow row = VisibleRow.valueOf(parts[0].toUpperCase());
            int idx = Integer.parseInt(parts[1]);
            return new CardPick(row, idx);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
