package mesos.effects;

import mesos.enums.TotemColor;
import mesos.order.TurnOrderSpace;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurnOrderPlacementEffectTest {

    private TurnOrderPlacementEffect effect;
    private Player alice;

    @BeforeEach
    void setUp() {
        effect = new TurnOrderPlacementEffect(2);
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    @Test
    void onTurnOrderPlaced_nullSpace_noFoodGain() {
        int foodBefore = alice.getFood();
        effect.onTurnOrderPlaced(alice, null);
        assertEquals(foodBefore, alice.getFood());
    }

    @Test
    void onTurnOrderPlaced_zeroFoodBonus_noFoodGain() {
        int foodBefore = alice.getFood();
        TurnOrderSpace space = new TurnOrderSpace(1, 0, false);
        effect.onTurnOrderPlaced(alice, space);
        assertEquals(foodBefore, alice.getFood());
    }

    @Test
    void onTurnOrderPlaced_lastSpace_noFoodGain() {
        int foodBefore = alice.getFood();
        TurnOrderSpace space = new TurnOrderSpace(3, 2, true); // has food bonus but is last space
        effect.onTurnOrderPlaced(alice, space);
        assertEquals(foodBefore, alice.getFood());
    }

    @Test
    void onTurnOrderPlaced_bonusSpaceNotLast_gainsExtraFood() {
        alice.gainFood(5);
        int foodBefore = alice.getFood();
        TurnOrderSpace space = new TurnOrderSpace(1, 2, false);
        effect.onTurnOrderPlaced(alice, space);
        assertEquals(foodBefore + 2, alice.getFood());
    }

    @Test
    void onTurnOrderPlaced_extraFoodIsConfiguredValue() {
        TurnOrderPlacementEffect effect3 = new TurnOrderPlacementEffect(3);
        int foodBefore = alice.getFood();
        TurnOrderSpace space = new TurnOrderSpace(1, 1, false);
        effect3.onTurnOrderPlaced(alice, space);
        assertEquals(foodBefore + 3, alice.getFood());
    }

    @Test
    void getDescription_returnsTurnOrder() {
        assertEquals("TURN_ORDER", effect.getDescription());
    }
}
