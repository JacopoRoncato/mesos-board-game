package mesos.effects;

import mesos.enums.TotemColor;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShamanicRitualEffectTest {

    private Player alice;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
    }

    @Test
    void getAdditionalShamanIcons_returnsConfiguredValue() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(3, false, false);
        assertEquals(3, effect.getAdditionalShamanIcons(alice));
    }

    @Test
    void getAdditionalShamanIcons_zero() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, false, false);
        assertEquals(0, effect.getAdditionalShamanIcons(alice));
    }

    @Test
    void ignoresShamanicPenalty_true() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, true, false);
        assertTrue(effect.ignoresShamanicPenalty(alice));
    }

    @Test
    void ignoresShamanicPenalty_false() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, false, false);
        assertFalse(effect.ignoresShamanicPenalty(alice));
    }

    @Test
    void doublesShamanicReward_true() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, false, true);
        assertTrue(effect.doublesShamanicReward(alice));
    }

    @Test
    void doublesShamanicReward_false() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, false, false);
        assertFalse(effect.doublesShamanicReward(alice));
    }

    @Test
    void allFeaturesEnabled_returnsCorrectCombination() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(5, true, true);
        assertEquals(5, effect.getAdditionalShamanIcons(alice));
        assertTrue(effect.ignoresShamanicPenalty(alice));
        assertTrue(effect.doublesShamanicReward(alice));
    }

    @Test
    void getDescription_returnsShamanicRitual() {
        ShamanicRitualEffect effect = new ShamanicRitualEffect(1, false, false);
        assertEquals("SHAMANIC_RITUAL", effect.getDescription());
    }
}
