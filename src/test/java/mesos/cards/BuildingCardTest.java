package mesos.cards;

import mesos.effects.ExtraTopRowPickEffect;
import mesos.effects.NoBuildingEffect;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildingCardTest {

    private Player alice;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    @Test
    void calculateRealCost_noDiscount_returnsFullCost() {
        BuildingCard card = new BuildingCard("B", Era.I, 5, 2, new NoBuildingEffect());
        assertEquals(5, card.calculateRealCost(0));
    }

    @Test
    void calculateRealCost_partialDiscount_reducedCost() {
        BuildingCard card = new BuildingCard("B", Era.I, 5, 2, new NoBuildingEffect());
        assertEquals(3, card.calculateRealCost(2));
    }

    @Test
    void calculateRealCost_excessDiscount_clampsToZero() {
        BuildingCard card = new BuildingCard("B", Era.I, 3, 2, new NoBuildingEffect());
        assertEquals(0, card.calculateRealCost(10));
    }

    @Test
    void getFoodCost_returnsConfiguredValue() {
        BuildingCard card = new BuildingCard("B", Era.I, 4, 3, new NoBuildingEffect());
        assertEquals(4, card.getFoodCost());
    }

    @Test
    void getPrintedPrestigePoints_returnsConfiguredValue() {
        BuildingCard card = new BuildingCard("B", Era.I, 4, 3, new NoBuildingEffect());
        assertEquals(3, card.getPrintedPrestigePoints());
    }

    @Test
    void getTotalPrestigeAtGameEnd_noBonus_equalsPrinted() {
        BuildingCard card = new BuildingCard("B", Era.I, 4, 3, new NoBuildingEffect());
        assertEquals(3, card.getTotalPrestigeAtGameEnd(alice));
    }

    @Test
    void getExtraTopRowPickEffect_withExtraTopEffect_returnsIt() {
        ExtraTopRowPickEffect extraEffect = new ExtraTopRowPickEffect();
        BuildingCard card = new BuildingCard("B", Era.I, 3, 1, extraEffect);
        assertSame(extraEffect, card.getExtraTopRowPickEffect());
    }

    @Test
    void getExtraTopRowPickEffect_withNoBuildingEffect_returnsNull() {
        BuildingCard card = new BuildingCard("B", Era.I, 3, 1, new NoBuildingEffect());
        assertNull(card.getExtraTopRowPickEffect());
    }

    @Test
    void getExtraTopRowPickEffect_nullEffect_replacedByNoBuildingEffect_returnsNull() {
        BuildingCard card = new BuildingCard("B", Era.I, 3, 1, null);
        assertNull(card.getExtraTopRowPickEffect());
    }

    @Test
    void getExtraFoodPerCharacterForEvent_delegatesToEffect() {
        BuildingCard card = new BuildingCard("B", Era.I, 3, 1, new NoBuildingEffect());
        assertEquals(0, card.getExtraFoodPerCharacterForEvent(EventType.HUNTING, CharacterType.HUNTER, alice));
    }

    @Test
    void getExtraPrestigePerCharacterForEvent_delegatesToEffect() {
        BuildingCard card = new BuildingCard("B", Era.I, 3, 1, new NoBuildingEffect());
        assertEquals(0, card.getExtraPrestigePerCharacterForEvent(EventType.HUNTING, CharacterType.HUNTER, alice));
    }

    @Test
    void getEndGameBonus_delegatesToEffect() {
        BuildingCard card = new BuildingCard("B", Era.I, 3, 1, new NoBuildingEffect());
        assertEquals(0, card.getEndGameBonus(alice));
    }
}
