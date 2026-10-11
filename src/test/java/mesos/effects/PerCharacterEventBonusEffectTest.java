package mesos.effects;

import mesos.enums.CharacterType;
import mesos.enums.EventType;
import mesos.enums.TotemColor;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerCharacterEventBonusEffectTest {

    private PerCharacterEventBonusEffect effect;
    private Player alice;

    @BeforeEach
    void setUp() {
        effect = new PerCharacterEventBonusEffect(EventType.HUNTING, CharacterType.HUNTER, 2, 1);
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    @Test
    void getExtraFood_matchingEventAndCharType_returnsConfiguredValue() {
        assertEquals(2, effect.getExtraFoodPerCharacterForEvent(EventType.HUNTING, CharacterType.HUNTER, alice));
    }

    @Test
    void getExtraFood_wrongEventType_returnsZero() {
        assertEquals(0, effect.getExtraFoodPerCharacterForEvent(EventType.SUSTENANCE, CharacterType.HUNTER, alice));
    }

    @Test
    void getExtraFood_wrongCharType_returnsZero() {
        assertEquals(0, effect.getExtraFoodPerCharacterForEvent(EventType.HUNTING, CharacterType.SHAMAN, alice));
    }

    @Test
    void getExtraFood_bothWrong_returnsZero() {
        assertEquals(0, effect.getExtraFoodPerCharacterForEvent(EventType.CAVE_PAINTINGS, CharacterType.ARTIST, alice));
    }

    @Test
    void getExtraPrestige_matchingEventAndCharType_returnsConfiguredValue() {
        assertEquals(1, effect.getExtraPrestigePerCharacterForEvent(EventType.HUNTING, CharacterType.HUNTER, alice));
    }

    @Test
    void getExtraPrestige_wrongEventType_returnsZero() {
        assertEquals(0, effect.getExtraPrestigePerCharacterForEvent(EventType.SHAMANIC_RITUAL, CharacterType.HUNTER, alice));
    }

    @Test
    void getExtraPrestige_wrongCharType_returnsZero() {
        assertEquals(0, effect.getExtraPrestigePerCharacterForEvent(EventType.HUNTING, CharacterType.BUILDER, alice));
    }

    @Test
    void getDescription_returnsEventTypeName() {
        assertEquals("HUNTING", effect.getDescription());
    }

    @Test
    void getDescription_sustainanceEffect_returnsCorrectName() {
        PerCharacterEventBonusEffect sust = new PerCharacterEventBonusEffect(
                EventType.SUSTENANCE, CharacterType.GATHERER, 1, 0);
        assertEquals("SUSTENANCE", sust.getDescription());
    }
}
