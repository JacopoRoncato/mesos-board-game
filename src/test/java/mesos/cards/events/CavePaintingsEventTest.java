package mesos.cards.events;

import mesos.cards.BuildingCard;
import mesos.cards.characters.Artist;
import mesos.cards.characters.Hunter;
import mesos.effects.PerCharacterEventBonusEffect;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CavePaintingsEventTest {

    private Player alice;
    // threshold=2, ppPenalty=3, ppRewardPerArtist=2
    private CavePaintingsEvent event;

    @BeforeEach
    void setUp() {
        alice = new Player("alice", new Totem(TotemColor.RED));
        event = new CavePaintingsEvent("Cave", Era.I, null, 2, 3, 2);
    }

    @Test
    void resolveEvent_noArtists_belowThreshold_penalty() {
        event.resolveEvent(List.of(alice));
        assertEquals(-3, alice.getPrestigePoints());
        assertEquals(0, alice.getFood());
    }

    @Test
    void resolveEvent_oneArtist_belowThreshold_penalty() {
        alice.getTribe().addCharacter(new Artist("a", Era.I, null));
        event.resolveEvent(List.of(alice)); // 1 < threshold 2 → penalty
        assertEquals(-3, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_exactlyAtThreshold_reward() {
        alice.getTribe().addCharacter(new Artist("a1", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a2", Era.I, null));
        event.resolveEvent(List.of(alice)); // 2 >= threshold 2 → 2×2=4 PP
        assertEquals(4, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_aboveThreshold_rewardScales() {
        alice.getTribe().addCharacter(new Artist("a1", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a2", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a3", Era.I, null));
        event.resolveEvent(List.of(alice)); // 3 × 2 = 6 PP
        assertEquals(6, alice.getPrestigePoints());
    }

    @Test
    void resolveEvent_withBuildingFoodBonus_gainsFood() {
        alice.getTribe().addCharacter(new Artist("a1", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a2", Era.I, null));
        // building: +1 food per ARTIST during CAVE_PAINTINGS
        PerCharacterEventBonusEffect bonus = new PerCharacterEventBonusEffect(
                EventType.CAVE_PAINTINGS, CharacterType.ARTIST, 1, 0);
        alice.getTribe().addBuilding(new BuildingCard("bonus", Era.I, 0, 1, bonus));
        event.resolveEvent(List.of(alice));
        assertEquals(2, alice.getFood()); // 2 artists × 1 extraFood
        assertEquals(4, alice.getPrestigePoints()); // 2 × 2 reward
    }

    @Test
    void resolveEvent_buildingFoodBonus_zeroArtists_noFood() {
        PerCharacterEventBonusEffect bonus = new PerCharacterEventBonusEffect(
                EventType.CAVE_PAINTINGS, CharacterType.ARTIST, 2, 0);
        alice.getTribe().addBuilding(new BuildingCard("bonus", Era.I, 0, 1, bonus));
        event.resolveEvent(List.of(alice)); // 0 artists → 0×2=0 food
        assertEquals(0, alice.getFood());
    }

    @Test
    void resolveEvent_multiplePlayers() {
        Player bob = new Player("bob", new Totem(TotemColor.BLUE));
        alice.getTribe().addCharacter(new Artist("a1", Era.I, null));
        alice.getTribe().addCharacter(new Artist("a2", Era.I, null)); // alice: reward
        // bob: 0 artists → penalty
        event.resolveEvent(List.of(alice, bob));
        assertEquals(4, alice.getPrestigePoints());
        assertEquals(-3, bob.getPrestigePoints());
    }

    @Test
    void resolveEvent_nonArtistDoesNotCount() {
        alice.getTribe().addCharacter(new Hunter("h", Era.I, null, false));
        alice.getTribe().addCharacter(new Hunter("h2", Era.I, null, false));
        event.resolveEvent(List.of(alice)); // 0 artists, 2 hunters → penalty
        assertEquals(-3, alice.getPrestigePoints());
    }

    @Test
    void getters_returnConfiguredValues() {
        assertEquals(2, event.getArtistThreshold());
        assertEquals(3, event.getPpPenalty());
        assertEquals(2, event.getPpRewardPerArtist());
    }
}
