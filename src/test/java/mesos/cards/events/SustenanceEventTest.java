package mesos.cards.events;

import mesos.cards.BuildingCard;
import mesos.cards.characters.Gatherer;
import mesos.cards.characters.Hunter;
import mesos.effects.SustenanceDiscountEffect;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SustenanceEventTest {

    private Player alice;
    private SustenanceEvent event;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
        event = new SustenanceEvent("Sus", Era.I, null, 2); // 2 PP penalty per unfed
    }

    @Test
    void resolveEvent_noCharacters_noFoodDeducted() {
        alice.gainFood(5);
        event.resolveEvent(List.of(alice));
        assertEquals(5, alice.getFood());
        assertEquals(0, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_enoughFood_feedsAllCharacters() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        alice.gainFood(5);
        event.resolveEvent(List.of(alice));
        assertEquals(3, alice.getFood()); // 5 - 2 = 3
        assertEquals(0, alice.getPrestigePoints()); // no penalty
    }

    @Test
    void resolveEvent_noFood_losesPrestigeForAllUnfed() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        // 2 characters, 0 food, 0 discount → 2 unfed → penalty 2×2=4
        event.resolveEvent(List.of(alice));
        assertEquals(0, alice.getFood());
        assertEquals(-4, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_partialFood_paysWhatAvailableAndPenalizesRest() {
        alice.getTribe().addCharacter(new Hunter("h1", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h3", Era.I, null, false));
        alice.gainFood(1); // 3 characters, 1 food → pays 1, 2 unfed → penalty 2×2=4
        event.resolveEvent(List.of(alice));
        assertEquals(0, alice.getFood());
        assertEquals(-4, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_withGathererDiscount_reducesFood() {
        alice.getTribe().addCharacter(new Hunter("h1", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h3", Era.I, null, false));
        alice.getTribe().addCharacter(new Gatherer("g", Era.I, null));
        // Gatherer discount reduces foodNeeded; 4 characters total (including gatherer)
        int gatherDiscount = new Gatherer("g", Era.I, null).getBaseSustenanceDiscount();
        alice.gainFood(5);
        event.resolveEvent(List.of(alice));
        int foodNeeded = Math.max(0, 4 - gatherDiscount);
        int expectedFood = 5 - Math.min(foodNeeded, 5);
        assertEquals(expectedFood, alice.getFood());
    }

    @Test
    void resolveEvent_withBuildingExtraDiscount_reducesFood() {
        alice.getTribe().addCharacter(new Hunter("h1", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h3", Era.I, null, false));
        // building: 1 discount per HUNTER (3 hunters → 3 extra discount)
        SustenanceDiscountEffect effect = new SustenanceDiscountEffect(CharacterType.HUNTER, 1);
        alice.getTribe().addBuilding(new BuildingCard("disc", Era.I, 0, 1, effect));
        alice.gainFood(0);
        // 3 chars, discount = 3 from building → foodNeeded = max(0, 3-3) = 0 → no cost, no penalty
        event.resolveEvent(List.of(alice));
        assertEquals(0, alice.getFood());
        assertEquals(0, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_exactFood_zeroUnfed() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.gainFood(1); // exactly enough
        event.resolveEvent(List.of(alice));
        assertEquals(0, alice.getFood());
        assertEquals(0, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_multiplePlayers_eachHandledIndependently() {
        Player bob = new Player("bob", new Totem(TotemColor.BLUE));
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.gainFood(10); // alice can afford
        // bob has 1 character, 0 food → penalty
        bob.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        event.resolveEvent(List.of(alice, bob));
        assertEquals(9, alice.getFood()); // paid 1
        assertEquals(0, alice.getPrestigePoints());
        assertEquals(-2, bob.getPrestigePoints()); // 1 unfed × 2 penalty
    }

    @Test
    void getPpPenaltyPerUnfedCharacter_returnsValue() {
        assertEquals(2, event.getPpPenaltyPerUnfedCharacter());
    }
}
