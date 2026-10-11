package mesos.effects;

import mesos.cards.characters.Artist;
import mesos.cards.characters.Hunter;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SustenanceDiscountEffectTest {

    private Player alice;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    @Test
    void getExtraSustenanceDiscount_noCharacters_returnsZero() {
        SustenanceDiscountEffect effect =
                new SustenanceDiscountEffect(CharacterType.ARTIST, 1);
        assertEquals(0, effect.getExtraSustenanceDiscount(alice));
    }

    @Test
    void getExtraSustenanceDiscount_wrongType_returnsZero() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        SustenanceDiscountEffect effect =
                new SustenanceDiscountEffect(CharacterType.ARTIST, 1);
        assertEquals(0, effect.getExtraSustenanceDiscount(alice));
    }

    @Test
    void getExtraSustenanceDiscount_oneCharacter_returnsSingleDiscount() {
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        SustenanceDiscountEffect effect =
                new SustenanceDiscountEffect(CharacterType.ARTIST, 2);
        assertEquals(2, effect.getExtraSustenanceDiscount(alice)); // 1 × 2
    }

    @Test
    void getExtraSustenanceDiscount_threeCharacters_sumsDiscount() {
        alice.getTribe().addCharacter(new Artist("a1", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a2", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a3", Era.I, null));
        SustenanceDiscountEffect effect =
                new SustenanceDiscountEffect(CharacterType.ARTIST, 1);
        assertEquals(3, effect.getExtraSustenanceDiscount(alice)); // 3 × 1
    }

    @Test
    void getExtraSustenanceDiscount_mixedTypes_countsOnlyTarget() {
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        SustenanceDiscountEffect effect =
                new SustenanceDiscountEffect(CharacterType.ARTIST, 3);
        assertEquals(3, effect.getExtraSustenanceDiscount(alice)); // only 1 artist
    }

    @Test
    void getDescription_returnsCharacterTypeName() {
        SustenanceDiscountEffect effect =
                new SustenanceDiscountEffect(CharacterType.HUNTER, 1);
        assertEquals("HUNTER", effect.getDescription());
    }
}
