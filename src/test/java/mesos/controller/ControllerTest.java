package mesos.controller;

import mesos.board.Board;
import mesos.board.RowSlot;
import mesos.board.RowRefillResult;
import mesos.enums.*;
import mesos.game.Game;
import mesos.offer.OfferTile;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ControllerTest {

    private Game game;
    private Controller controller;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
        bob   = new Player("bob",   new Totem(TotemColor.BLUE));
        game  = new Game(PlayerCount.TWO, List.of(alice, bob));
        controller = new Controller(game);
    }

    // ---- null / blank ----

    @Test
    void processCmd_null_returnsErrEmpty() {
        assertTrue(controller.processCmd(null).startsWith("ERR EMPTY"));
    }

    @Test
    void processCmd_blank_returnsErrEmpty() {
        assertTrue(controller.processCmd("   ").startsWith("ERR EMPTY"));
    }

    @Test
    void processCmd_emptyString_returnsErrEmpty() {
        assertTrue(controller.processCmd("").startsWith("ERR EMPTY"));
    }

    // ---- START ----

    @Test
    void processCmd_START_returnsOkAndSetsPlacement() {
        String result = controller.processCmd("START");
        assertTrue(result.startsWith("OK"));
        assertEquals(GamePhase.PLACEMENT, game.getPhase());
    }

    @Test
    void processCmd_START_caseInsensitive() {
        String result = controller.processCmd("start");
        assertTrue(result.startsWith("OK"));
    }

    @Test
    void processCmd_START_mixedCase() {
        String result = controller.processCmd("Start");
        assertTrue(result.startsWith("OK"));
    }

    // ---- PLACE ----

    @Test
    void processCmd_PLACE_wrongPhase_returnsErr() {
        // game is in SETUP, not PLACEMENT
        String result = controller.processCmd("PLACE alice B");
        assertTrue(result.startsWith("ERR"), "Expected ERR but got: " + result);
    }

    @Test
    void processCmd_PLACE_missingArgs_returnsErr() {
        controller.processCmd("START");
        String result = controller.processCmd("PLACE alice");
        assertTrue(result.startsWith("ERR"));
    }

    @Test
    void processCmd_PLACE_unknownPlayer_returnsErr() {
        controller.processCmd("START");
        String result = controller.processCmd("PLACE nonexistent B");
        assertTrue(result.startsWith("ERR") && result.contains("PLAYER_NOT_FOUND"));
    }

    @Test
    void processCmd_PLACE_unknownTile_returnsErr() {
        controller.processCmd("START");
        String result = controller.processCmd("PLACE alice ZZNOTEXIST");
        assertTrue(result.startsWith("ERR") && result.contains("TILE_NOT_FOUND"));
    }

    @Test
    void processCmd_PLACE_success_returnsOk() {
        controller.processCmd("START");
        String result = controller.processCmd("PLACE alice B"); // B exists for 2 players
        assertTrue(result.startsWith("OK"), "Expected OK but got: " + result);
    }

    @Test
    void processCmd_PLACE_tileOccupied_returnsErr() {
        controller.processCmd("START");
        controller.processCmd("PLACE alice B");
        // alice now occupies B; bob trying to take B should fail
        String result = controller.processCmd("PLACE bob B");
        assertTrue(result.startsWith("ERR"), "Expected ERR but got: " + result);
    }

    @Test
    void processCmd_PLACE_allPlayersPlaced_advancesPhase() {
        controller.processCmd("START");
        controller.processCmd("PLACE alice B");
        controller.processCmd("PLACE bob C");
        assertEquals(GamePhase.ACTION_RESOLUTION, game.getPhase());
    }

    // ---- EXECUTE ----

    @Test
    void processCmd_EXECUTE_wrongPhase_returnsErr() {
        // game in SETUP, not ACTION_RESOLUTION
        String result = controller.processCmd("EXECUTE alice BOTTOM:0");
        assertTrue(result.startsWith("ERR"));
    }

    @Test
    void processCmd_EXECUTE_unknownPlayer_returnsErr() {
        setupActionResolution();
        String result = controller.processCmd("EXECUTE ghost BOTTOM:0");
        assertTrue(result.startsWith("ERR") && result.contains("PLAYER_NOT_FOUND"));
    }

    @Test
    void processCmd_EXECUTE_badPickFormat_returnsErr() {
        setupActionResolution();
        String result = controller.processCmd("EXECUTE alice NOTAROW:0");
        assertTrue(result.startsWith("ERR"));
    }

    @Test
    void processCmd_EXECUTE_badPickSeparator_returnsErr() {
        setupActionResolution();
        String result = controller.processCmd("EXECUTE alice BOTTOM-0");
        assertTrue(result.startsWith("ERR"));
    }

    @Test
    void processCmd_EXECUTE_aliceOnTileB_pickBottomChar_returnsOk() {
        // Tile B: 0 top, 1 bottom. Bottom row always has characters after setup.
        setupActionResolution();
        String pick = "BOTTOM:" + firstCharSlot(VisibleRow.BOTTOM);
        String result = controller.processCmd("EXECUTE alice " + pick);
        assertTrue(result.startsWith("OK"), "Expected OK but got: " + result);
    }

    @Test
    void processCmd_EXECUTE_invalidChoiceValidation_returnsErr() {
        // Alice is on tile B (1 bottom), trying to pick more cards than budget allows
        setupActionResolution();
        // Exceeds budget of 1 bottom card
        String result = controller.processCmd("EXECUTE alice BOTTOM:0 BOTTOM:1 BOTTOM:2");
        assertTrue(result.startsWith("ERR"), "Expected ERR but got: " + result);
    }

    // ---- SKIP_EXTRA ----

    @Test
    void processCmd_SKIP_EXTRA_wrongPhase_returnsErr() {
        String result = controller.processCmd("SKIP_EXTRA alice");
        assertTrue(result.startsWith("ERR"));
    }

    @Test
    void processCmd_SKIP_EXTRA_unknownPlayer_returnsErr() {
        setupActionResolution();
        String result = controller.processCmd("SKIP_EXTRA nobody");
        assertTrue(result.startsWith("ERR") && result.contains("PLAYER_NOT_FOUND"));
    }

    @Test
    void processCmd_SKIP_EXTRA_missingArgs_returnsErr() {
        setupActionResolution();
        String result = controller.processCmd("SKIP_EXTRA");
        assertTrue(result.startsWith("ERR"));
    }

    @Test
    void processCmd_SKIP_EXTRA_success_returnsOk() {
        setupActionResolution();
        String result = controller.processCmd("SKIP_EXTRA alice");
        assertTrue(result.startsWith("OK"), "Expected OK but got: " + result);
    }

    @Test
    void processCmd_SKIP_EXTRA_bothDone_advancesRound() {
        setupActionResolution();
        controller.processCmd("SKIP_EXTRA alice");
        controller.processCmd("SKIP_EXTRA bob");
        // Both done → endOfRound → round 2
        assertEquals(2, game.getCurrentRound());
    }

    // ---- EXTRA_PICK ----

    @Test
    void processCmd_EXTRA_PICK_wrongPhase_returnsErr() {
        String result = controller.processCmd("EXTRA_PICK alice TOP:0");
        assertTrue(result.startsWith("ERR"));
    }

    @Test
    void processCmd_EXTRA_PICK_unknownPlayer_returnsErr() {
        setupActionResolution();
        String result = controller.processCmd("EXTRA_PICK ghost TOP:0");
        assertTrue(result.startsWith("ERR") && result.contains("PLAYER_NOT_FOUND"));
    }

    @Test
    void processCmd_EXTRA_PICK_noEffect_returnsErr() {
        setupActionResolution();
        // alice has no ExtraTopRowPickEffect building
        String result = controller.processCmd("EXTRA_PICK alice TOP:0");
        assertTrue(result.startsWith("ERR") && result.contains("NO_EXTRA_PICK_EFFECT"));
    }

    @Test
    void processCmd_EXTRA_PICK_missingArgs_returnsErr() {
        setupActionResolution();
        String result = controller.processCmd("EXTRA_PICK alice");
        assertTrue(result.startsWith("ERR"));
    }

    // ---- unknown verb ----

    @Test
    void processCmd_unknownVerb_returnsErrUnknownCmd() {
        String result = controller.processCmd("FOOBAR");
        assertTrue(result.startsWith("ERR") && result.contains("UNKNOWN_CMD"));
    }

    // ---- helpers ----

    /** Sets up game in ACTION_RESOLUTION: alice on tile B, bob on tile C. */
    private void setupActionResolution() {
        controller.processCmd("START");
        controller.processCmd("PLACE alice B"); // B: 0 top, 1 bottom
        controller.processCmd("PLACE bob C");   // C: 1 top, 0 bottom
        assertEquals(GamePhase.ACTION_RESOLUTION, game.getPhase());
    }

    /** Returns the index of the first selectable character in the given row. */
    private int firstCharSlot(VisibleRow row) {
        Board board = game.getBoard();
        Optional<RowSlot> slot = board.getVisibleRow(row).getSlots().stream()
                .filter(s -> s.containsCharacter() && s.isSelectable())
                .findFirst();
        assertTrue(slot.isPresent(), "No selectable character in row " + row);
        return slot.get().getIndex();
    }
}
