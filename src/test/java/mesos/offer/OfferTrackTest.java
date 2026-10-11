package mesos.offer;

import mesos.enums.OfferTileId;
import mesos.enums.PlayerCount;
import mesos.enums.TotemColor;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfferTrackTest {

    private OfferTrack track;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        track = new OfferTrack();
        alice = new Player("alice", new Totem(TotemColor.RED));
        bob   = new Player("bob",   new Totem(TotemColor.BLUE));
    }

    // ---- configureForPlayerCount ----

    @Test
    void configureForPlayerCount_TWO_hasFourTiles() {
        track.configureForPlayerCount(PlayerCount.TWO);
        // TWO: tiles B, C, D (A=FIVE, E=THREE+, F=FOUR+, G=FIVE)
        assertEquals(4, track.getTiles().size());
    }

    @Test
    void configureForPlayerCount_THREE_hasFiveTiles() {
        track.configureForPlayerCount(PlayerCount.THREE);
        // THREE: B, C, D, E
        assertEquals(5, track.getTiles().size());
    }

    @Test
    void configureForPlayerCount_FOUR_hasSixTiles() {
        track.configureForPlayerCount(PlayerCount.FOUR);
        // FOUR: B, C, D, E, F
        assertEquals(6, track.getTiles().size());
    }

    @Test
    void configureForPlayerCount_FIVE_hasSevenTiles() {
        track.configureForPlayerCount(PlayerCount.FIVE);
        // FIVE: A, B, C, D, E, F, G
        assertEquals(7, track.getTiles().size());
    }

    @Test
    void configureForPlayerCount_TWO_containsTileB() {
        track.configureForPlayerCount(PlayerCount.TWO);
        assertTrue(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.B));
    }

    @Test
    void configureForPlayerCount_TWO_doesNotContainTileA() {
        track.configureForPlayerCount(PlayerCount.TWO);
        assertFalse(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.A));
    }

    @Test
    void configureForPlayerCount_TWO_doesNotContainTileD() {
        track.configureForPlayerCount(PlayerCount.TWO);
        assertFalse(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.D));
    }

    @Test
    void configureForPlayerCount_TWO_containsTileE() {
        track.configureForPlayerCount(PlayerCount.TWO);
        assertTrue(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.E));
    }

    @Test
    void configureForPlayerCount_TWO_containsTileF() {
        track.configureForPlayerCount(PlayerCount.TWO);
        assertTrue(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.F));
    }

    @Test
    void configureForPlayerCount_THREE_containsTileD() {
        track.configureForPlayerCount(PlayerCount.THREE);
        assertTrue(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.D));
    }

    @Test
    void configureForPlayerCount_THREE_doesNotContainTileG() {
        track.configureForPlayerCount(PlayerCount.THREE);
        assertFalse(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.G));
    }

    @Test
    void configureForPlayerCount_FOUR_containsTileG() {
        track.configureForPlayerCount(PlayerCount.FOUR);
        assertTrue(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.G));
    }

    @Test
    void configureForPlayerCount_FIVE_containsTileA() {
        track.configureForPlayerCount(PlayerCount.FIVE);
        assertTrue(track.getTiles().stream().anyMatch(t -> t.getId() == OfferTileId.A));
    }

    @Test
    void configureForPlayerCount_tileB_hasCorrectBudget() {
        track.configureForPlayerCount(PlayerCount.TWO);
        OfferTile tileB = track.getTiles().stream()
                .filter(t -> t.getId() == OfferTileId.B).findFirst().orElseThrow();
        assertEquals(0, tileB.getCardsFromTop());
        assertEquals(1, tileB.getCardsFromBottom());
    }

    @Test
    void configureForPlayerCount_tileC_hasCorrectBudget() {
        track.configureForPlayerCount(PlayerCount.TWO);
        OfferTile tileC = track.getTiles().stream()
                .filter(t -> t.getId() == OfferTileId.C).findFirst().orElseThrow();
        assertEquals(1, tileC.getCardsFromTop());
        assertEquals(0, tileC.getCardsFromBottom());
    }

    // ---- placeTotem ----

    @Test
    void placeTotem_freeTile_returnsTrue() {
        track.configureForPlayerCount(PlayerCount.TWO);
        OfferTile tileB = track.getTiles().get(0);
        assertTrue(track.placeTotem(alice, tileB));
    }

    @Test
    void placeTotem_tileBecomesOccupied() {
        track.configureForPlayerCount(PlayerCount.TWO);
        OfferTile tileB = track.getTiles().get(0);
        track.placeTotem(alice, tileB);
        assertFalse(tileB.isAvailable());
        assertEquals(alice.getTotem(), tileB.getOccupiedBy());
    }

    @Test
    void placeTotem_occupiedTile_returnsFalse() {
        track.configureForPlayerCount(PlayerCount.TWO);
        OfferTile tileB = track.getTiles().get(0);
        track.placeTotem(alice, tileB);
        assertFalse(track.placeTotem(bob, tileB));
    }

    @Test
    void placeTotem_nullPlayer_returnsFalse() {
        track.configureForPlayerCount(PlayerCount.TWO);
        OfferTile tileB = track.getTiles().get(0);
        assertFalse(track.placeTotem(null, tileB));
    }

    @Test
    void placeTotem_nullTile_returnsFalse() {
        track.configureForPlayerCount(PlayerCount.TWO);
        assertFalse(track.placeTotem(alice, null));
    }

    @Test
    void placeTotem_tileNotOnTrack_returnsFalse() {
        track.configureForPlayerCount(PlayerCount.TWO);
        OfferTile externalTile = new OfferTile(OfferTileId.A); // not in TWO track
        assertFalse(track.placeTotem(alice, externalTile));
    }

    @Test
    void placeTotem_movesPreviousTotem_clearsPreviousTile() {
        track.configureForPlayerCount(PlayerCount.TWO);
        List<OfferTile> tiles = track.getTiles();
        OfferTile tileB = tiles.get(0);
        OfferTile tileC = tiles.get(1);
        track.placeTotem(alice, tileB);
        track.placeTotem(alice, tileC); // moves alice from B to C
        assertTrue(tileB.isAvailable());   // B is now free
        assertFalse(tileC.isAvailable());  // C is occupied
    }

    // ---- getTilesInResolutionOrder ----

    @Test
    void getTilesInResolutionOrder_sortedByTileId() {
        track.configureForPlayerCount(PlayerCount.FIVE); // all 7 tiles
        List<OfferTile> ordered = track.getTilesInResolutionOrder();
        for (int i = 0; i < ordered.size() - 1; i++) {
            assertTrue(ordered.get(i).getId().compareTo(ordered.get(i + 1).getId()) < 0);
        }
    }

    @Test
    void getTilesInResolutionOrder_firstTileIsA_forFivePlayers() {
        track.configureForPlayerCount(PlayerCount.FIVE);
        assertEquals(OfferTileId.A, track.getTilesInResolutionOrder().get(0).getId());
    }
}
