package mesos.order;

import mesos.enums.PlayerCount;
import mesos.enums.TotemColor;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TurnOrderTileTest {

    private TurnOrderTile tile;
    private Player alice;
    private Player bob;
    private Totem redTotem;
    private Totem blueTotem;

    @BeforeEach
    void setUp() {
        tile = new TurnOrderTile();
        redTotem  = new Totem(TotemColor.RED);
        blueTotem = new Totem(TotemColor.BLUE);
        alice = new Player("alice", redTotem);
        bob   = new Player("bob",   blueTotem);
    }

    // ---- configureForPlayerCount ----

    @Test
    void configure_TWO_createsTwoSpaces() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        assertEquals(2, tile.getSpaces().size());
    }

    @Test
    void configure_THREE_createsThreeSpaces() {
        tile.configureForPlayerCount(PlayerCount.THREE);
        assertEquals(3, tile.getSpaces().size());
    }

    @Test
    void configure_FOUR_createsFourSpaces() {
        tile.configureForPlayerCount(PlayerCount.FOUR);
        assertEquals(4, tile.getSpaces().size());
    }

    @Test
    void configure_FIVE_createsFiveSpaces() {
        tile.configureForPlayerCount(PlayerCount.FIVE);
        assertEquals(5, tile.getSpaces().size());
    }

    @Test
    void configure_lastSpaceIsPenaltySpace() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        List<TurnOrderSpace> spaces = tile.getSpaces();
        assertTrue(spaces.get(spaces.size() - 1).isLastSpace());
    }

    @Test
    void configure_firstSpaceIsNotPenaltySpace() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        assertFalse(tile.getSpaces().get(0).isLastSpace());
    }

    @Test
    void configure_TWO_firstSpaceHasFoodBonus() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        assertEquals(1, tile.getSpaces().get(0).getFoodBonus());
    }

    // ---- addTotem / determineNewOrder ----

    @Test
    void addTotem_returnsNonNullSpace() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        TurnOrderSpace space = tile.addTotem(redTotem, alice);
        assertNotNull(space);
    }

    @Test
    void addTotem_spaceBecomesOccupied() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        tile.addTotem(redTotem, alice);
        assertFalse(tile.getSpaces().get(0).isFree());
    }

    @Test
    void determineNewOrder_twoPlayers_preservesInsertionOrder() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        tile.addTotem(redTotem, alice); // space 0
        tile.addTotem(blueTotem, bob);  // space 1
        List<Player> order = tile.determineNewOrder();
        assertEquals(2, order.size());
        assertEquals("alice", order.get(0).getId());
        assertEquals("bob",   order.get(1).getId());
    }

    @Test
    void determineNewOrder_emptyTile_returnsEmptyList() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        assertTrue(tile.determineNewOrder().isEmpty());
    }

    @Test
    void getFirstFreeSpace_allFree_returnsFirstSpace() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        TurnOrderSpace first = tile.getFirstFreeSpace();
        assertNotNull(first);
        assertEquals(0, first.getPosition());
    }

    @Test
    void getFirstFreeSpace_allOccupied_returnsNull() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        tile.addTotem(redTotem, alice);
        tile.addTotem(blueTotem, bob);
        assertNull(tile.getFirstFreeSpace());
    }

    // ---- applySpaceEffect ----

    @Test
    void applySpaceEffect_bonusSpace_givesFood() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        TurnOrderSpace bonusSpace = tile.getSpaces().get(0); // foodBonus=1, !lastSpace
        tile.applySpaceEffect(bonusSpace, alice);
        assertEquals(1, alice.getFood());
    }

    @Test
    void applySpaceEffect_lastSpaceWithFood_paysOneFood() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        alice.gainFood(3);
        TurnOrderSpace lastSpace = tile.getSpaces().get(1); // lastSpace
        tile.applySpaceEffect(lastSpace, alice);
        assertEquals(2, alice.getFood()); // 3-1=2, no prestige penalty
        assertEquals(0, alice.getPrestigePoints()); // no penalty
    }

    @Test
    void applySpaceEffect_lastSpaceWithoutFood_removesPrestige() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        TurnOrderSpace lastSpace = tile.getSpaces().get(1); // lastSpace, foodBonus=0
        // alice has 0 food → payFood(1) fails → removePrestige(2)
        tile.applySpaceEffect(lastSpace, alice);
        assertEquals(-2, alice.getPrestigePoints());
    }

    @Test
    void applySpaceEffect_nullSpace_noEffect() {
        tile.applySpaceEffect(null, alice);
        assertEquals(0, alice.getFood());
    }

    @Test
    void applySpaceEffect_nullPlayer_noException() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        TurnOrderSpace space = tile.getSpaces().get(0);
        assertDoesNotThrow(() -> tile.applySpaceEffect(space, null));
    }

    // ---- clearTotem ----

    @Test
    void clearTotem_existingTotem_returnsTrueAndFrees() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        tile.addTotem(redTotem, alice);
        assertTrue(tile.clearTotem(redTotem));
        assertTrue(tile.getSpaces().get(0).isFree());
    }

    @Test
    void clearTotem_nonExistingTotem_returnsFalse() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        assertFalse(tile.clearTotem(redTotem));
    }

    @Test
    void clearTotem_null_returnsFalse() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        assertFalse(tile.clearTotem(null));
    }

    // ---- clearSpaces ----

    @Test
    void clearSpaces_allSpacesBecomeEmpty() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        tile.addTotem(redTotem, alice);
        tile.addTotem(blueTotem, bob);
        tile.clearSpaces();
        for (TurnOrderSpace space : tile.getSpaces()) {
            assertTrue(space.isFree());
        }
    }

    @Test
    void clearSpaces_orderBecomeEmpty() {
        tile.configureForPlayerCount(PlayerCount.TWO);
        tile.addTotem(redTotem, alice);
        tile.clearSpaces();
        assertTrue(tile.determineNewOrder().isEmpty());
    }
}
