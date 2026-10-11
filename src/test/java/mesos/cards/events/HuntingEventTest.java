package mesos.cards.events;

import mesos.cards.BuildingCard;
import mesos.cards.characters.Hunter;
import mesos.effects.PerCharacterEventBonusEffect;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HuntingEventTest {

    private Player alice;
    private Player bob;
    private HuntingEvent event;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
        bob   = new Player("bob",   new Totem(TotemColor.BLUE));
        event = new HuntingEvent("Hunt", Era.I, null, 1);
    }

    @Test
    void resolveEvent_noHunters_noFoodNoPP() {
        event.resolveEvent(List.of(alice));
        assertEquals(0, alice.getFood());
        assertEquals(0, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_oneHunter_gainsFoodAndPP() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        event.resolveEvent(List.of(alice));
        assertEquals(1, alice.getFood());         // 1 hunter × (1 + 0 extraFood)
        assertEquals(1, alice.getPrestigePoints()); // 1 hunter × (1 ppReward + 0)
    }

    @Test
    void resolveEvent_twoHunters_scaledReward() {
        alice.getTribe().addCharacter(new Hunter("h1", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        event.resolveEvent(List.of(alice));
        assertEquals(2, alice.getFood());
        assertEquals(2, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_higherPpReward_scalesCorrectly() {
        HuntingEvent bigReward = new HuntingEvent("Big Hunt", Era.II, null, 3);
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        bigReward.resolveEvent(List.of(alice));
        assertEquals(1, alice.getFood()); // 1 × (1 + 0) = 1 food
        assertEquals(3, alice.getPrestigePoints()); // 1 × (3 + 0) = 3 PP
    }

    @Test
    void resolveEvent_withBuildingExtraFood_addsBonus() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        // building: +1 food per hunter during HUNTING events
        PerCharacterEventBonusEffect bonus = new PerCharacterEventBonusEffect(
                EventType.HUNTING, CharacterType.HUNTER, 1, 0);
        alice.getTribe().addBuilding(new BuildingCard("bonus", Era.I, 0, 1, bonus));
        event.resolveEvent(List.of(alice));
        assertEquals(2, alice.getFood()); // 1 hunter × (1 + 1 extraFood) = 2
    }

    @Test
    void resolveEvent_withBuildingExtraPrestige_addsBonus() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        PerCharacterEventBonusEffect bonus = new PerCharacterEventBonusEffect(
                EventType.HUNTING, CharacterType.HUNTER, 0, 2);
        alice.getTribe().addBuilding(new BuildingCard("bonus", Era.I, 0, 1, bonus));
        event.resolveEvent(List.of(alice));
        assertEquals(3, alice.getPrestigePoints()); // 1 × (1 ppReward + 2) = 3
    }

    @Test
    void resolveEvent_multiplePlayers_eachGetsOwn() {
        alice.getTribe().addCharacter(new Hunter("h1", Era.I, null, false));
        bob.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        bob.getTribe().addCharacter(new Hunter("h3", Era.I, null, false));
        event.resolveEvent(List.of(alice, bob));
        assertEquals(1, alice.getFood());
        assertEquals(2, bob.getFood());
    }

    @Test
    void resolveEvent_emptyPlayers_noException() {
        assertDoesNotThrow(() -> event.resolveEvent(List.of()));
    }

    @Test
    void getEventType_isHunting() {
        assertEquals(EventType.HUNTING, event.getEventType());
    }

    @Test
    void getPpRewardPerHunter_returnsValue() {
        assertEquals(1, event.getPpRewardPerHunter());
    }
}
