package mesos.player;

import mesos.cards.BuildingCard;
import mesos.cards.characters.Builder;
import mesos.cards.characters.Gatherer;
import mesos.cards.characters.Hunter;
import mesos.effects.NoBuildingEffect;
import mesos.enums.Era;
import mesos.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    private Player alice;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    // ---- payFood ----

    @Test
    void payFood_sufficientFood_deductsAndReturnsTrue() {
        alice.gainFood(5);
        assertTrue(alice.payFood(3));
        assertEquals(2, alice.getFood());
    }

    @Test
    void payFood_exactAmount_deductsToZeroAndReturnsTrue() {
        alice.gainFood(5);
        assertTrue(alice.payFood(5));
        assertEquals(0, alice.getFood());
    }

    @Test
    void payFood_insufficientFood_returnsFalseAndNoDeduction() {
        alice.gainFood(2);
        assertFalse(alice.payFood(5));
        assertEquals(2, alice.getFood());
    }

    @Test
    void payFood_zero_returnsTrueWithoutChange() {
        alice.gainFood(3);
        assertTrue(alice.payFood(0));
        assertEquals(3, alice.getFood());
    }

    @Test
    void payFood_negative_returnsTrueWithoutChange() {
        alice.gainFood(3);
        assertTrue(alice.payFood(-1));
        assertEquals(3, alice.getFood());
    }

    @Test
    void payFood_zeroFoodZeroAmount_returnsTrue() {
        assertTrue(alice.payFood(0));
        assertEquals(0, alice.getFood());
    }

    // ---- gainFood ----

    @Test
    void gainFood_positive_addsFood() {
        alice.gainFood(4);
        assertEquals(4, alice.getFood());
    }

    @Test
    void gainFood_zero_noChange() {
        alice.gainFood(3);
        alice.gainFood(0);
        assertEquals(3, alice.getFood());
    }

    @Test
    void gainFood_negative_noChange() {
        alice.gainFood(3);
        alice.gainFood(-2);
        assertEquals(3, alice.getFood());
    }

    // ---- addPrestige / removePrestige ----

    @Test
    void addPrestige_positive_increases() {
        alice.addPrestige(7);
        assertEquals(7, alice.getPrestigePoints());
    }

    @Test
    void addPrestige_zero_noChange() {
        alice.addPrestige(5);
        alice.addPrestige(0);
        assertEquals(5, alice.getPrestigePoints());
    }

    @Test
    void removePrestige_positive_decreases() {
        alice.addPrestige(10);
        alice.removePrestige(4);
        assertEquals(6, alice.getPrestigePoints());
    }

    @Test
    void removePrestige_zero_noChange() {
        alice.addPrestige(5);
        alice.removePrestige(0);
        assertEquals(5, alice.getPrestigePoints());
    }

    @Test
    void removePrestige_belowZero_allowsNegative() {
        alice.removePrestige(5);
        assertEquals(-5, alice.getPrestigePoints());
    }

    // ---- acquireCharacter ----

    @Test
    void acquireCharacter_hunterWithImmediateFood_gainsFood() {
        Hunter h = new Hunter("h", Era.I, null, true);
        alice.acquireCharacter(h);
        // tribe has 1 hunter; onPurchase gives gainFood(1)
        assertEquals(1, alice.getFood());
    }

    @Test
    void acquireCharacter_hunterNoFood_noFoodGained() {
        Hunter h = new Hunter("h", Era.I, null, false);
        alice.acquireCharacter(h);
        assertEquals(0, alice.getFood());
    }

    @Test
    void acquireCharacter_twoHuntersWithFood_foodScalesWithCount() {
        Hunter h1 = new Hunter("h1", Era.I, null, false);
        Hunter h2 = new Hunter("h2", Era.I, null, true);
        alice.acquireCharacter(h1); // no food, 1 hunter in tribe, hasFood=false
        // h2 has immediate food: tribe has 2 hunters now → gainFood(2)
        alice.acquireCharacter(h2);
        assertEquals(2, alice.getFood());
    }

    @Test
    void acquireCharacter_addedToTribe() {
        Gatherer g = new Gatherer("g", Era.I, null);
        alice.acquireCharacter(g);
        assertEquals(1, alice.getTribe().getCharacterCards().size());
    }

    // ---- acquireBuilding ----

    @Test
    void acquireBuilding_deductsFoodCost() {
        alice.gainFood(5);
        BuildingCard b = new BuildingCard("b", Era.I, 3, 1, new NoBuildingEffect());
        alice.acquireBuilding(b);
        assertEquals(2, alice.getFood());
    }

    @Test
    void acquireBuilding_withBuilderDiscount_reducedCost() {
        alice.gainFood(5);
        Builder builder = new Builder("b", Era.I, null, 1, 2); // discountValue=2
        alice.acquireCharacter(builder);
        // building cost 3, discount 2 → realCost = max(0, 3-2) = 1
        BuildingCard building = new BuildingCard("house", Era.I, 3, 1, new NoBuildingEffect());
        alice.acquireBuilding(building);
        assertEquals(4, alice.getFood()); // 5 - 1 = 4
    }

    @Test
    void acquireBuilding_discountExceedsCost_paysZero() {
        alice.gainFood(5);
        Builder builder = new Builder("b", Era.I, null, 1, 5); // discountValue=5
        alice.acquireCharacter(builder);
        // building cost 2, discount 5 → realCost = max(0, 2-5) = 0
        BuildingCard building = new BuildingCard("free", Era.I, 2, 1, new NoBuildingEffect());
        alice.acquireBuilding(building);
        assertEquals(5, alice.getFood()); // no food deducted
    }

    @Test
    void acquireBuilding_insufficientFood_throwsIllegalState() {
        alice.gainFood(1);
        BuildingCard b = new BuildingCard("b", Era.I, 3, 1, new NoBuildingEffect());
        assertThrows(IllegalStateException.class, () -> alice.acquireBuilding(b));
        // food must be unchanged
        assertEquals(1, alice.getFood());
    }

    @Test
    void acquireBuilding_addedToTribe() {
        alice.gainFood(3);
        BuildingCard b = new BuildingCard("b", Era.I, 3, 1, new NoBuildingEffect());
        alice.acquireBuilding(b);
        assertEquals(1, alice.getTribe().getBuildingCards().size());
    }

    // ---- selectedOfferTile ----

    @Test
    void selectedOfferTile_initiallyNull() {
        assertNull(alice.getSelectedOfferTile());
    }

    @Test
    void clearSelectedOfferTile_setsToNull() {
        // We can't easily create an OfferTile here without setting up a board,
        // so just verify clearSelectedOfferTile works on already-null state
        alice.clearSelectedOfferTile();
        assertNull(alice.getSelectedOfferTile());
    }

    // ---- identity ----

    @Test
    void getId_returnsCorrectId() {
        assertEquals("alice", alice.getId());
    }

    @Test
    void getTotem_returnsCorrectColor() {
        assertEquals(TotemColor.RED, alice.getTotem().getColor());
    }
}
