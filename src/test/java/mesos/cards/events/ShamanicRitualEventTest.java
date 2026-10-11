package mesos.cards.events;

import mesos.cards.BuildingCard;
import mesos.cards.characters.Shaman;
import mesos.effects.ShamanicRitualEffect;
import mesos.enums.*;
import mesos.player.Player;
import mesos.player.Totem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShamanicRitualEventTest {

    // ppReward=3, ppPenalty=2
    private ShamanicRitualEvent event;

    @BeforeEach
    void setUp() {
        event = new ShamanicRitualEvent("Ritual", Era.I, null, 3, 2);
    }

    private Player player(String id, TotemColor color) {
        return new Player(id, new Totem(color));
    }

    private void addShaman(Player p, int icons) {
        p.getTribe().addCharacter(new Shaman("s", Era.I, null, icons));
    }

    // ---- single player ----

    @Test
    void resolveEvent_singlePlayer_getsRewardAndPenalty() {
        Player alice = player("alice", TotemColor.RED);
        addShaman(alice, 2);
        event.resolveEvent(List.of(alice));
        // alice is both max and min → gets reward AND penalty
        assertEquals(3 - 2, alice.getPrestigePoints()); // reward - penalty = 1
    }

    // ---- two players ----

    @Test
    void resolveEvent_clearWinner_getsOnlyReward() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        addShaman(alice, 3); // more icons
        addShaman(bob,   1); // fewer icons
        event.resolveEvent(List.of(alice, bob));
        assertEquals(3, alice.getPrestigePoints());  // reward only
        assertEquals(0, alice.getFood());
        assertEquals(-2, bob.getPrestigePoints()); // penalty only
    }

    @Test
    void resolveEvent_tiedForFirst_bothGetReward() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        addShaman(alice, 2);
        addShaman(bob,   2); // tied → both are max AND min
        event.resolveEvent(List.of(alice, bob));
        // Both get reward AND penalty (separate if-blocks)
        assertEquals(3 - 2, alice.getPrestigePoints());
        assertEquals(3 - 2, bob.getPrestigePoints());
    }

    @Test
    void resolveEvent_zeroIcons_allTied() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        // no shamans → all have 0 icons → all tied → each gets reward AND penalty
        event.resolveEvent(List.of(alice, bob));
        assertEquals(3 - 2, alice.getPrestigePoints());
        assertEquals(3 - 2, bob.getPrestigePoints());
    }

    // ---- three players for cleaner winner/loser separation ----

    @Test
    void resolveEvent_threeWayHierarchy_onlyExtremesPunishedOrRewarded() {
        Player a = player("a", TotemColor.RED);
        Player b = player("b", TotemColor.BLUE);
        Player c = player("c", TotemColor.WHITE);
        addShaman(a, 3);
        addShaman(b, 2);
        addShaman(c, 1);
        event.resolveEvent(List.of(a, b, c));
        assertEquals(3,  a.getPrestigePoints());  // max → reward
        assertEquals(0,  b.getPrestigePoints());  // middle → nothing
        assertEquals(-2, c.getPrestigePoints()); // min → penalty
    }

    // ---- ShamanicRitualEffect: ignore penalty ----

    @Test
    void resolveEvent_ignorePenalty_noPrestigeLoss() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        addShaman(bob, 5); // bob wins
        // alice has ignore-penalty building
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, true, false);
        alice.getTribe().addBuilding(new BuildingCard("noPenalty", Era.II, 0, 2, effect));
        event.resolveEvent(List.of(alice, bob));
        assertEquals(0, alice.getPrestigePoints()); // penalty ignored
        assertEquals(3, bob.getPrestigePoints());   // reward
    }

    @Test
    void resolveEvent_ignorePenalty_totalTie_penaltyStillApplied() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        addShaman(alice, 2);
        addShaman(bob,   2); // pareggio totale
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, true, false);
        alice.getTribe().addBuilding(new BuildingCard("noPenalty", Era.II, 0, 2, effect));
        event.resolveEvent(List.of(alice, bob));
        // In pareggio totale ignoresShamanicPenalty non protegge
        assertEquals(3 - 2, alice.getPrestigePoints());
        assertEquals(3 - 2, bob.getPrestigePoints());
    }

    // ---- ShamanicRitualEffect: double reward (unique winner) ----

    @Test
    void resolveEvent_doubleReward_uniqueWinner_rewardDoubled() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        addShaman(alice, 5); // alice wins uniquely
        // alice has double-reward building
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, false, true);
        alice.getTribe().addBuilding(new BuildingCard("doubleRew", Era.III, 0, 4, effect));
        event.resolveEvent(List.of(alice, bob));
        assertEquals(6, alice.getPrestigePoints()); // 3 × 2 = 6
        assertEquals(-2, bob.getPrestigePoints());
    }

    @Test
    void resolveEvent_doubleReward_tiedWinners_notDoubled() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        addShaman(alice, 3);
        addShaman(bob,   3); // tied
        ShamanicRitualEffect effect = new ShamanicRitualEffect(0, false, true);
        alice.getTribe().addBuilding(new BuildingCard("doubleRew", Era.III, 0, 4, effect));
        event.resolveEvent(List.of(alice, bob));
        // tied → not doubled even with doubleRewardEffect
        assertEquals(3 - 2, alice.getPrestigePoints()); // 3 reward, 2 penalty
        assertEquals(3 - 2, bob.getPrestigePoints());
    }

    // ---- ShamanicRitualEffect: additional icons ----

    @Test
    void resolveEvent_additionalShamanIcons_countedInComparison() {
        Player alice = player("alice", TotemColor.RED);
        Player bob   = player("bob",   TotemColor.BLUE);
        addShaman(alice, 1);
        addShaman(bob,   1);
        // alice gets +3 extra icons from building → alice wins
        ShamanicRitualEffect effect = new ShamanicRitualEffect(3, false, false);
        alice.getTribe().addBuilding(new BuildingCard("extraIcons", Era.II, 0, 3, effect));
        event.resolveEvent(List.of(alice, bob));
        assertEquals(3, alice.getPrestigePoints());   // max (1+3=4 vs 1)
        assertEquals(-2, bob.getPrestigePoints()); // min
    }

    @Test
    void getters_returnConfiguredValues() {
        assertEquals(3, event.getPpReward());
        assertEquals(2, event.getPpPenalty());
    }
}
