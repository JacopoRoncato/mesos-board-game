package mesos.game;

import mesos.board.RowRefillResult;
import mesos.enums.*;
import mesos.offer.OfferTile;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    private Player alice;
    private Player bob;
    private Game game;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
        bob   = new Player("bob",   new Totem(TotemColor.BLUE));
        game  = new Game(PlayerCount.TWO, List.of(alice, bob));
    }

    // ---- initial state ----

    @Test
    void initialPhase_isSetup() {
        assertEquals(GamePhase.SETUP, game.getPhase());
    }

    @Test
    void initialRound_isOne() {
        assertEquals(1, game.getCurrentRound());
    }

    @Test
    void initialEra_isOne() {
        assertEquals(Era.I, game.getCurrentEra());
    }

    @Test
    void getMaxRounds_returnsTen() {
        assertEquals(10, game.getMaxRounds());
    }

    @Test
    void getPlayers_returnsDefensiveCopy() {
        List<Player> p1 = game.getPlayers();
        List<Player> p2 = game.getPlayers();
        assertNotSame(p1, p2);
        assertEquals(p1.size(), p2.size());
    }

    // ---- startGame / setup ----

    @Test
    void startGame_transitionsToPlacement() {
        game.startGame();
        assertEquals(GamePhase.PLACEMENT, game.getPhase());
    }

    @Test
    void startGame_populatesBoard() {
        game.startGame();
        assertNotNull(game.getBoard());
        assertFalse(game.getBoard().getOfferTrack().getTiles().isEmpty());
    }

    @Test
    void startGame_assignsInitialFoodToAllPlayers() {
        game.startGame();
        // Every player should have received some food
        game.getPlayers().forEach(p -> assertTrue(p.getFood() >= 2));
    }

    @Test
    void startGame_idempotentIfCalledTwice() {
        game.startGame();
        assertEquals(GamePhase.PLACEMENT, game.getPhase());
        game.startGame(); // second call: setup already done, just re-sets PLACEMENT
        assertEquals(GamePhase.PLACEMENT, game.getPhase());
    }

    // ---- placeTotem ----

    @Test
    void placeTotem_validTile_returnsTrue() {
        game.startGame();
        OfferTile tileB = getFirstTile();
        assertTrue(game.placeTotem(alice, tileB));
    }

    @Test
    void placeTotem_tileBecomesBound_toAlice() {
        game.startGame();
        OfferTile tileB = getFirstTile();
        game.placeTotem(alice, tileB);
        assertEquals(alice.getTotem(), tileB.getOccupiedBy());
    }

    @Test
    void placeTotem_aliceSetsSelectedTile() {
        game.startGame();
        OfferTile tileB = getFirstTile();
        game.placeTotem(alice, tileB);
        assertSame(tileB, alice.getSelectedOfferTile());
    }

    @Test
    void placeTotem_nullPlayer_returnsFalse() {
        game.startGame();
        OfferTile tileB = getFirstTile();
        assertFalse(game.placeTotem(null, tileB));
    }

    @Test
    void placeTotem_occupiedTile_returnsFalse() {
        game.startGame();
        OfferTile tileB = getFirstTile();
        game.placeTotem(alice, tileB);
        assertFalse(game.placeTotem(bob, tileB));
    }

    @Test
    void placeTotem_allPlayersPlaced_advancesToActionResolution() {
        game.startGame();
        List<OfferTile> tiles = game.getBoard().getOfferTrack().getTiles();
        game.placeTotem(alice, tiles.get(0));
        game.placeTotem(bob,   tiles.get(1));
        assertEquals(GamePhase.ACTION_RESOLUTION, game.getPhase());
    }

    @Test
    void placeTotem_onlyOnePlayerPlaced_staysInPlacement() {
        game.startGame();
        game.placeTotem(alice, getFirstTile());
        assertEquals(GamePhase.PLACEMENT, game.getPhase());
    }

    // ---- markPlayerDone ----

    @Test
    void markPlayerDone_bothDone_triggersEndOfRound() {
        game.startGame();
        List<OfferTile> tiles = game.getBoard().getOfferTrack().getTiles();
        game.placeTotem(alice, tiles.get(0));
        game.placeTotem(bob,   tiles.get(1));
        // phase = ACTION_RESOLUTION
        game.markPlayerDone(alice);
        assertEquals(GamePhase.ACTION_RESOLUTION, game.getPhase()); // not done yet

        game.markPlayerDone(bob);
        // endOfRound called → resolveNormalRound → round 2 → PLACEMENT
        assertEquals(2, game.getCurrentRound());
        assertEquals(GamePhase.PLACEMENT, game.getPhase());
    }

    @Test
    void markPlayerDone_onlyOnePlayerDone_staysInActionResolution() {
        game.startGame();
        List<OfferTile> tiles = game.getBoard().getOfferTrack().getTiles();
        game.placeTotem(alice, tiles.get(0));
        game.placeTotem(bob,   tiles.get(1));
        game.markPlayerDone(alice);
        assertEquals(GamePhase.ACTION_RESOLUTION, game.getPhase());
    }

    // ---- handleRefillResult + final round via deckExhausted ----

    @Test
    void handleRefillResult_deckExhausted_nextRoundIsGameOver() {
        game.startGame();
        // Force deckExhaustedFlag = true
        game.handleRefillResult(new RowRefillResult(false, null, true));
        List<OfferTile> tiles = game.getBoard().getOfferTrack().getTiles();
        game.placeTotem(alice, tiles.get(0));
        game.placeTotem(bob,   tiles.get(1));
        game.markPlayerDone(alice);
        game.markPlayerDone(bob); // endOfRound → final → GAME_OVER
        assertEquals(GamePhase.GAME_OVER, game.getPhase());
    }

    @Test
    void handleRefillResult_eraTransition_updatesEra() {
        game.startGame();
        game.handleRefillResult(new RowRefillResult(true, Era.II, false));
        assertEquals(Era.II, game.getCurrentEra());
    }

    @Test
    void handleRefillResult_null_noException() {
        game.startGame();
        assertDoesNotThrow(() -> game.handleRefillResult(null));
    }

    // ---- triggerEndGame ----

    @Test
    void triggerEndGame_setsPhaseToGameOver() {
        game.startGame();
        game.triggerEndGame();
        assertEquals(GamePhase.GAME_OVER, game.getPhase());
    }

    // ---- getRanking ----

    @Test
    void getRanking_sortsByPrestigeDescending() {
        alice.addPrestige(10);
        bob.addPrestige(5);
        List<Player> ranking = game.getRanking();
        assertEquals("alice", ranking.get(0).getId());
        assertEquals("bob",   ranking.get(1).getId());
    }

    @Test
    void getRanking_tiebreakByFoodDescending() {
        alice.addPrestige(10);
        alice.gainFood(3);
        bob.addPrestige(10);
        bob.gainFood(7);
        List<Player> ranking = game.getRanking();
        assertEquals("bob",   ranking.get(0).getId()); // more food wins
        assertEquals("alice", ranking.get(1).getId());
    }

    @Test
    void getRanking_doesNotMutateInternalList() {
        List<Player> r1 = game.getRanking();
        List<Player> r2 = game.getRanking();
        assertNotSame(r1, r2);
    }

    // ---- helpers ----

    private OfferTile getFirstTile() {
        return game.getBoard().getOfferTrack().getTiles().get(0);
    }
}
